package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.BrowserConfig
import com.qiyu.browser.model.ModelProvider
import com.qiyu.browser.util.AppStrings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as NovaBrowserApp).settingsRepository

    val config: StateFlow<BrowserConfig> = repo.config
    val menuOrder: StateFlow<List<String>> = repo.menuOrder
    val providers: StateFlow<List<ModelProvider>> = repo.providers
    val activeProviderId: StateFlow<Long> = repo.activeProviderId

    /** 真实版本号（跟 build.gradle 的 versionName 同步，避免写死） */
    val versionName: String = try {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

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
    fun setUseDesktopMode(enabled: Boolean) = repo.setUseDesktopMode(enabled)
    fun setDownloadDir(dir: String) = repo.setDownloadDir(dir)
    fun setAiBaseUrl(url: String) = repo.setAiBaseUrl(url)
    fun setAiApiKey(key: String) = repo.setAiApiKey(key)
    fun setAiModel(model: String) = repo.setAiModel(model)
    fun setMenuOrder(order: List<String>) = repo.setMenuOrder(order)
    fun resetMenuOrder() = repo.resetMenuOrder()
    fun addProvider(name: String, baseUrl: String, apiKey: String, model: String) = repo.addProvider(name, baseUrl, apiKey, model)
    fun deleteProvider(id: Long) = repo.deleteProvider(id)
    fun setActiveProvider(id: Long) = repo.setActiveProvider(id)
}
