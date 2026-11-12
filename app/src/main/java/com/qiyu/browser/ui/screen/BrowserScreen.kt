package com.qiyu.browser.ui.screen

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isBookmarked by bookmarksVM.isBookmarked.collectAsStateWithLifecycle()
    val config by settingsVM.config.collectAsStateWithLifecycle()
    val showNewTab = (activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank")
        && (config.homePage.isEmpty() || config.homePage == "about:blank")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!showNewTab) {
        // Tab strip
        if (tabs.size > 1 || tabs.firstOrNull()?.url?.isNotEmpty() == true) {
            TabStrip(
                tabs = tabs,
                activeTabId = activeTab?.id,
                onTabClick = { tabsVM.switchTab(it) },
                onTabClose = {
                    val wasLast = tabsVM.tabs.value.size == 1
                    tabsVM.closeTab(it)
                    webManager.destroyWebView(it)
                    if (wasLast) {
                        val newTab = tabsVM.tabs.value.lastOrNull()
                        newTab?.let {
                            webManager.createWebView(it.id, false)
                            webManager.showTab(it.id)
                        }
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
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            onTabSwitcher = { showTabSwitcher = true },
            onMenu = { showMenu = true },
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

    // Apply desktop mode to all WebViews when it changes
    LaunchedEffect(config.useDesktopMode) {
        webManager.applyDesktopMode(config.useDesktopMode)
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

    // Tab Switcher Overlay
    if (showTabSwitcher) {
        TabSwitcherOverlay(
            tabs = tabs,
            activeTabId = activeTab?.id,
            onSelect = { tabId ->
                tabsVM.switchTab(tabId)
                webManager.showTab(tabId)
                showTabSwitcher = false
            },
            onClose = { tabId ->
                val wasLast = tabsVM.tabs.value.size == 1
                tabsVM.closeTab(tabId)
                webManager.destroyWebView(tabId)
                if (wasLast) {
                    val newTab = tabsVM.tabs.value.lastOrNull()
                    newTab?.let {
                        webManager.createWebView(it.id, false)
                        webManager.showTab(it.id)
                    }
                }
            },
            onNewTab = {
                val tabId = tabsVM.addTab()
                webManager.createWebView(tabId, false)
                webManager.showTab(tabId)
                showTabSwitcher = false
            },
            onDismiss = { showTabSwitcher = false },
            getThumbnail = { tabId -> webManager.getThumbnail(tabId) }
        )
    }

    // Menu dialog
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            text = {
                Column {
                    MenuRow("\u4e66\u7b7e") { onNavigateToBookmarks(); showMenu = false }
                    MenuRow("\u5386\u53f2\u8bb0\u5f55") { onNavigateToHistory(); showMenu = false }
                    MenuRow("\u4e0b\u8f7d") { onNavigateToDownloads(); showMenu = false }
                    MenuRow("\u79c1\u5bc6\u6a21\u5f0f") {
                        val tabId = tabsVM.addTab("", incognito = true)
                        webManager.createWebView(tabId, incognito = true)
                        webManager.showTab(tabId)
                        showMenu = false
                    }
                    MenuRow("\u8bbe\u7f6e") { onNavigateToSettings(); showMenu = false }
                    MenuRow("\u5206\u4eab") {
                        activeTab?.let { onShare(it.url, it.title) }
                        showMenu = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }
}

@Composable
fun TabSwitcherOverlay(
    tabs: List<com.qiyu.browser.model.TabItem>,
    activeTabId: Int?,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    getThumbnail: (Int) -> android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "标签页 (${tabs.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Tab cards 2-column layout
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tabs.size / 2 + 1) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card at index row*2
                    tabs.getOrNull(row * 2)?.let { tab ->
                        TabCard2(tab, tab.id == activeTabId, onSelect, { onClose(tab.id) }, tabs.size > 1, getThumbnail, Modifier.weight(1f))
                    }
                    // Card at index row*2+1
                    tabs.getOrNull(row * 2 + 1)?.let { tab ->
                        TabCard2(tab, tab.id == activeTabId, onSelect, { onClose(tab.id) }, tabs.size > 1, getThumbnail, Modifier.weight(1f))
                    }
                    // If only one card in this row, fill remaining space
                    if (tabs.getOrNull(row * 2 + 1) == null && tabs.getOrNull(row * 2) != null) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Floating new tab button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1A73E8),
                        shadowElevation = 4.dp,
                        modifier = Modifier.clickable { onNewTab() }
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, "", tint = Color.White,
                                modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
private fun TabCard2(
    tab: com.qiyu.browser.model.TabItem,
    isActive: Boolean,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    showClose: Boolean,
    getThumbnail: (Int) -> android.graphics.Bitmap?,
    cardModifier: Modifier = Modifier
) {
    val thumbnail = getThumbnail(tab.id)
    val borderColor = when {
        tab.isIncognito -> Color.Black
        isActive -> Color(0xFF1A73E8)
        else -> Color.Transparent
    }
    val host = try {
        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
            java.net.URL(tab.url).host.ifEmpty { tab.url }
        } else "新标签页"
    } catch (e: Exception) {
        if (tab.url.isNotEmpty() && tab.url != "about:blank") tab.url else "新标签页"
    }

    Column(
        modifier = cardModifier
            .clickable { onSelect(tab.id) }
    ) {
        // Header: site initial + host + close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (host.isNotEmpty() && host != "新标签页") host.first().uppercase() else "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            if (tab.isIncognito) {
                Text(
                    text = "\u9690\u8eab",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E),
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showClose) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, "",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}


