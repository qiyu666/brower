package com.qiyu.browser.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index("url"), Index("visitTime")]
)
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val visitTime: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val isIncognito: Boolean = false
)
