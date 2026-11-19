package com.qiyu.browser.ui.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiyu.browser.util.LinuxManager
import com.qiyu.browser.util.LinuxManager.Dist
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf("select") } // select | progress | config | terminal
    var distSel by remember { mutableStateOf("debian") }
    var progress by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (stage) {
                            "terminal" -> "终端 · " + distSel
                            "config" -> "配置 · " + distSel
                            "progress" -> "下载中 · " + distSel
                            else -> "容器"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (stage == "terminal") {
                            LinuxManager.stopShell(distSel)
                        } else if (stage != "select") {
                            stage = "select"
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        when (stage) {
            "select" -> DistList(ctx, pad, { d -> distSel = d; stage = "progress"; progress = 0 }, { d -> distSel = d; stage = "terminal" }, { d -> distSel = d; stage = "config" })
            "progress" -> DownloadStage(ctx, LinuxManager.dist(distSel), progress, pad, { ok ->
                if (ok) stage = "config"
                else stage = "select"
            })
            "config" -> ConfigWizard(ctx, LinuxManager.dist(distSel), pad, { stage = "terminal" })
            "terminal" -> TerminalView(ctx, LinuxManager.dist(distSel), pad, { stage = "select" })
        }
    }
}

@Composable
private fun DistList(
    ctx: Context, pad: PaddingValues,
    onDownload: (String) -> Unit, onEnter: (String) -> Unit, onConfig: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(LinuxManager.DISTROS) { d ->
            val installed = LinuxManager.isInstalled(ctx, d.name)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Folder, null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.label + " " + d.version, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${d.sizeMB} MB · ${if (d.pkgMgr == "apt") "apt" else "apk"} 包管理 · 内置 proot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (installed) {
                        Button(onClick = { onEnter(d.name) }, modifier = Modifier.padding(start = 8.dp)) { Text("进入终端") }
                        TextButton(onClick = { onConfig(d.name) }) { Text("重新配置") }
                    } else {
                        Button(onClick = { onDownload(d.name) }, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("下载")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStage(ctx: Context, d: Dist, progress: Int, pad: PaddingValues, onDone: (Boolean) -> Unit) {
    var pct by remember { mutableStateOf(progress) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(d.name) {
        val r = LinuxManager.setup(ctx, d) { p -> pct = p }
        if (r.isSuccess) onDone(true) else { failed = true; onDone(false) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            Icons.Filled.Download, null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(d.label + " " + d.version, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        if (failed) {
            Text(
                "下载失败：直连与 gh-proxy 代理均不可用，请检查网络后返回重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onDone(false) }) { Text("返回") }
        } else {
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (pct < 100) "下载中 ${pct}% · 解压需要一点时间…" else "解压中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** 镜像源预设：dist → (名称, 每行内容) */
private fun mirrorPresets(d: Dist): List<Pair<String, List<String>>> = when (d.pkgMgr) {
    "apk" -> listOf(
        "Alpine 官方" to listOf(
            "https://dl-cdn.alpinelinux.org/alpine/v3.22/main",
            "https://dl-cdn.alpinelinux.org/alpine/v3.22/community"
        ),
        "清华" to listOf(
            "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.22/main",
            "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.22/community"
        )
    )
    else -> when (d.name) {
        "debian" -> listOf(
            "Debian 官方" to listOf("http://deb.debian.org/debian trixie main"),
            "清华" to listOf("http://mirrors.tuna.tsinghua.edu.cn/debian trixie main"),
            "阿里" to listOf("https://mirrors.aliyun.com/debian trixie main"),
            "中科大" to listOf("https://mirrors.ustc.edu.cn/debian trixie main")
        )
        else -> listOf(
            "Ubuntu 官方" to listOf(
                "http://ports.ubuntu.com/ubuntu-ports noble main",
                "http://ports.ubuntu.com/ubuntu-ports noble-updates main"
            ),
            "清华" to listOf(
                "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports noble main",
                "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports noble-updates main"
            ),
            "阿里" to listOf(
                "https://mirrors.aliyun.com/ubuntu-ports noble main",
                "https://mirrors.aliyun.com/ubuntu-ports noble-updates main"
            ),
            "中科大" to listOf(
                "https://mirrors.ustc.edu.cn/ubuntu-ports noble main",
                "https://mirrors.ustc.edu.cn/ubuntu-ports noble-updates main"
            )
        )
    }
}

@Composable
private fun ConfigWizard(ctx: Context, d: Dist, pad: PaddingValues, onDone: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var preset by remember { mutableStateOf(1) }
    var customMirror by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }
    var wantDesktop by remember { mutableStateOf(false) }
    var desktopSel by remember { mutableStateOf("XFCE") }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(pad)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (step == 1) {
            Text("第一步：配置镜像源", style = MaterialTheme.typography.titleMedium)
            Text(
                "选一个源并自动执行软件包索引更新；国内网络推荐选国内源",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(10.dp))
            val presets = mirrorPresets(d)
            presets.forEachIndexed { i, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { preset = i; useCustom = false }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (i == preset && !useCustom) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (i == preset && !useCustom) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(p.first, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        p.second[0].substringBefore(' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { useCustom = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (useCustom) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = if (useCustom) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(Modifier.width(10.dp))
                Text("自定义", style = MaterialTheme.typography.bodyMedium)
            }
            if (useCustom) {
                OutlinedTextField(
                    value = customMirror,
                    onValueChange = { customMirror = it },
                    label = { Text("每行一条源，如 http://…  noble main") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val lines = if (useCustom) {
                        customMirror.lines().filter { it.isNotBlank() }
                    } else {
                        presets[preset].second
                    }
                    if (lines.isEmpty()) return@Button
                    LinuxManager.writeMirrors(ctx, d.name, lines)
                    logs = listOf("写入镜像源：") + lines
                    running = true
                    finished = false
                    LinuxManager.runScript(
                        ctx, d.name, LinuxManager.updateScript(d),
                        onLine = { line -> logs = (logs + line).takeLast(300) },
                        onDone = { ok ->
                            running = false
                            finished = true
                            logs = logs + if (ok) "软件包索引更新完成 ✓" else "更新失败：检查镜像源地址或网络"
                        }
                    )
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存并更新软件包") }
            ConsoleBox(logs)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (finished) {
                    Button(onClick = { step = 2 }) { Text("下一步：桌面") }
                } else {
                    TextButton(onClick = { step = 2 }, enabled = !running) { Text("跳过，下一步") }
                }
            }
        } else if (step == 2) {
            Text("第二步：安装桌面环境", style = MaterialTheme.typography.titleMedium)
            Text(
                "不装也能用终端和命令行软件；装了桌面可以配合 VNC 等方案起图形会话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("安装桌面环境", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = wantDesktop, onCheckedChange = { wantDesktop = it })
            }
            if (wantDesktop) {
                val des = if (d.pkgMgr == "apk") listOf("XFCE", "LXQt", "MATE") else listOf("XFCE", "LXQt", "GNOME", "MATE")
                des.forEach { de ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { desktopSel = de }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (desktopSel == de) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = if (desktopSel == de) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(de, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        logs = listOf("安装 " + desktopSel + "（可能需要几分钟）…")
                        running = true
                        finished = false
                        LinuxManager.runScript(
                            ctx, d.name, LinuxManager.desktopInstallScript(d, desktopSel),
                            onLine = { line -> logs = (logs + line).takeLast(300) },
                            onDone = { ok ->
                                running = false
                                finished = true
                                logs = logs + if (ok) "桌面安装完成 ✓" else "桌面安装失败，可稍后在终端手动执行"
                            }
                        )
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("安装 " + desktopSel) }
                ConsoleBox(logs)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { step = 1 }) { Text("上一步") }
                Button(onClick = { onDone() }, enabled = !running) { Text("进入终端") }
            }
        }
    }
}

@Composable
private fun ConsoleBox(logs: List<String>) {
    if (logs.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111418))
                .heightIn(max = 220.dp)
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(10.dp)) {
                logs.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFB8C4D6),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalView(ctx: Context, d: Dist, pad: PaddingValues, onClose: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(0) }
    var pasteTick by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // 会话：进终端时启动 shell（若已有活会话则复用）
    LaunchedEffect(d.name) {
        var s = LinuxManager.shellSession(d.name)
        if (s == null) s = LinuxManager.startShell(ctx, d.name)
    }

    // 粘贴：剪贴板读取是异步的
    LaunchedEffect(pasteTick) {
        if (pasteTick > 0) {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    clipboard.getText()
                }
                if (text != null) input = input + text
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val s = LinuxManager.shellSession(d.name)
            if (s != null) {
                val snap = s.snapshot()
                if (snap.size != size) {
                    size = snap.size
                    lines = snap.takeLast(500)
                }
            }
            delay(400)
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad)
            .background(Color(0xFF111418)),
        state = listState,
        contentPadding = PaddingValues(12.dp)
    ) {
        item {
            Text(
                "· " + d.label + " " + d.version + " · proot 会话 · 输入命令回车执行",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5E6A7A),
                fontFamily = FontFamily.Monospace
            )
        }
        items(lines) { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (line.startsWith("\u25b6 ")) Color(0xFF7EE0A0) else Color(0xFFD7DEE8),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u25b6", color = Color(0xFF7EE0A0), fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B2028),
                        unfocusedContainerColor = Color(0xFF1B2028),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFD7DEE8),
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF3A4250),
                        unfocusedBorderColor = Color(0xFF3A4250)
                    ),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { pasteTick = pasteTick + 1 }) { Text("粘贴") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    val c = input.trim()
                    if (c.isNotEmpty()) {
                        LinuxManager.sendCommand(d.name, c)
                        input = ""
                        lines = lines + "\u25b6 " + c
                    }
                }) { Text("执行") }
            }
        }
    }
}

