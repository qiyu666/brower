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

    /** 当前所在文件夹（null = 根目录） */
    private val _selectedFolder = MutableStateFlow<Bookmark?>(null)
    val selectedFolder: StateFlow<Bookmark?> = _selectedFolder

    /** 当前视图列表：搜索时全局搜，文件夹内看子项，否则根目录 */
    val currentList: StateFlow<List<Bookmark>> =
        combine(_searchQuery, _selectedFolder) { query, folder ->
            Triple(query, folder, "")
        }.flatMapLatest { (query, folder, _) ->
            when {
                query.isNotEmpty() -> repo.search(query)
                folder != null -> repo.getChildren(folder.id)
                else -> repo.getRoot()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openFolder(folder: Bookmark) {
        _selectedFolder.value = folder
    }

    fun closeFolder() {
        _selectedFolder.value = null
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repo.addFolder(name.trim().ifEmpty { "New Folder" }, _selectedFolder.value?.id)
        }
    }

    fun deleteFolder(folder: Bookmark) {
        viewModelScope.launch {
            repo.deleteFolderCascade(folder.id)
            if (_selectedFolder.value?.id == folder.id) _selectedFolder.value = null
        }
    }

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

    fun removeByURL(url: String) {
        viewModelScope.launch {
            repo.deleteByUrl(url)
            isBookmarked.value = false
        }
    }

    fun updateBookmarkTitle(bookmark: Bookmark, newTitle: String) {
        viewModelScope.launch {
            repo.update(bookmark.copy(title = newTitle.trim().ifEmpty { bookmark.title }))
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
