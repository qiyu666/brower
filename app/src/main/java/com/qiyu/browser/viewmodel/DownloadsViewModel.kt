package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 应用内提醒事件：某条下载进入终态（完成/失败） */
data class DownloadEvent(
    val token: Long,
    val downloadId: Long,
    val fileName: String,
    val success: Boolean,
    val detail: String
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).downloadRepository

    private val _filterStatus = MutableStateFlow<DownloadStatus?>(null)
    val filterStatus: StateFlow<DownloadStatus?> = _filterStatus

    val downloads: StateFlow<List<DownloadItem>> =
        _filterStatus.flatMapLatest { status ->
            if (status == null) repo.getAll() else repo.getByStatus(status)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 下载进入终态时发出的事件（token 单调递增，UI 据此识别新事件） */
    private var eventToken = 0L
    private val _lastEvent = MutableStateFlow<DownloadEvent?>(null)
    val lastEvent: StateFlow<DownloadEvent?> = _lastEvent

    init {
        viewModelScope.launch {
            val terminal = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)
            var firstEmission = true
            var lastStatus = emptyMap<Long, DownloadStatus>()
            repo.getAll().collect { list ->
                if (firstEmission) {
                    // 首次只记录存量状态，不对旧下载补提醒
                    firstEmission = false
                    lastStatus = list.associateBy({ it.id }, { it.status })
                    return@collect
                }
                val fresh = list.filter { it.status in terminal && lastStatus[it.id] != it.status }
                if (fresh.isNotEmpty()) {
                    lastStatus = list.associateBy({ it.id }, { it.status })
                    val item = fresh.maxBy { it.id }
                    eventToken++
                    _lastEvent.value = DownloadEvent(
                        token = eventToken,
                        downloadId = item.id,
                        fileName = item.fileName,
                        success = item.status == DownloadStatus.COMPLETED,
                        detail = item.errorMsg ?: ""
                    )
                }
            }
        }
    }

    fun setFilterStatus(status: DownloadStatus?) {
        _filterStatus.value = status
    }

    fun addDownload(item: DownloadItem) {
        viewModelScope.launch {
            repo.add(item)
        }
    }

    fun updateDownload(item: DownloadItem) {
        viewModelScope.launch {
            repo.update(item)
        }
    }

    fun removeDownload(item: DownloadItem) {
        viewModelScope.launch {
            repo.delete(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repo.deleteCompletedAndCancelled()
        }
    }
}
