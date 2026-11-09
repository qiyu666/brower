package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.Bookmark
import com.qiyu.browser.model.HistoryItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).bookmarkRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val bookmarks: StateFlow<List<Bookmark>> =
        _searchQuery.flatMapLatest { query ->
            if (query.isEmpty()) repo.getAll() else repo.search(query)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isBookmarked: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            if (!repo.isBookmarked(url)) {
                repo.add(Bookmark(title = title, url = url))
            }
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repo.delete(bookmark)
        }
    }

    fun checkBookmarked(url: String) {
        viewModelScope.launch {
            isBookmarked.value = repo.isBookmarked(url)
        }
    }

    suspend fun saveToHistory(title: String, url: String, incognito: Boolean) {
        if (!incognito) {
            val historyRepo = (getApplication<Application>() as NovaBrowserApp).historyRepository
            historyRepo.add(HistoryItem(title = title, url = url))
        }
    }
}
