package com.qiyu.browser.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release 更新检查与下载。
 * 查询：GET /releases/latest（公开仓库无需鉴权）。
 * 下载：先直连 GitHub；失败（不通/太慢导致超时）自动切 gh-proxy.com 代理重试。
 */
object UpdateChecker {

    private const val API_LATEST = "https://api.github.com/repos/qiyu666/brower/releases/latest"
    private const val PROXY_PREFIX = "https://gh-proxy.com/"

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val directUrl: String
    )

    /**
     * 把 APK 存到系统公共 Download 目录（用户文件管理器可见）——国内 ROM 拦截自动安装时的兑底：
     * 用户从文件管理器/快速访问安装。API 29+ 用 MediaStore，26-28 用公共目录（需存储权限，失败返 null）。
     */
    fun saveToPublicDownloads(context: Context, source: File, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: return null
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, done, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dir == null) return null
                source.copyTo(File(dir, fileName), overwrite = true)
                Uri.fromFile(File(dir, fileName))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 拉取最新 Release（IO 线程调用），无 release / 失败返回 null */
    fun fetchLatest(): UpdateInfo? {
        return try {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "").removePrefix("v")
            if (tag.isEmpty()) return null
            val notes = json.optString("body", "").take(400)

            var url = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name", "").endsWith(".apk")) {
                        url = a.optString("browser_download_url", "")
                        if (url.isNotEmpty()) break
                    }
                }
            }
            if (url.isEmpty()) {
                url = "https://github.com/qiyu666/brower/releases/download/${json.optString("tag_name")}/qiyu-browser.apk"
            }
            UpdateInfo(tag, notes, url)
        } catch (e: Exception) {
            null
        }
    }

    /** 目标版本是否比当前版本新（按 . 分段数字比较；无法解析时字符串不等即视为新） */
    fun isNewer(latest: String, current: String): Boolean {
        if (latest == current) return false
        val l = latest.split(".")
        val c = current.split(".")
        var comparable = true
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrNull(i)?.toIntOrNull()
            val b = c.getOrNull(i)?.toIntOrNull()
            if (a == null || b == null) {
                comparable = false
                break
            }
            if (a != b) return a > b
        }
        if (!comparable) return true
        return false
    }

    /**
     * 下载 APK 到 [dest]：先直连，IOException（不通/超时太慢）自动切 gh-proxy 重试。
     * onProgress 回调 0-100（可能在 IO 线程执行）。返回是否成功。
     */
    suspend fun download(directUrl: String, dest: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            tryDownload(directUrl, dest, onProgress) ||
                tryDownload(PROXY_PREFIX + directUrl, dest, onProgress)
        }

    private fun tryDownload(url: String, dest: File, onProgress: (Int) -> Unit): Boolean {
        dest.parentFile?.mkdirs()
        dest.delete()
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            if (conn.responseCode != 200) return false
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReported = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct - lastReported >= 5) {
                                lastReported = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            if (read == 0L) return false
            onProgress(100)
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }
}
