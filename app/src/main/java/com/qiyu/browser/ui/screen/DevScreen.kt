package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiyu.browser.util.WebViewManager
import java.net.URI

/**
 * 开发者面板：
 * - 网络：Edge 式完整抓取（状态码/类型/大小/耗时/请求响应头/响应预览，类型筛选 + URL 搜索）
 * - 控制台：WebChromeClient.onConsoleMessage
 * - 远程调试开关：电脑 Chrome 打开 chrome://inspect 连上本应用 WebView 用完整 DevTools
 */
private data class NetFilter(val id: String, val label: String)

@Composable
fun DevScreen(
    webManager: WebViewManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(0) } // 0=网络 1=控制台
    var netLog by remember { mutableStateOf<List<WebViewManager.DevNetEntry>>(emptyList()) }
    var consoleLog by remember { mutableStateOf<List<WebViewManager.DevConsoleEntry>>(emptyList()) }
    var remoteDebug by remember { mutableStateOf(webManager.remoteDebugging) }
    var typeFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            netLog = webManager.devNetLog().reversed()
            consoleLog = webManager.devConsoleLog().reversed()
            remoteDebug = webManager.remoteDebugging
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null)
                }
                Text("开发者", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { webManager.clearDevLogs() }) {
                    Text("清空")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    label = { Text("网络 ${netLog.size}") }
                )
                FilterChip(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    label = { Text("控制台 ${consoleLog.size}") }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("UI 日志（定位状态问题：复现后复制）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        com.qiyu.browser.util.UiDebugLog.dump().lines().takeLast(4).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 4
                    )
                }
                TextButton(onClick = {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("qiyu-ui-log", com.qiyu.browser.util.UiDebugLog.dump()))
                    android.widget.Toast.makeText(ctx, "UI 日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("复制") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("远程调试", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后电脑 Chrome 打开 chrome://inspect 可连上本应用 WebView",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = remoteDebug,
                    onCheckedChange = { webManager.setRemoteDebugging(it) }
                )
            }
            HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
            if (selected == 0) {
                NetworkPanel(netLog, typeFilter, query, { typeFilter = it }, { query = it })
            } else {
                if (consoleLog.isEmpty()) {
                    EmptyHint("暂无控制台输出（console.log 等会显示在这里）")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(consoleLog) { entry ->
                            ConsoleRow(entry)
                        }
                    }
                }
            }
        }
    }
}

private val TYPE_FILTERS = listOf(
    NetFilter("all", "全部"),
    NetFilter("xhr", "XHR"),
    NetFilter("js", "JS"),
    NetFilter("css", "CSS"),
    NetFilter("img", "图片"),
    NetFilter("doc", "文档"),
    NetFilter("font", "字体"),
    NetFilter("media", "媒体"),
    NetFilter("other", "其他")
)

@Composable
private fun NetworkPanel(
    netLog: List<WebViewManager.DevNetEntry>,
    typeFilter: String,
    query: String,
    onTypeFilter: (String) -> Unit,
    onQuery: (String) -> Unit
) {
    val filtered = netLog.filter { e ->
        (typeFilter == "all" || e.type == typeFilter) &&
            (query.isBlank() || e.url.contains(query, ignoreCase = true))
    }
    val totalBytes = filtered.sumOf { e -> if (e.size > 0) e.size else 0L }
    val pending = filtered.count { e -> e.statusText == "–" }
    var detail by remember { mutableStateOf<WebViewManager.DevNetEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 类型筛选 chips（横向滚动）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TYPE_FILTERS.forEach { f ->
                FilterChip(
                    selected = typeFilter == f.id,
                    onClick = { onTypeFilter(f.id) },
                    label = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        // URL 搜索
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            singleLine = true,
            placeholder = { Text("筛选 URL…", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        // 汇总行
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "共 ${filtered.size} 条 · ${fmtBytes(totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (pending > 0) {
                Text(
                    "进行中 $pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyHint("暂无网络记录，翻几个页面再看看")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { entry ->
                    NetRow(entry) { detail = entry }
                }
            }
        }
    }
    detail?.let { e ->
        NetDetailDialog(e) { detail = null }
    }
}

private fun fmtBytes(n: Long): String = when {
    n >= 1048576 -> "%.1f MB".format(n / 1048576.0)
    n >= 1024 -> "%.1f KB".format(n / 1024.0)
    n < 0 -> "–"
    else -> "$n B"
}

/** Edge 的 Name 列：显示路径段；解析失败退回 host */
private fun netName(url: String): String {
    return try {
        val u = URI(url)
        val p = u.path ?: ""
        if (p.isNotEmpty() && p != "/") p else (u.host ?: url)
    } catch (e: Exception) {
        url.take(60)
    }
}

@Composable
private fun statusColor(statusText: String): Color = when {
    statusText.startsWith("2") -> Color(0xFF4CAF50)
    statusText.startsWith("3") -> Color(0xFF2196F3)
    statusText.startsWith("4") || statusText.startsWith("5") -> Color(0xFFF44336)
    statusText == "error" -> Color(0xFFF44336)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun typeBadgeColor(type: String): Color = when (type) {
    "doc" -> Color(0xFF2196F3)
    "xhr" -> Color(0xFF4CAF50)
    "js" -> Color(0xFFFFC107)
    "css" -> Color(0xFF9C27B0)
    "img" -> Color(0xFF00BCD4)
    "font" -> Color(0xFF795548)
    "media" -> Color(0xFFE91E63)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun NetRow(entry: WebViewManager.DevNetEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                netName(entry.url),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (entry.main) "主文档" else entry.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeBadgeColor(entry.type)
                )
                Text(
                    entry.method,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    entry.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                entry.statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(entry.statusText)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    fmtBytes(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (entry.elapsedMs >= 0) {
                    Text(
                        if (entry.elapsedMs < 1000) "${entry.elapsedMs}ms" else "%.1fs".format(entry.elapsedMs / 1000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                entry.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun NetDetailDialog(entry: WebViewManager.DevNetEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${entry.method} · ${entry.statusText}",
                style = MaterialTheme.typography.titleMedium,
                color = statusColor(entry.statusText)
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetaChip("类型", entry.type)
                    MetaChip("大小", fmtBytes(entry.size))
                    MetaChip("耗时", if (entry.elapsedMs >= 0) "${entry.elapsedMs}ms" else "–")
                    MetaChip("时间", entry.time)
                    MetaChip("标签", entry.tabId.toString())
                }
                HeaderBlock("请求头", entry.reqHeaders)
                HeaderBlock("响应头", entry.resHeaders)
                if (entry.preview.isNotEmpty()) {
                    Text("响应预览", style = MaterialTheme.typography.labelMedium)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            entry.preview,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 60,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun MetaChip(label: String, value: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HeaderBlock(title: String, headers: Map<String, String>) {
    if (headers.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        headers.entries.forEach { (k, v) ->
            Text(
                "$k: $v",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun levelColor(level: String): Color = when (level) {
    "error" -> Color(0xFFF44336)
    "warn" -> Color(0xFFFF9800)
    "info" -> Color(0xFF2196F3)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun ConsoleRow(entry: WebViewManager.DevConsoleEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            entry.level,
            style = MaterialTheme.typography.labelMedium,
            color = levelColor(entry.level),
            modifier = Modifier.width(48.dp),
            maxLines = 1
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.message,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 6
            )
            if (entry.source.isNotEmpty()) {
                Text(
                    entry.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
