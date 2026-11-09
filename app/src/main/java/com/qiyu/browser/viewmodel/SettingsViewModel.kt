package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.BrowserConfig
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).settingsRepository

    val config: StateFlow<BrowserConfig> = repo.config

    fun setHomePage(url: String) = repo.setHomePage(url)
    fun setFontSize(size: Int) = repo.setFontSize(size)
    fun setDarkMode(enabled: Boolean) = repo.setDarkMode(enabled)
    fun setDataSaver(enabled: Boolean) = repo.setDataSaver(enabled)
    fun setAutoPlayVideos(enabled: Boolean) = repo.setAutoPlayVideos(enabled)
    fun setBlockPopups(enabled: Boolean) = repo.setBlockPopups(enabled)
}
