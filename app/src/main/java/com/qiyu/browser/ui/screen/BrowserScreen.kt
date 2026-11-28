package com.qiyu.browser.ui.screen

import android.widget.FrameLayout
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Computer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.qiyu.browser.model.HistoryItem
import com.qiyu.browser.ui.component.AddressBar
import com.qiyu.browser.ui.component.BottomNav
import com.qiyu.browser.ui.component.TabStrip
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.util.UiDebugLog
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
    onScanQr: () -> Unit,
    onClearData: (Boolean, Boolean, Boolean) -> Unit,
    onDev: () -> Unit,
    onNavigateToAiChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by tabsVM.tabs.collectAsStateWithLifecycle()
    val activeTab by tabsVM.activeTab.collectAsStateWithLifecycle()
    // 激活标签兑底：activeTab 与列表失配（为 null 但还有标签）时用最后一个标签，
    // 保证地址栏/内容区在任何激活态下都不消失（WebView 实际显示的就是它）
    val barTab = activeTab ?: tabs.lastOrNull()
    val context = LocalContext.current
    val favBookmarks by bookmarksVM.bookmarks.collectAsStateWithLifecycle()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTrafficDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearSelHistory by remember { mutableStateOf(true) }
    var clearSelCookies by remember { mutableStateOf(true) }
    var clearSelCache by remember { mutableStateOf(true) }
    val isBookmarked by bookmarksVM.isBookmarked.collectAsStateWithLifecycle()
    val config by settingsVM.config.collectAsStateWithLifecycle()
    val menuOrder by settingsVM.menuOrder.collectAsStateWithLifecycle()
    val recentItems by historyVM.recentItems.collectAsStateWithLifecycle()
    var prevTabId by remember { mutableStateOf<Int?>(null) }
    val showNewTab = (barTab?.url.isNullOrEmpty() || barTab?.url == "about:blank")
        && (config.homePage.isEmpty() || config.homePage == "about:blank")
    // 诊断：显隐条件/激活态变化记日志（开发者面板可复制）
    var lastUiSig by remember { mutableStateOf("") }
    val uiSig = "snt=$showNewTab active=${activeTab?.id} bar=${barTab?.id}/${(barTab?.url ?: "").take(30)} tabs=${tabs.size} title=${(barTab?.title ?: "").take(20)} back=${barTab?.canGoBack} home=${config.homePage.take(20)}"
    LaunchedEffect(uiSig) {
        if (uiSig != lastUiSig) {
            UiDebugLog.log(context, "UI sig: $uiSig")
            lastUiSig = uiSig
        }
    }
    LaunchedEffect(tabs, activeTab) {
        if (activeTab == null && tabs.isNotEmpty()) {
            UiDebugLog.log(context, "ACTIVE MISMATCH: tabs=${tabs.map { it.id }} activeId=${tabsVM.activeTabId.value}")
        }
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (config.darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

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

        barTab?.let { tab ->
            // 诊断：地址栏卸载时记日志（复现“消失”的铁证）
            DisposableEffect(Unit) {
                onDispose {
                    UiDebugLog.log(context, "addressbar UNMOUNT tab=${tab.id} url='${tab.url.take(40)}' snt=$showNewTab active=${activeTab?.id} tabs=${tabs.size}")
                }
            }
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
                    val url = tab.url
                    if (isBookmarked) {
                        bookmarksVM.removeByURL(url)
                    } else {
                        bookmarksVM.addBookmark(tab.title.ifEmpty { WebUtils.extractDomain(url) }, url)
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
                        barTab?.let { tab ->
                            webManager.loadUrl(tab.id, url)
                            tabsVM.updateTabState(tab.id, url = url, isLoading = true)
                        }
                    },
                    searchEngine = config.searchEngine,
                    recentItems = recentItems,
                    favorites = favBookmarks,
                    isDark = isDark
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
                barTab?.let { tab ->
                    webManager.goBack(tab.id)
                }
            },
            onForward = {
                barTab?.let { tab ->
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
            canGoBack = barTab?.canGoBack ?: false,
            canGoForward = barTab?.canGoForward ?: false
        )
    }

    // Apply dark mode to all WebViews when it changes
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
            onCloseAll = {
                val ids = tabsVM.tabs.value.map { it.id }
                ids.forEach { id -> webManager.destroyWebView(id) }
                tabsVM.closeAllTabs()
                val tabId = tabsVM.addTab()
                webManager.createWebView(tabId, false)
                webManager.showTab(tabId)
                showTabSwitcher = false
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

    // 一键清理对话框：历史 / Cookie 与站点数据 / 缓存
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(AppStrings.t("clear_data_title")) },
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
                        Text(AppStrings.t("data_history"), modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { clearSelCookies = !clearSelCookies }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearSelCookies, onCheckedChange = { clearSelCookies = it })
                        Text(AppStrings.t("data_cookies"), modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { clearSelCache = !clearSelCache }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = clearSelCache, onCheckedChange = { clearSelCache = it })
                        Text(AppStrings.t("data_cache"), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearData(clearSelHistory, clearSelCookies, clearSelCache)
                    showClearDataDialog = false
                }) { Text(AppStrings.t("clear"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text(AppStrings.t("cancel")) }
            }
        )
    }

    // 密码建议：保存 / 自动填充（webManager.onPasswordEvent 主线程回调）
    var saveSug by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    var fillSug by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    LaunchedEffect(Unit) {
        webManager.onPasswordEvent = { tabId, event, host, extra ->
            when (event) {
                "save" -> saveSug = Triple(tabId, host, extra)
                "fill" -> fillSug = Triple(tabId, host, extra)
            }
        }
    }
    saveSug?.let { s ->
        AlertDialog(
            onDismissRequest = { webManager.discardSaveSuggestion(s.first); saveSug = null },
            title = { Text(AppStrings.t("save_password")) },
            text = {
                Column {
                    Text(AppStrings.t("website") + ": " + s.second)
                    Text(AppStrings.t("username") + ": " + (if (s.third.isNotEmpty()) s.third else "—"))
                }
            },
            confirmButton = {
                TextButton(onClick = { webManager.acceptSaveSuggestion(s.first); saveSug = null }) {
                    Text(AppStrings.t("save"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { webManager.blockSaveForHost(s.first, s.second); saveSug = null }) {
                        Text(AppStrings.t("never_save"))
                    }
                    TextButton(onClick = { webManager.discardSaveSuggestion(s.first); saveSug = null }) {
                        Text(AppStrings.t("cancel"))
                    }
                }
            }
        )
    }
    fillSug?.let { f ->
        AlertDialog(
            onDismissRequest = { fillSug = null },
            title = { Text(AppStrings.t("saved_password_found")) },
            text = { Text((if (f.third.isNotEmpty()) f.third + " · " else "") + f.second) },
            confirmButton = {
                TextButton(onClick = { webManager.fillSavedPassword(f.first, f.second, f.third); fillSug = null }) {
                    Text(AppStrings.t("auto_fill"), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { fillSug = null }) { Text(AppStrings.t("cancel")) }
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
                        barTab?.let { onShare(it.url, it.title) }
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
                    onClearData = {
                        showMenu = false
                        showClearDataDialog = true
                    },
                    onDev = {
                        showMenu = false
                        onDev()
                    },
                    onCopyLink = {
                        barTab?.let { tab ->
                            if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText(tab.title, tab.url))
                                }
                                android.widget.Toast.makeText(context, AppStrings.t("link_copied"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        showMenu = false
                    },
                    onDesktopMode = {
                        settingsVM.setUseDesktopMode(!config.useDesktopMode)
                        showMenu = false
                    },
                    desktopActive = config.useDesktopMode,
                    menuOrder = menuOrder,
                    onMenuOrderChanged = { settingsVM.setMenuOrder(it) },
                    onMenuOrderReset = { settingsVM.resetMenuOrder() },
                    onAiChat = {
                        onNavigateToAiChat()
                        showMenu = false
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
            title = { Text(AppStrings.t("traffic_title")) },
            text = {
                Column {
                    Text(AppStrings.t("traffic_page") + "：" + WebUtils.formatFileSize(pageBytes), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(AppStrings.t("traffic_session") + "：" + WebUtils.formatFileSize(webManager.sessionBytesTotal()), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(AppStrings.t("traffic_note"), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrafficDialog = false }) { Text(AppStrings.t("done")) }
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
    onCloseAll: () -> Unit,
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
                AppStrings.t("tabs_header") + " (" + tabs.size + ")",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                AppStrings.t("close_all"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable { onCloseAll() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, AppStrings.t("close"), tint = MaterialTheme.colorScheme.onSurface)
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

private class MenuDef(val key: String, val icon: ImageVector, val labelKey: String, val onClick: () -> Unit)

/** 底部菜单面板：图标网格（每行 4 个）。长按任一项进入排序，点按其它项交换位置，完成/恢复默认后持久化 */
@Composable
private fun OverflowMenuSheet(
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onIncognito: () -> Unit,
    onSettings: () -> Unit,
    onDev: () -> Unit,
    onShare: () -> Unit,
    onDesktopMode: () -> Unit,
    onTraffic: () -> Unit,
    onScanQr: () -> Unit,
    onClearData: () -> Unit,
    onCopyLink: () -> Unit,
    desktopActive: Boolean,
    menuOrder: List<String>,
    onMenuOrderChanged: (List<String>) -> Unit,
    onMenuOrderReset: () -> Unit,
    onAiChat: () -> Unit
) {
    val defs = listOf(
        MenuDef("ai_chat", Icons.Filled.AutoAwesome, "ai_chat", onAiChat),
        MenuDef("bookmarks", Icons.Filled.Star, "bookmarks", onBookmarks),
        MenuDef("downloads", Icons.Filled.Download, "downloads", onDownloads),
        MenuDef("share", Icons.Filled.Share, "share", onShare),
        MenuDef("settings", Icons.Filled.Settings, "settings", onSettings),
        MenuDef("history", Icons.Filled.History, "history", onHistory),
        MenuDef("dev_tools", Icons.Filled.Code, "dev_tools", onDev),
        MenuDef("desktop_mode", Icons.Filled.Computer, "desktop_mode", onDesktopMode),
        MenuDef("traffic", Icons.Filled.DataUsage, "traffic", onTraffic),
        MenuDef("scan_qr", Icons.Filled.QrCode, "scan_qr", onScanQr),
        MenuDef("clear_data", Icons.Filled.CleaningServices, "clear_data", onClearData),
        MenuDef("copy_link", Icons.Filled.Link, "copy_link", onCopyLink),
        MenuDef("incognito", Icons.Filled.Face, "incognito", onIncognito),
    )

    var reorderMode by remember { mutableStateOf(false) }
    var orderState by remember { mutableStateOf<List<String>?>(null) }
    var selIndex by remember { mutableIntStateOf(-1) }

    val activeOrder = orderState ?: menuOrder
    val ordered = activeOrder.mapNotNull { k -> defs.firstOrNull { it.key == k } }
        .let { it + defs.filter { d -> d.key !in activeOrder } }
    val rows = ordered.chunked(4)

    fun enterReorder() {
        if (!reorderMode) { orderState = menuOrder; reorderMode = true; selIndex = -1 }
    }
    fun finishReorder() {
        orderState?.let { onMenuOrderChanged(it) }
        reorderMode = false; orderState = null; selIndex = -1
    }
    fun resetReorder() {
        onMenuOrderReset(); reorderMode = false; orderState = null; selIndex = -1
    }
    fun onCellTap(index: Int) {
        if (selIndex < 0) { selIndex = index; return }
        val list = (orderState ?: menuOrder).toMutableList()
        val tmp = list[selIndex]; list[selIndex] = list[index]; list[index] = tmp
        orderState = list; selIndex = index
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 12.dp, bottom = 28.dp)
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))
        if (reorderMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("menu_reorder_hint"), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                TextButton(onClick = { resetReorder() }) { Text(AppStrings.t("menu_reset")) }
                TextButton(onClick = { finishReorder() }) { Text(AppStrings.t("done")) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    row.forEachIndexed { c, def ->
                        val g = r * 4 + c
                        GridMenuItem(
                            icon = def.icon,
                            label = AppStrings.t(def.labelKey),
                            accent = def.key == "desktop_mode" && desktopActive,
                            selected = selIndex == g,
                            badge = if (reorderMode) g + 1 else null,
                            modifier = Modifier.weight(1f),
                            onClick = { if (reorderMode) onCellTap(g) else def.onClick() },
                            onLongClick = { enterReorder() }
                        )
                    }
                    repeat((4 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GridMenuItem(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: Int? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent || selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(badge.toString(), color = Color.White,
                        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    text = AppStrings.t("incognito"),
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


