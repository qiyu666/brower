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
    onOpenUrlInCurrentTab: (String) -> Unit
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
                onDownload = { url, fileName, mimeType ->
                    onOpenFile(url)
                }
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
                onOpenFile = onOpenFile
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = settingsVM,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
