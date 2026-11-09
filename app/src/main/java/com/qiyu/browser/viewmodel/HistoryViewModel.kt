package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.HistoryItem
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
        }
    }

    fun removeItem(item: HistoryItem) {
        viewModelScope.launch {
            repo.deleteById(item.id)
        }
    }
}
