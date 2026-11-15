package com.qiyu.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import android.app.DownloadManager
import android.os.Environment
import android.net.Uri
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import com.qiyu.browser.navigation.BrowserNavHost
import com.qiyu.browser.navigation.Destinations
import com.qiyu.browser.service.DownloadService
import com.qiyu.browser.ui.theme.NovaBrowserTheme
import com.qiyu.browser.util.LocalDownload
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.util.WebViewListener
import com.qiyu.browser.util.WebViewManager
import com.qiyu.browser.viewmodel.*
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    lateinit var webManager: WebViewManager
    lateinit var tabsVM: TabsViewModel
    lateinit var bookmarksVM: BookmarksViewModel
    lateinit var historyVM: HistoryViewModel
    lateinit var downloadsVM: DownloadsViewModel
    lateinit var settingsVM: SettingsViewModel
    private var navController: androidx.navigation.NavHostController? = null
    private var backCallback: OnBackPressedCallback? = null
    private var pendingOpenDownloads = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NovaBrowserApp
        val vmProvider = ViewModelProvider(this)

        tabsVM = vmProvider.get(TabsViewModel::class.java)
        bookmarksVM = vmProvider.get(BookmarksViewModel::class.java)
        historyVM = vmProvider.get(HistoryViewModel::class.java)
        downloadsVM = vmProvider.get(DownloadsViewModel::class.java)
        settingsVM = vmProvider.get(SettingsViewModel::class.java)

        webManager = WebViewManager(this, app.settingsRepository, object : WebViewListener {
            override fun onPageStarted(tabId: Int, url: String) {
                tabsVM.setTabLoading(tabId, true)
            }

            override fun onPageFinished(tabId: Int, title: String, url: String) {
                val wv = webManager.getWebView(tabId)
                tabsVM.updateTabState(
                    tabId = tabId,
                    title = if (title.isEmpty() || title == "about:blank") "" else title,
                    url = url,
                    canGoBack = wv?.canGoBack() ?: false,
                    canGoForward = wv?.canGoForward() ?: false,
                    isLoading = false
                )
                val tab = tabsVM.tabs.value.find { it.id == tabId }
                if (tab != null && !tab.isIncognito && url.isNotEmpty() && url != "about:blank") {
                    lifecycleScope.launch {
                        bookmarksVM.saveToHistory(
                            title.ifEmpty { WebUtils.extractDomain(url) }, url, false
                        )
                    }
                }
            }

            override fun onProgressChanged(tabId: Int, progress: Int) {
                tabsVM.setTabLoading(tabId, progress < 100)
            }

            override fun onNewPage(tabId: Int, url: String) {}

            override fun onDownloadRequested(tabId: Int, url: String, fileName: String?, mimeType: String?) {
                val isIncognito = tabsVM.tabs.value.find { it.id == tabId }?.isIncognito ?: false
                val referer = webManager.getWebView(tabId)?.url
                val finalName = fileName ?: if (url.startsWith("blob:") || url.startsWith("data:")) "download" else WebUtils.getFileNameFromUrl(url)
                lifecycleScope.launch {
                    val app = application as NovaBrowserApp
                    val row = DownloadItem(url = url, fileName = finalName, mimeType = mimeType, isIncognito = isIncognito)
                    val id = app.downloadRepository.add(row)
                    val item = row.copy(id = id)
                    when {
                        url.startsWith("blob:") -> {
                            val wv = webManager.getWebView(tabId)
                            if (wv != null) {
                                LocalDownload.fromBlob(
                                    this@MainActivity, wv, app.downloadRepository,
                                    app.settingsRepository.config.value, item, url, finalName
                                )
                            } else {
                                app.downloadRepository.update(item.copy(status = DownloadStatus.FAILED, errorMsg = "WebView unavailable"))
                            }
                        }
                        url.startsWith("data:") ->
                            LocalDownload.fromDataUri(
                                this@MainActivity, app.downloadRepository,
                                app.settingsRepository.config.value, item, url, finalName, mimeType
                            )
                        url.startsWith("http://") || url.startsWith("https://") ->
                            DownloadService.start(this@MainActivity, url, finalName, mimeType, id, referer)
                        else ->
                            app.downloadRepository.update(item.copy(status = DownloadStatus.FAILED, errorMsg = "Unsupported URL: $url"))
                    }
                }
            }
        })

        intent?.let { data ->
            if (data.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
                pendingOpenDownloads = true
            }
        }
        intent?.data?.let { uri ->
            val tabId = tabsVM.addTab(uri.toString())
            webManager.createWebView(tabId, false)
            webManager.showTab(tabId)
            webManager.loadUrl(tabId, uri.toString())
        }

        if (tabsVM.tabs.value.isEmpty()) {
            val homeUrl = (application as NovaBrowserApp).settingsRepository.config.value.homePage
            val tabId = tabsVM.addTab(homeUrl)
            webManager.createWebView(tabId, false)
            webManager.showTab(tabId)
            if (homeUrl.isNotEmpty() && homeUrl != "about:blank") {
                webManager.loadUrl(tabId, homeUrl)
            }
        }

        setContent {
            val config by settingsVM.config.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (config.darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            NovaBrowserTheme(darkMode = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    navController = nav

                    if (pendingOpenDownloads) {
                        pendingOpenDownloads = false
                        LaunchedEffect(Unit) { nav.navigate(Destinations.DOWNLOADS) }
                    }

                    // 应用内下载提醒：终态事件横幅（NavHost 上方，所有页面可见）
                    val lastEvent by downloadsVM.lastEvent.collectAsStateWithLifecycle()
                    var consumedToken: Long by remember { mutableStateOf(0L) }
                    val activeEvent = lastEvent?.takeIf { it.token > consumedToken }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (activeEvent != null) {
                            val ev = activeEvent
                            DownloadEventBanner(
                                event = ev,
                                onView = {
                                    consumedToken = ev.token
                                    if (nav.currentDestination?.id != nav.graph.findNode(Destinations.DOWNLOADS)?.id) {
                                        nav.navigate(Destinations.DOWNLOADS)
                                    }
                                },
                                onDismiss = { consumedToken = ev.token }
                            )
                            LaunchedEffect(ev.token) {
                                kotlinx.coroutines.delay(6000)
                                consumedToken = ev.token
                            }
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            BrowserNavHost(
                                navController = nav,
                                tabsVM = tabsVM,
                                bookmarksVM = bookmarksVM,
                                historyVM = historyVM,
                                downloadsVM = downloadsVM,
                                settingsVM = settingsVM,
                                webManager = webManager,
                                onShare = { url, title -> shareUrl(url, title) },
                                onOpenFile = { path -> openFile(path) },
                                onOpenUrlInCurrentTab = { url ->
                                    val activeTab = tabsVM.activeTab.value
                                    if (activeTab != null) {
                                        webManager.loadUrl(activeTab.id, url)
                                        tabsVM.updateTabState(activeTab.id, url = url, isLoading = true)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val nav = navController
                if (nav != null && nav.previousBackStackEntry != null) {
                    nav.popBackStack()
                    return
                }

                val activeTabId = tabsVM.activeTabId.value
                if (activeTabId != null) {
                    if (webManager.goBack(activeTabId)) {
                        val wv = webManager.getWebView(activeTabId)
                        tabsVM.updateTabState(
                            activeTabId,
                            url = wv?.url ?: "",
                            canGoBack = wv?.canGoBack() ?: false,
                            canGoForward = wv?.canGoForward() ?: false
                        )
                        return
                    }
                }

                if (tabsVM.tabs.value.size > 1 && activeTabId != null) {
                    webManager.destroyWebView(activeTabId)
                    tabsVM.closeTab(activeTabId)
                    return
                }

                isEnabled = false
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    private fun shareUrl(url: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun openFile(path: String) {
        try {
            val intent = if (path.startsWith("content://")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(path))
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", File(path)
                )
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            navController?.let { nav ->
                if (nav.currentDestination?.id != nav.graph.findNode(Destinations.DOWNLOADS)?.id) {
                    nav.navigate(Destinations.DOWNLOADS)
                }
            }
        }
    }

    override fun onDestroy() {
        webManager.destroyAll()
        super.onDestroy()
    }
}

/** 应用内下载完成/失败横幅：点「查看」跳下载列表，点关闭或 6 秒后自动消失 */
@Composable
private fun DownloadEventBanner(
    event: com.qiyu.browser.viewmodel.DownloadEvent,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (event.success) Icons.Filled.Download else Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (event.success) "下载完成：${event.fileName}" else "下载失败：${event.fileName} ${event.detail}".trim(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onView) {
            Text("查看")
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
    }
}
