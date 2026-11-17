package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/**
 * 开发者面板：
 * - 网络记录：WebViewManager.shouldInterceptRequest / onReceivedHttpError 捕获（主文档含状态码/大小）
 * - 控制台：WebChromeClient.onConsoleMessage
 * - 远程调试开关：电脑 Chrome 打开 chrome://inspect 连上本应用 WebView 用完整 DevTools
 */
@OptIn(ExperimentalMaterialApi::class)
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
                    Text("远程调试", style = MaterialTheme.typography.body1)
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
                if (netLog.isEmpty()) {
                    EmptyHint("暂无网络记录，翻几个页面再看看")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(netLog) { entry ->
                            NetRow(entry)
                        }
                    }
                }
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
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun statusColor(status: String): Color = when {
    status.startsWith("2") -> Color(0xFF4CAF50)
    status.startsWith("3") -> Color(0xFF2196F3)
    status.startsWith("4") || status.startsWith("5") -> Color(0xFFF44336)
    status == "error" -> Color(0xFFF44336)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun NetRow(entry: WebViewManager.DevNetEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            entry.status,
            style = MaterialTheme.typography.body1,
            color = statusColor(entry.status),
            modifier = Modifier.width(48.dp),
            maxLines = 1
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.url,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    if (entry.main) "主文档" else "子资源",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (entry.size.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
        Text(
            entry.time,
            style = MaterialTheme.typography.labelSmall,
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
                style = MaterialTheme.typography.body2,
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
