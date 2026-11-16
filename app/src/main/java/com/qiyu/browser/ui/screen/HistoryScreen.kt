package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiyu.browser.model.HistoryItem
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.viewmodel.HistoryViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar

private data class DaySection(val label: String, val items: List<HistoryItem>)

/** 本地时区当日 0 点的时间戳（分组 key） */
private fun dayKey(time: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(key: Long): String {
    val nowKey = dayKey(System.currentTimeMillis())
    val diff = ((nowKey - key) / 86_400_000L).toInt()
    return when (diff) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(key))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var searchInput by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    // 按天分组（items 本身按 visitTime 降序，分组保持顺序）
    val daySections = remember(items) {
        items.groupBy { dayKey(it.visitTime) }.map { (key, list) -> DaySection(dayLabel(key), list) }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (items.isNotEmpty()) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { showClearDialog = true }
                            .padding(end = 12.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search, "",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it
                        viewModel.setSearchQuery(it)
                    },
                    singleLine = true,
                    placeholder = { Text("Search") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }

            // History list
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No browsing history",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(daySections) { section ->
                        item {
                            Text(
                                section.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(section.items) { item ->
                            HistoryItemRow(
                                item = item,
                                onOpen = { onOpenUrl(item.url) },
                                onDelete = { viewModel.removeItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 清除对话框：按时间段或全部
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history") },
            text = { Text("清除哪些时间的浏览记录？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearBefore(0)
                }) { Text("All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showClearDialog = false
                        viewModel.clearBefore(1)
                    }) { Text("1 day") }
                    TextButton(onClick = {
                        showClearDialog = false
                        viewModel.clearBefore(7)
                    }) { Text("7 days") }
                    TextButton(onClick = {
                        showClearDialog = false
                        viewModel.clearBefore(30)
                    }) { Text("1 month") }
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History, "",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifEmpty { WebUtils.extractDomain(item.url) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${WebUtils.extractDomain(item.url)}  ·  ${WebUtils.formatClock(item.visitTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Delete, "Delete",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
