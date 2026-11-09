package com.qiyu.browser.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val isIncognito: Boolean = false
)
