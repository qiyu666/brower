package com.qiyu.browser.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Linux 容器管理：rootfs 下载/解压、proot 会话、包管理脚本执行。
 *  rootfs 内置 proot 5.5.0 静态版（/usr/local/bin/proot），宿主无 root 直接跑。 */
object LinuxManager {

    class Dist(
        val name: String, val label: String, val version: String,
        val url: String, val ghProxyUrl: String,
        val sizeMB: Int, val pkgMgr: String // "apt" | "apk"
    )

    fun dist(name: String): Dist = DISTROS.first { it.name == name }

    val DISTROS = listOf(
        Dist(
            "alpine", "Alpine Linux", "3.22",
            "https://github.com/qiyu666/brower/raw/linux-rootfs/alpine-rootfs-v2.tar.gz",
            "https://gh-proxy.com/https://github.com/qiyu666/brower/raw/linux-rootfs/alpine-rootfs-v2.tar.gz",
            4, "apk"
        ),
        Dist(
            "debian", "Debian", "13 (trixie)",
            "https://github.com/qiyu666/brower/raw/linux-rootfs/debian-rootfs-v2.tar.gz",
            "https://gh-proxy.com/https://github.com/qiyu666/brower/raw/linux-rootfs/debian-rootfs-v2.tar.gz",
            48, "apt"
        ),
        Dist(
            "ubuntu", "Ubuntu", "24.04 (noble)",
            "https://github.com/qiyu666/brower/raw/linux-rootfs/ubuntu-rootfs-v2.tar.gz",
            "https://gh-proxy.com/https://github.com/qiyu666/brower/raw/linux-rootfs/ubuntu-rootfs-v2.tar.gz",
            28, "apt"
        )
    )

    fun baseDir(ctx: Context): File = File(ctx.filesDir, "linux")
    fun rootDir(ctx: Context, dist: String): File = File(baseDir(ctx), "$dist/root")
    fun prootBin(ctx: Context, dist: String): File = File(rootDir(ctx, dist), "usr/local/bin/proot")

    fun isInstalled(ctx: Context, dist: String): Boolean {
        val r = rootDir(ctx, dist)
        return r.isDirectory && File(r, "usr").isDirectory && prootBin(ctx, dist).isFile
    }

    /** 下载（直连失败切 gh-proxy）+ gunzip + 解 tar 到 rootDir。onProgress: 0..100 */
    suspend fun setup(ctx: Context, d: Dist, onProgress: (Int) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = baseDir(ctx)
                dir.mkdirs()
                val gz = File(dir, "${d.name}-rootfs-v2.tar.gz")
                val tar = File(dir, "${d.name}-rootfs-v2.tar")
                val root = rootDir(ctx, d.name)
                if (root.exists()) root.deleteRecursively()
                root.mkdirs()

                // 下载（旧缓存可能不完整/被污染，先校验，坏就删了重下）
                if (!verifyGz(gz)) {
                    gz.delete()
                    onProgress(0)
                    var ok = false
                    for (u in listOf(d.url, d.ghProxyUrl)) {
                        if (downloadBlocking(u, gz, onProgress)) { ok = true; break }
                        gz.delete()
                    }
                    if (!ok) return@withContext Result.failure(Exception("rootfs 下载失败：直连与 gh-proxy 均不可用"))
                    if (!verifyGz(gz)) {
                        gz.delete()
                        return@withContext Result.failure(Exception("rootfs 下载不完整：两个源都没拿到完整文件，请检查网络后重试"))
                    }
                } else {
                    onProgress(100)
                }

                gunzipTo(gz, tar)
                extractTar(tar, root)
                gz.delete()
                tar.delete()
                // 完整性验收：关键目录都在才算成功
                val missing = listOf("bin", "usr", "etc").firstOrNull { !File(root, it).exists() }
                if (missing != null) {
                    return@withContext Result.failure(Exception("rootfs 解压不完整（缺少 " + missing + " 目录），请删掉旧数据后重试"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 快速校验 gz：能完整解一遍才算好（不完整流会在中途报异常） */
    private fun verifyGz(gz: File): Boolean {
        if (!gz.exists() || gz.length() < 1024L) return false
        val ok = try {
            var good = true
            FileInputStream(gz).use { fis ->
                GZIPInputStream(BufferedInputStream(fis)).use { gzis ->
                    val buf = ByteArray(256 * 1024)
                    while (gzis.read(buf) >= 0) { /* 完整解一遍 */ }
                }
            }
            good
        } catch (e: Exception) {
            false
        }
        return ok
    }

    private fun downloadBlocking(url: String, out: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return false
            }
            val total = conn.contentLengthLong
            val t0 = System.currentTimeMillis()
            var complete = false
            conn.inputStream.use { input ->
                FileOutputStream(out).use { os ->
                    val buf = ByteArray(256 * 1024)
                    var got = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) { complete = true; break }
                        os.write(buf, 0, n)
                        got += n
                        val now = System.currentTimeMillis()
                        // 慢速自动切代理：下载 5s 后平均速度不足 12KB/s 就掐掉，交给下一个 URL（gh-proxy）
                        val el = now - t0
                        if (el >= 5000L && got < el / 1000 * 12) break
                        val pct = if (total > 0) ((got * 100) / total).toInt() else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            complete && out.length() > 1024L
        } catch (e: Exception) {
            false
        }
    }

    private fun gunzipTo(gz: File, out: File) {
        FileInputStream(gz).use { fis ->
            GZIPInputStream(BufferedInputStream(fis)).use { gzis ->
                BufferedOutputStream(FileOutputStream(out)).use { os ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = gzis.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                    }
                }
            }
        }
    }

    /** 最小 tar 解包：512B 头，支持常规文件/目录/符号链接/硬链接（rootfs 全是这些） */
    private fun extractTar(tar: File, to: File) {
        FileInputStream(tar).buffered().use { br ->
            while (true) {
                val header = ByteArray(512)
                var off = 0
                while (off < 512) {
                    val n = br.read(header, off, 512 - off)
                    if (n <= 0) break
                    off += n
                }
                if (off < 512) break
                if (header.all { it.toByte() == 0.toByte() }) break
                val name = cstr(header, 0, 100)
                val sizeStr = cstr(header, 124, 12).trim { it <= ' ' }
                // GNU tar 的空目录/空条目 size 字段是空串，不能直接 toLong
                val size = if (sizeStr.isEmpty()) 0L else try {
                    sizeStr.toLong(8)
                } catch (e: Exception) {
                    0L
                }
                val type = header[156].toChar()
                val link = cstr(header, 157, 100)
                // ustar 长名
                var fullName = name
                if (header[257].toInt() == 'u'.code) {
                    val pfx = cstr(header, 345, 155)
                    if (pfx.isNotEmpty()) fullName = pfx + "/" + name
                }
                val data = ByteArray(if (size > 0) size.toInt() else 0)
                var read = 0
                while (read < data.size) {
                    val n = br.read(data, read, data.size - read)
                    if (n <= 0) break
                    read += n
                }
                // 关键：tar 数据块按 512 字节对齐，必须跳完剩余 padding，否则下一个头读到数据中间
                var pad = ((512 - (size % 512)) % 512).toInt()
                val padBuf = ByteArray(pad)
                var padRead = 0
                while (padRead < pad) {
                    val n = br.read(padBuf, padRead, pad - padRead)
                    if (n <= 0) break
                    padRead += n
                }
                if (fullName == "" || fullName == "./") continue
                val target = File(to, fullName.trimStart('/'))
                try {
                    if (fullName.endsWith("/") || type == 'd') {
                        target.mkdirs()
                    } else if (type == '5' && link.isNotEmpty()) {
                        try {
                            if (target.exists()) target.delete()
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(link),
                                null
                            )
                        } catch (e: Exception) {
                            // 目标不存在时符号链接照样建（相对/绝对路径直接指向）
                            try {
                                if (target.exists()) target.delete()
                                java.nio.file.Files.createSymbolicLink(
                                    target.toPath(),
                                    java.nio.file.Paths.get(link),
                                    null
                                )
                            } catch (e2: Exception) {}
                        }
                    } else {
                        val parent = target.parentFile
                        if (parent != null) parent.mkdirs()
                        if (type == '2' && link.isNotEmpty()) {
                            // 硬链接：指向 rootfs 内路径
                            val src = File(to, link.trimStart('/'))
                            try {
                                if (src.exists()) {
                                    java.nio.file.Files.createLink(target.toPath(), src.toPath())
                                    continue
                                }
                            } catch (e: Exception) {}
                        }
                        FileOutputStream(target).use { os -> os.write(data) }
                        if (header[146].toInt() != '0'.code) target.setExecutable(true, false)
                    }
                } catch (e: Exception) {
                    // 单条坏记录不中断整体解压（数据已读走，继续下一条）
                }
            }
        }
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end].toByte() != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    // ---------- proot 会话 ----------

    class Session(val dist: String, val process: Process) {
        val output = ArrayDeque<String>()
        val lock = Any()

        fun append(line: String) {
            synchronized(lock) {
                output.addLast(line)
                while (output.size > 4000) output.removeFirst()
            }
        }

        fun snapshot(): List<String> = synchronized(lock) { output.toList() }
    }

    private val sessions = HashMap<String, Session>()

    /** 交互式 shell：proot -0 -r rootfs /bin/sh；输出进共享缓冲 */
    fun startShell(ctx: Context, dist: String): Session {
        stopShell(dist)
        val d = dist(dist)
        val root = rootDir(ctx, dist)
        val proot = prootBin(ctx, dist)
        val s0 = Session(dist, ProcessBuilder("true").start())
        sessions[dist] = s0

        fun launch(): Process? {
            val cmd = ArrayList<String>()
            cmd.add(proot.absolutePath)
            cmd.add("-0")
            cmd.add("-r")
            cmd.add(root.absolutePath)
            cmd.add("-b")
            cmd.add("/sys")
            cmd.add("-w")
            cmd.add("/root")
            cmd.add("/bin/sh")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            return try {
                pb.start()
            } catch (e: Exception) {
                s0.append("启动失败：" + (e.message ?: e.javaClass.simpleName))
                s0.append("二进制 存在=" + proot.exists() + " 可执行=" + proot.canExecute() + " 大小=" + proot.length())
                null
            }
        }

        // 先试启动（存在时）；启动器调用在 UI 线程，重活（重解压）一律后台
        val p = if (proot.exists()) launch() else null
        if (p != null) {
            // 自检：跑一次 --version，确认二进制在这台机器上真能执行
            try {
                val vt = ProcessBuilder(proot.absolutePath, "--version").redirectErrorStream(true).start()
                val out = vt.inputStream.bufferedReader().readText().lineSequence().firstOrNull()
                vt.waitFor()
                s0.append("自检：" + (out ?: "proot --version 无输出"))
            } catch (e: Exception) {
                s0.append("自检失败：" + (e.message ?: e.javaClass.simpleName))
            }
            val s = Session(dist, p)
            sessions[dist] = s
            Thread {
                val rd = p.inputStream.bufferedReader()
                while (true) {
                    val line = rd.readLine()
                    if (line == null) break
                    s.append(line)
                }
            }.start()
            return s
        }
        // 启动失败/缺失：后台重新解压并重试
        s0.append("proot 无法启动，正在重新解压并重试…")
        Thread {
            kotlinx.coroutines.runBlocking { setup(ctx, d) { } }
            if (!proot.exists()) {
                s0.append("重解压后仍缺失：bin/=" + File(root, "bin").exists() + " usr/=" + File(root, "usr").exists() + " root/=" + root.exists())
                s0.append("请回下载页重新点下载")
                return@Thread
            }
            if (!proot.canExecute()) proot.setExecutable(true)
            val p2 = launch()
            if (p2 == null) {
                s0.append("重解压后仍无法启动（可能 ROM 限制）。设置页确认版本号，或换 Debian 试")
                return@Thread
            }
            val s2 = Session(dist, p2)
            sessions[dist] = s2
            Thread {
                val rd = p2.inputStream.bufferedReader()
                while (true) {
                    val line = rd.readLine()
                    if (line == null) break
                    s2.append(line)
                }
            }.start()
            s0.append("重试成功，会话已恢复")
        }.start()
        return s0
    }

    fun stopShell(dist: String) {
        sessions.remove(dist)?.let { it.process.destroy() }
    }

    fun shellSession(dist: String): Session? = sessions[dist]

    fun sendCommand(dist: String, cmd: String) {
        val s = sessions[dist] ?: return
        try {
            s.process.outputStream.use { it.write((cmd + "\n").toByteArray()) }
        } catch (e: Exception) {}
    }

    /** 一次性脚本（配置镜像源/更新/装桌面），流式回调每行，结束回调 */
    fun runScript(ctx: Context, dist: String, script: String, onLine: (String) -> Unit, onDone: (Boolean) -> Unit) {
        val d = dist(dist)
        val root = rootDir(ctx, dist)
        val proot = prootBin(ctx, dist)
        if (!proot.exists() || !root.exists()) {
            onLine("环境未就绪：rootfs 或 proot 二进制缺失（" + proot.absolutePath + "），请返回重新下载解压")
            onDone(false)
            return
        }
        val cmd = ArrayList<String>()
        cmd.add(proot.absolutePath)
        cmd.add("-0")
        cmd.add("-r")
        cmd.add(root.absolutePath)
        cmd.add("-b")
        cmd.add("/sys")
        cmd.add("-w")
        cmd.add("/")
        cmd.add("/bin/sh")
        cmd.add("-c")
        cmd.add(script)
        Thread {
            try {
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true)
                val p = pb.start()
                val rd = p.inputStream.bufferedReader()
                while (true) {
                    val line = rd.readLine()
                    if (line == null) break
                    onLine(line)
                }
                val code = p.waitFor()
                onDone(code == 0)
            } catch (e: Exception) {
                onLine("执行出错：" + e.message)
                onDone(false)
            }
        }.start()
    }

    /** 写镜像源（宿主侧直接改 rootfs 文件，无需进 proot） */
    fun writeMirrors(ctx: Context, dist: String, lines: List<String>) {
        val d = dist(dist)
        val root = rootDir(ctx, dist)
        val text = lines.joinToString("\n")
        if (d.pkgMgr == "apt") {
            val f = File(root, "etc/apt/sources.list")
            f.parentFile?.mkdirs()
            java.io.FileOutputStream(f).use { it.write(text.toByteArray()) }
        } else {
            val f = File(root, "etc/apk/repositories")
            f.parentFile?.mkdirs()
            java.io.FileOutputStream(f).use { it.write(text.toByteArray()) }
        }
    }

    fun updateScript(d: Dist): String = if (d.pkgMgr == "apt") {
        "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get -y upgrade"
    } else {
        "apk update && apk upgrade"
    }

    fun desktopPkgs(d: Dist, de: String): String = when {
        d.pkgMgr == "apk" -> when (de) {
            "XFCE" -> "xfce4"
            "LXQt" -> "lxqt-opensuse"
            "MATE" -> "mate"
            else -> "xfce4"
        }
        else -> when (de) {
            "XFCE" -> "xfce4"
            "LXQt" -> "lxqt"
            "MATE" -> "mate-desktop-environment"
            else -> "xfce4"
        }
    }

    fun desktopInstallScript(d: Dist, de: String): String {
        val pkgs = desktopPkgs(d, de)
        return if (d.pkgMgr == "apk") {
            "apk add -q " + pkgs
        } else {
            "export DEBIAN_FRONTEND=noninteractive; apt-get install -y " + pkgs
        }
    }
}
