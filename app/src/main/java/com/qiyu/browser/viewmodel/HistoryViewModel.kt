package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.HistoryItem
import com.qiyu.browser.util.WebUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).historyRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val historyItems: StateFlow<List<HistoryItem>> =
        _searchQuery.flatMapLatest { query ->
            if (query.isEmpty()) repo.getAll() else repo.search(query)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentItems: StateFlow<List<HistoryItem>> =
        repo.getAll().map { it.take(4) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
        }
    }

    /** 清除指定时间段前的历史；days=0 表示全部 */
    fun clearBefore(days: Int) {
        viewModelScope.launch {
            if (days == 0) {
                repo.clearAll()
            } else {
                repo.deleteBefore(System.currentTimeMillis() - days * 86_400_000L)
            }
        }
    }

    fun removeItem(item: HistoryItem) {
        viewModelScope.launch {
            repo.deleteById(item.id)
        }
    }

    /** 本地历史联想（地址栏下拉用） */
    suspend fun suggest(query: String): List<String> {
        val items = repo.suggest(query, 4)
        return items.map { it.title.ifEmpty { WebUtils.extractDomain(it.url) } }
    }
}
