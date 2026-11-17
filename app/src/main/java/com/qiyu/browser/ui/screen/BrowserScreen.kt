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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import com.qiyu.browser.model.HistoryItem
import com.qiyu.browser.ui.component.AddressBar
import com.qiyu.browser.ui.component.BottomNav
import com.qiyu.browser.ui.component.TabStrip
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.util.WebViewManager
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.util.SearchSuggester
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
    onScanQr: () -> Unit,
    onClearData: (Boolean, Boolean, Boolean) -> Unit,
    onDev: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by tabsVM.tabs.collectAsStateWithLifecycle()
    val activeTab by tabsVM.activeTab.collectAsStateWithLifecycle()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTrafficDialog by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearSelHistory by remember { mutableStateOf(true) }
    var clearSelCookies by remember { mutableStateOf(true) }
    var clearSelCache by remember { mutableStateOf(true) }
    val isBookmarked by bookmarksVM.isBookmarked.collectAsStateWithLifecycle()
    val config by settingsVM.config.collectAsStateWithLifecycle()
    val recentItems by historyVM.recentItems.collectAsStateWithLifecycle()
    var prevTabId by remember { mutableStateOf<Int?>(null) }
    val showNewTab = (activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank")
        && (config.homePage.isEmpty() || config.homePage == "about:blank")

    Box(modifier) {
    Column(
        modifier = Modifier.fillMaxSize()
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
                searchEngine = config.searchEngine,
                localSuggest = { q -> historyVM.suggest(q) },
                remoteSuggest = { q ->
                    val engine = settingsVM.config.value.searchEngine
                    SearchSuggester.fetchSuggests(engine, q)
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
                        AppStrings.t("incognito_banner"),
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
                    searchEngine = config.searchEngine,
                    recentItems = recentItems
                )
            } else {
                AndroidView(
                    factory = { webManager.refreshLayout },
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

    // Pause/resume tabs when switching (memory optimization)
    LaunchedEffect(activeTab?.id) {
        val prev: Int? = prevTabId
        val curr: Int? = activeTab?.id
        if (prev != null && prev != curr) {
            webManager.pauseTab(prev)
        }
        if (curr != null) {
            webManager.resumeTab(curr)
        }
        prevTabId = curr
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
            getThumbnail = { tabId -> webManager.getThumbnail(tabId) },
            getFavicon = { tabId -> webManager.getFavicon(tabId) }
        )
    }

    // 缩放档位对话框（双指缩放已默认开启，这是快捷档位）
    if (showZoomDialog && activeTab != null) {
        val current = webManager.currentZoom(activeTab!!.id)
        AlertDialog(
            onDismissRequest = { showZoomDialog = false },
            title = { Text("缩放") },
            text = {
                Column {
                    listOf(50, 75, 100, 125, 150, 200).forEach { pct ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    webManager.setZoom(activeTab!!.id, pct)
                                    showZoomDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (current == pct) Icons.Filled.Star else Icons.Filled.Close,
                                null,
                                modifier = Modifier.size(18.dp).padding(end = 10.dp),
                                tint = if (current == pct) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            )
                            Text(
                                "${pct}%",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoomDialog = false }) { Text("完成") }
            }
        )
    }

    // 一键清理对话框：历史 / Cookie 与站点数据 / 缓存
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("清理浏览数据") },
            text = {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { clearSelHistory = !clearSelHistory }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearSelHistory, onCheckedChange = { clearSelHistory = it })
                        Text("浏览历史", modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { clearSelCookies = !clearSelCookies }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearSelCookies, onCheckedChange = { clearSelCookies = it })
                        Text("Cookie 与站点数据", modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { clearSelCache = !clearSelCache }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearSelCache, onCheckedChange = { clearSelCache = it })
                        Text("缓存", modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearData(clearSelHistory, clearSelCookies, clearSelCache)
                    showClearDataDialog = false
                }) { Text("清理", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("取消") }
            }
        )
    }

    // 底部菜单抽屉：点 ⋮ 弹出（上滑动画 + 遮罩），点项或点遮罩/下滑关闭
    val sheetOffset by animateDpAsState(
        targetValue = if (showMenu) 0.dp else 520.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "menuSheetOffset"
    )
    val sheetScrim by animateFloatAsState(
        targetValue = if (showMenu) 0.5f else 0f,
        animationSpec = tween(250),
        label = "menuSheetScrim"
    )
    if (showMenu || sheetOffset < 519.dp) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = sheetScrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (showMenu) showMenu = false }
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .offset(y = sheetOffset)
            ) {
                OverflowMenuSheet(
                    onBookmarks = {
                        onNavigateToBookmarks()
                        showMenu = false
                    },
                    onHistory = {
                        onNavigateToHistory()
                        showMenu = false
                    },
                    onDownloads = {
                        onNavigateToDownloads()
                        showMenu = false
                    },
                    onIncognito = {
                        val tabId = tabsVM.addTab("", incognito = true)
                        webManager.createWebView(tabId, incognito = true)
                        webManager.showTab(tabId)
                        showMenu = false
                    },
                    onSettings = {
                        onNavigateToSettings()
                        showMenu = false
                    },
                    onShare = {
                        activeTab?.let { onShare(it.url, it.title) }
                        showMenu = false
                    },
                    onTraffic = {
                        showMenu = false
                        showTrafficDialog = true
                    },
                    onScanQr = {
                        showMenu = false
                        onScanQr()
                    },
                    onZoom = {
                        showMenu = false
                        showZoomDialog = true
                    },
                    onClearData = {
                        showMenu = false
                        showClearDataDialog = true
                    },
                    onDev = {
                        showMenu = false
                        onDev()
                    }
                )
            }
        }
    }

    // 流量统计：当前页 + 会话累计
    if (showTrafficDialog) {
        val tab = activeTab
        val pageBytes = tab?.let { webManager.currentPageBytes(it.id) } ?: 0L
        AlertDialog(
            onDismissRequest = { showTrafficDialog = false },
            title = { Text("流量统计") },
            text = {
                Column {
                    Text("当前页面：${WebUtils.formatFileSize(pageBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("本次会话累计：${WebUtils.formatFileSize(webManager.sessionBytesTotal())}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("统计主框架加载的字节数，不含缓存命中", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrafficDialog = false }) { Text("知道了") }
            }
        )
    }
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
    getFavicon: (Int) -> android.graphics.Bitmap?,
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
                        TabCard2(tab, tab.id == activeTabId, onSelect, { onClose(tab.id) }, true, getThumbnail, getFavicon, Modifier.weight(1f))
                    }
                    // Card at index row*2+1
                    tabs.getOrNull(row * 2 + 1)?.let { tab ->
                        TabCard2(tab, tab.id == activeTabId, onSelect, { onClose(tab.id) }, true, getThumbnail, getFavicon, Modifier.weight(1f))
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

/** 底部菜单面板：拖拽把手 + 图标网格（上排主色/常用，下排常规），仿 Chrome 溢出菜单 */
@Composable
private fun OverflowMenuSheet(
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onIncognito: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onTraffic: () -> Unit,
    onScanQr: () -> Unit,
    onZoom: () -> Unit,
    onClearData: () -> Unit,
    onDev: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 12.dp, bottom = 28.dp)
    ) {
        // 拖拽把手
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GridMenuItem(Icons.Filled.Star, "书签", accent = true, modifier = Modifier.weight(1f), onClick = onBookmarks)
            GridMenuItem(Icons.Filled.History, "历史记录", accent = true, modifier = Modifier.weight(1f), onClick = onHistory)
            GridMenuItem(Icons.Filled.Download, "下载", accent = true, modifier = Modifier.weight(1f), onClick = onDownloads)
        }
        Spacer(Modifier.height(12.dp))
        // 分隔线
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GridMenuItem(Icons.Filled.Face, "私密模式", modifier = Modifier.weight(1f), onClick = onIncognito)
            GridMenuItem(Icons.Filled.Settings, "设置", modifier = Modifier.weight(1f), onClick = onSettings)
            GridMenuItem(Icons.Filled.Code, "开发者", modifier = Modifier.weight(1f), onClick = onDev)
            GridMenuItem(Icons.Filled.Share, "分享", modifier = Modifier.weight(1f), onClick = onShare)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GridMenuItem(Icons.Filled.DataUsage, "流量", modifier = Modifier.weight(1f), onClick = onTraffic)
            GridMenuItem(Icons.Filled.QrCode, "扫码", modifier = Modifier.weight(1f), onClick = onScanQr)
            GridMenuItem(Icons.Filled.ZoomIn, "缩放", modifier = Modifier.weight(1f), onClick = onZoom)
            GridMenuItem(Icons.Filled.CleaningServices, "清理数据", modifier = Modifier.weight(1f), onClick = onClearData)
        }
    }
}

/** 网格单元格：图标在上、标签在下，整格可点 */
@Composable
private fun GridMenuItem(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabCard2(
    tab: com.qiyu.browser.model.TabItem,
    isActive: Boolean,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    showClose: Boolean,
    getThumbnail: (Int) -> android.graphics.Bitmap?,
    getFavicon: (Int) -> android.graphics.Bitmap?,
    cardModifier: Modifier = Modifier
) {
    val thumbnail = getThumbnail(tab.id)
    val favicon = getFavicon(tab.id)
    val borderColor = when {
        tab.isIncognito -> Color.Black
        isActive -> Color(0xFF1A73E8)
        else -> Color.Transparent
    }
    val host = try {
        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
            java.net.URL(tab.url).host.ifEmpty { tab.url }
        } else AppStrings.t("new_tab")
    } catch (e: Exception) {
        if (tab.url.isNotEmpty() && tab.url != "about:blank") tab.url else AppStrings.t("new_tab")
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
            if (favicon != null) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (host.isNotEmpty() && host != AppStrings.t("new_tab")) host.first().uppercase() else "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
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


