package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewModelScope
import com.qiyu.browser.model.TabItem
import com.qiyu.browser.util.AppStrings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TabsViewModel(application: Application) : AndroidViewModel(application) {

    private var nextTabId = 1

    private val _tabs = MutableStateFlow<List<TabItem>>(emptyList())
    val tabs: StateFlow<List<TabItem>> = _tabs

    private val _activeTabId = MutableStateFlow<Int?>(null)
    val activeTabId: StateFlow<Int?> = _activeTabId

    val activeTab: StateFlow<TabItem?> =
        combine(_tabs, _activeTabId) { tabs, activeId ->
            tabs.find { it.id == activeId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasTabs: StateFlow<Boolean> =
        _tabs.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasIncognitoTabs: StateFlow<Boolean> =
        _tabs.map { tabs -> tabs.any { it.isIncognito } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun addTab(url: String = "", incognito: Boolean = false): Int {
        val id = nextTabId++
        val title = if (url.isEmpty()) AppStrings.t("new_tab") else url
        _tabs.update { it + TabItem(id = id, url = url, title = title, isIncognito = incognito) }
        _activeTabId.value = id
        return id
    }

    fun switchTab(tabId: Int) {
        _activeTabId.value = tabId
    }

    fun closeTab(tabId: Int) {
        _tabs.update { tabs ->
            val remaining = tabs.filter { it.id != tabId }
            if (_activeTabId.value == tabId) {
                _activeTabId.value = remaining.lastOrNull()?.id
            }
            remaining
        }
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun closeAllIncognitoTabs() {
        _tabs.update { tabs -> tabs.filter { !it.isIncognito } }
        if (_tabs.value.none { !it.isIncognito }) {
            _activeTabId.value = null
        } else if (_tabs.value.none { it.id == _activeTabId.value }) {
            _activeTabId.value = _tabs.value.first().id
        }
    }

    fun updateTabState(
        tabId: Int,
        title: String = "",
        url: String = "",
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
        isLoading: Boolean = false
    ) {
        _tabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) tab.copy(
                    title = title.ifEmpty { tab.title },
                    url = url.ifEmpty { tab.url },
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isLoading = isLoading
                ) else tab
            }
        }
    }

    fun setTabLoading(tabId: Int, isLoading: Boolean) {
        _tabs.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(isLoading = isLoading) else it }
        }
    }
}
