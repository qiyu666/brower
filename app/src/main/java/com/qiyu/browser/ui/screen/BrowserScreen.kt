package com.qiyu.browser.ui.screen

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.viewinterop.AndroidView
import com.qiyu.browser.model.HistoryItem
import com.qiyu.browser.ui.component.AddressBar
import com.qiyu.browser.ui.component.BottomNav
import com.qiyu.browser.ui.component.TabStrip
import com.qiyu.browser.util.WebViewManager
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.viewmodel.BookmarksViewModel
import com.qiyu.browser.viewmodel.HistoryViewModel
import com.qiyu.browser.viewmodel.SettingsViewModel
import com.qiyu.browser.viewmodel.TabsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    tabsVM: TabsViewModel,
    bookmarksVM: BookmarksViewModel,
    historyVM: HistoryViewModel,
    settingsVM: SettingsViewModel,
    webManager: WebViewManager,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShare: (String, String) -> Unit,
    onDownload: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by tabsVM.tabs.collectAsStateWithLifecycle()
    val activeTab by tabsVM.activeTab.collectAsStateWithLifecycle()
    val isBookmarked by bookmarksVM.isBookmarked.collectAsStateWithLifecycle()
    val config by settingsVM.config.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab strip
        if (tabs.size > 1 || tabs.firstOrNull()?.url?.isNotEmpty() == true) {
            TabStrip(
                tabs = tabs,
                activeTabId = activeTab?.id,
                onTabClick = { tabsVM.switchTab(it) },
                onTabClose = {
                    tabsVM.closeTab(it)
                    webManager.destroyWebView(it)
                },
                onNewTab = {
                    val homeUrl = config.homePage
                    val tabId = tabsVM.addTab(homeUrl)
                    webManager.createWebView(tabId, false)
                    webManager.showTab(tabId)
                    if (homeUrl.isNotEmpty() && homeUrl != "about:blank") {
                        webManager.loadUrl(tabId, homeUrl)
                    }
                },
                modifier = Modifier
            )
        }

        activeTab?.let { tab ->
            // Address bar
            AddressBar(
                url = tab.url,
                isLoading = tab.isLoading,
                canGoBack = tab.canGoBack,
                canGoForward = tab.canGoForward,
                isBookmarked = isBookmarked,
                onUrlSubmit = { input ->
                    val resolvedUrl = WebUtils.resolveUrlOrSearch(input, config.searchEngine)
                    webManager.loadUrl(tab.id, resolvedUrl)
                    tabsVM.updateTabState(tab.id, url = resolvedUrl, isLoading = true)
                },
                onBack = {
                    if (webManager.goBack(tab.id)) {
                        val wv = webManager.getWebView(tab.id)
                        tabsVM.updateTabState(
                            tab.id,
                            url = wv?.url ?: "",
                            canGoBack = wv?.canGoBack() ?: false,
                            canGoForward = wv?.canGoForward() ?: false
                        )
                    }
                },
                onForward = {
                    if (webManager.goForward(tab.id)) {
                        val wv = webManager.getWebView(tab.id)
                        tabsVM.updateTabState(
                            tab.id,
                            url = wv?.url ?: "",
                            canGoBack = wv?.canGoBack() ?: false,
                            canGoForward = wv?.canGoForward() ?: false
                        )
                    }
                },
                onRefresh = {
                    webManager.refresh(tab.id)
                    tabsVM.setTabLoading(tab.id, true)
                },
                onBookmarkToggle = {
                    if (isBookmarked) {
                        // Simplified: just toggle state
                    } else {
                        bookmarksVM.addBookmark(tab.title.ifEmpty { WebUtils.extractDomain(tab.url) }, tab.url)
                    }
                },
                modifier = Modifier
            )

            // Incognito indicator
            if (tab.isIncognito) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        "Incognito mode - browsing data won't be saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Progress bar
            if (tab.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val showNewTab = (activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank")
                && (config.homePage.isEmpty() || config.homePage == "about:blank")
            if (showNewTab) {
                NewTabPage(
                    onNavigate = { url ->
                        activeTab?.let { tab ->
                            webManager.loadUrl(tab.id, url)
                            tabsVM.updateTabState(tab.id, url = url, isLoading = true)
                        }
                    },
                    searchEngine = config.searchEngine
                )
            } else {
                AndroidView(
                    factory = { webManager.container },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom nav
        BottomNav(
            onBack = {
                activeTab?.let { tab ->
                    webManager.goBack(tab.id)
                }
            },
            onForward = {
                activeTab?.let { tab ->
                    webManager.goForward(tab.id)
                }
            },
            onNewTab = {
                val homeUrl = config.homePage
                val tabId = tabsVM.addTab(homeUrl)
                webManager.createWebView(tabId, false)
                webManager.showTab(tabId)
                if (homeUrl.isNotEmpty() && homeUrl != "about:blank") {
                    webManager.loadUrl(tabId, homeUrl)
                }
            },
            tabCount = tabs.size,
            onMenu = {
                // TODO: open menu dialog
            },
            canGoBack = activeTab?.canGoBack ?: false,
            canGoForward = activeTab?.canGoForward ?: false
        )
    }

    // Apply dark mode to all WebViews when it changes
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (config.darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    LaunchedEffect(isDark) {
        webManager.applyDarkMode(isDark)
    }

    // Observe bookmark state for current tab
    LaunchedEffect(activeTab?.url) {
        activeTab?.url?.let { url ->
            if (url.isNotEmpty() && url != "about:blank") {
                bookmarksVM.checkBookmarked(url)
            } else {
                bookmarksVM.checkBookmarked("")
            }
        }
    }
}
