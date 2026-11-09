package com.qiyu.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.navigation.BrowserNavHost
import com.qiyu.browser.service.DownloadService
import com.qiyu.browser.ui.theme.NovaBrowserTheme
import com.qiyu.browser.util.WebUtils
import com.qiyu.browser.util.WebViewListener
import com.qiyu.browser.util.WebViewManager
import com.qiyu.browser.viewmodel.*
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    lateinit var webManager: WebViewManager
    lateinit var tabsVM: TabsViewModel
    lateinit var bookmarksVM: BookmarksViewModel
    lateinit var historyVM: HistoryViewModel
    lateinit var downloadsVM: DownloadsViewModel
    lateinit var settingsVM: SettingsViewModel
    private var navController: androidx.navigation.NavHostController? = null
    private var backCallback: OnBackPressedCallback? = null

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
                    title = title,
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
                val resolvedName = fileName?.ifEmpty { null } ?: WebUtils.getFileNameFromUrl(url)
                lifecycleScope.launch {
                    val downloadItem = DownloadItem(url = url, fileName = resolvedName, mimeType = mimeType)
                    val downloadId = app.downloadRepository.add(downloadItem)
                    DownloadService.start(this@MainActivity, url, resolvedName, mimeType, downloadId)
                }
            }
        })

        intent?.data?.let { uri ->
            val tabId = tabsVM.addTab(uri.toString())
            webManager.createWebView(tabId, false)
            webManager.showTab(tabId)
            webManager.loadUrl(tabId, uri.toString())
        }

        if (tabsVM.tabs.value.isEmpty()) {
            val tabId = tabsVM.addTab()
            webManager.createWebView(tabId, false)
            webManager.showTab(tabId)
        }

        setContent {
            val config by settingsVM.config.collectAsStateWithLifecycle()

            NovaBrowserTheme(darkMode = config.darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    navController = nav

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
            val file = File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        webManager.destroyAll()
        super.onDestroy()
    }
}
