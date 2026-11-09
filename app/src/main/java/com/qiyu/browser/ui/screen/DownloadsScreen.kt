package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.viewmodel.DownloadsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val filter by viewModel.filterStatus.collectAsStateWithLifecycle()

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
                    "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (downloads.isNotEmpty()) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { viewModel.clearCompleted() }
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
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { viewModel.setFilterStatus(null) },
                    label = { Text("All", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = filter == DownloadStatus.IN_PROGRESS,
                    onClick = { viewModel.setFilterStatus(DownloadStatus.IN_PROGRESS) },
                    label = { Text("Active", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = filter == DownloadStatus.COMPLETED,
                    onClick = { viewModel.setFilterStatus(DownloadStatus.COMPLETED) },
                    label = { Text("Done", style = MaterialTheme.typography.labelMedium) }
                )
            }

            // Download list
            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(downloads) { item ->
                        DownloadItemRow(
                            item = item,
                            onOpen = { item.filePath?.let(onOpenFile) },
                            onDelete = { viewModel.removeDownload(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when (item.status) {
        DownloadStatus.IN_PROGRESS -> "${item.downloadedSize * 100 / (item.fileSize.coerceAtLeast(1))}%"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Download, "",
            modifier = Modifier.size(20.dp),
            tint = when (item.status) {
                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${WebUtils.formatFileSize(item.fileSize)}  ·  $statusText",
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
