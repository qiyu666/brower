package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.BrowserConfig
import com.qiyu.browser.util.AppStrings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).settingsRepository

    val config: StateFlow<BrowserConfig> = repo.config

    init {
        AppStrings.setLanguage(config.value.language)
    }

    fun setHomePage(url: String) = repo.setHomePage(url)
    fun setFontSize(size: Int) = repo.setFontSize(size)
    fun setDarkMode(mode: String) = repo.setDarkMode(mode)
    fun setDataSaver(enabled: Boolean) = repo.setDataSaver(enabled)
    fun setAutoPlayVideos(enabled: Boolean) = repo.setAutoPlayVideos(enabled)
    fun setBlockPopups(enabled: Boolean) = repo.setBlockPopups(enabled)
    fun setLanguage(lang: String) {
        repo.setLanguage(lang)
        AppStrings.setLanguage(lang)
    }
    fun setSearchEngine(engine: String) = repo.setSearchEngine(engine)
}
