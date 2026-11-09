package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).downloadRepository

    private val _filterStatus = MutableStateFlow<DownloadStatus?>(null)
    val filterStatus: StateFlow<DownloadStatus?> = _filterStatus

    val downloads: StateFlow<List<DownloadItem>> =
        _filterStatus.flatMapLatest { status ->
            if (status == null) repo.getAll() else repo.getByStatus(status)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
