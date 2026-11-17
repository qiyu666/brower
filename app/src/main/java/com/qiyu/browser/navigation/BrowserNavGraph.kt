package com.qiyu.browser.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.qiyu.browser.ui.screen.*
import com.qiyu.browser.util.WebViewManager
import com.qiyu.browser.viewmodel.*

object Destinations {
    const val BROWSER = "browser"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val DEV = "dev"
}

@Composable
fun BrowserNavHost(
    navController: NavHostController,
    tabsVM: TabsViewModel,
    bookmarksVM: BookmarksViewModel,
    historyVM: HistoryViewModel,
    downloadsVM: DownloadsViewModel,
    settingsVM: SettingsViewModel,
    webManager: WebViewManager,
    onShare: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenWith: (String, String?) -> Unit,
    onShowInFolder: () -> Unit,
    onOpenUrlInCurrentTab: (String) -> Unit,
    onScanQr: () -> Unit,
    onClearData: (Boolean, Boolean, Boolean) -> Unit,
    onRetryDownload: (com.qiyu.browser.model.DownloadItem) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.BROWSER
    ) {
        composable(Destinations.BROWSER) {
            BrowserScreen(
                tabsVM = tabsVM,
                bookmarksVM = bookmarksVM,
                historyVM = historyVM,
                settingsVM = settingsVM,
                webManager = webManager,
                onNavigateToBookmarks = { navController.navigate(Destinations.BOOKMARKS) },
                onNavigateToHistory = { navController.navigate(Destinations.HISTORY) },
                onNavigateToDownloads = { navController.navigate(Destinations.DOWNLOADS) },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                onShare = onShare,
                onScanQr = onScanQr,
                onClearData = onClearData,
                onDev = { navController.navigate(Destinations.DEV) }
            )
        }
        composable(Destinations.BOOKMARKS) {
            BookmarksScreen(
                viewModel = bookmarksVM,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    navController.popBackStack(Destinations.BROWSER, inclusive = false)
                    onOpenUrlInCurrentTab(url)
                }
            )
        }
        composable(Destinations.HISTORY) {
            HistoryScreen(
                viewModel = historyVM,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    navController.popBackStack(Destinations.BROWSER, inclusive = false)
                    onOpenUrlInCurrentTab(url)
                }
            )
        }
        composable(Destinations.DOWNLOADS) {
            DownloadsScreen(
                viewModel = downloadsVM,
                onBack = { navController.popBackStack() },
                onOpenFile = onOpenFile,
                onOpenWith = onOpenWith,
                onShowInFolder = onShowInFolder,
                onRetry = onRetryDownload
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = settingsVM,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.DEV) {
            DevScreen(
                webManager = webManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
