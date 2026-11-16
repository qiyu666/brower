package com.qiyu.browser.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 小组件配置页：添加 / 删除要显示的常用网址（最多 8 个），保存后小组件刷新 */
class SitesWidgetConfig : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Intent.RESULT_CANCELED)
            finish()
            return
        }
        setResult(AppWidgetManager.RESULT_CANCELED)

        val provider = SitesWidgetProvider()
        setContent {
            MaterialTheme {
                SitesConfigContent(
                    onDone = { sites ->
                        SitesWidgetStore.save(this, sites)
                        provider.refresh(this, appWidgetId)
                        val result = Intent().setResultCode(AppWidgetManager.RESULT_OK)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(result)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun SitesConfigContent(onDone: (List<SitesWidgetStore.Site>) -> Unit) {
    val context = LocalContext.current
    var sites by remember { mutableStateOf(SitesWidgetStore.get(context)) }
    var urlInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }

    val isFull = sites.size >= SitesWidgetStore.MAX_SITES

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("常用网址小组件", style = MaterialTheme.typography.titleMedium)
        Text(
            "添加要显示在桌面上的网址（最多 ${SitesWidgetStore.MAX_SITES} 个），点击小组件在本应用打开。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))

        // 已添加列表
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            sites.forEachIndexed { i, site ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(site.label.ifEmpty { site.url }, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(site.url, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { sites = sites.toMutableList().apply { removeAt(i) } },
                        modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (sites.isEmpty()) {
                Text("还没有添加网址", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp))
            }
        }

        // 添加行
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("网址（如 baidu.com）") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text("名称（可选）") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    val url = urlInput.trim()
                    if (url.isBlank()) return@TextButton
                    val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                    sites = sites + SitesWidgetStore.Site(finalUrl, labelInput.trim())
                    urlInput = ""
                    labelInput = ""
                },
                enabled = !isFull && urlInput.isNotBlank()
            ) {
                Text("添加")
            }
            TextButton(onClick = { onDone(sites) }) {
                Text("完成", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
