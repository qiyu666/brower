package com.qiyu.browser.data.repository

import android.content.Context
import com.qiyu.browser.model.BrowserConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<BrowserConfig> = _config

    fun updateConfig(transform: (BrowserConfig) -> BrowserConfig) {
        _config.value = transform(_config.value)
        saveConfig(_config.value)
    }

    fun setHomePage(url: String) = updateConfig { it.copy(homePage = url) }
    fun setFontSize(size: Int) = updateConfig { it.copy(fontSize = size) }
    fun setDarkMode(enabled: Boolean) = updateConfig { it.copy(darkMode = enabled) }
    fun setDataSaver(enabled: Boolean) = updateConfig { it.copy(dataSaver = enabled) }
    fun setAutoPlayVideos(enabled: Boolean) = updateConfig { it.copy(autoPlayVideos = enabled) }
    fun setBlockPopups(enabled: Boolean) = updateConfig { it.copy(blockPopups = enabled) }

    private fun loadConfig(): BrowserConfig {
        return BrowserConfig(
            homePage = prefs.getString("home_page", "https://www.google.com") ?: "https://www.google.com",
            searchEngine = prefs.getString("search_engine", "google") ?: "google",
            fontSize = prefs.getInt("font_size", 100),
            darkMode = prefs.getBoolean("dark_mode", false),
            dataSaver = prefs.getBoolean("data_saver", false),
            autoPlayVideos = prefs.getBoolean("auto_play", false),
            blockPopups = prefs.getBoolean("block_popups", true)
        )
    }

    private fun saveConfig(config: BrowserConfig) {
        prefs.edit().apply {
            putString("home_page", config.homePage)
            putString("search_engine", config.searchEngine)
            putInt("font_size", config.fontSize)
            putBoolean("dark_mode", config.darkMode)
            putBoolean("data_saver", config.dataSaver)
            putBoolean("auto_play", config.autoPlayVideos)
            putBoolean("block_popups", config.blockPopups)
        }.apply()
    }
}
