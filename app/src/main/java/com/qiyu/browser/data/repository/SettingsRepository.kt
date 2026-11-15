package com.qiyu.browser.data.repository

import android.content.Context
import android.content.res.Resources
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
    fun setDarkMode(mode: String) = updateConfig { it.copy(darkMode = mode) }
    fun setDataSaver(enabled: Boolean) = updateConfig { it.copy(dataSaver = enabled) }
    fun setAutoPlayVideos(enabled: Boolean) = updateConfig { it.copy(autoPlayVideos = enabled) }
    fun setBlockPopups(enabled: Boolean) = updateConfig { it.copy(blockPopups = enabled) }
    fun setLanguage(lang: String) = updateConfig { it.copy(language = lang) }
    fun setSearchEngine(engine: String) = updateConfig { it.copy(searchEngine = engine) }
    fun setUseDesktopMode(enabled: Boolean) = updateConfig { it.copy(useDesktopMode = enabled) }
    fun setDownloadDir(dir: String) = updateConfig { it.copy(downloadDir = dir) }

    private fun loadConfig(): BrowserConfig {
        return BrowserConfig(
            homePage = prefs.getString("home_page", "") ?: "",
            searchEngine = prefs.getString("search_engine", "google") ?: "google",
            fontSize = prefs.getInt("font_size", 100),
            darkMode = prefs.getString("dark_mode", "auto") ?: "auto",
            dataSaver = prefs.getBoolean("data_saver", false),
            autoPlayVideos = prefs.getBoolean("auto_play", false),
            blockPopups = prefs.getBoolean("block_popups", true),
            language = resolveLanguage(),
            useDesktopMode = prefs.getBoolean("use_desktop", false),
            downloadDir = prefs.getString("download_dir", "default") ?: "default"
        )
    }

    /**
     * 首次打开（尚无语言偏好）时自动识别系统语言环境并写入偏好；
     * 之后以用户手动选择为准，不再覆盖。
     */
    private fun resolveLanguage(): String {
        if (prefs.contains("language")) {
            return prefs.getString("language", "en") ?: "en"
        }
        val supported = setOf("en", "zh")
        val systemLang = try {
            Resources.getSystem().configuration.locales[0].language
        } catch (e: Exception) {
            java.util.Locale.getDefault().language
        }
        val detected = if (systemLang in supported) systemLang else "en"
        prefs.edit().putString("language", detected).apply()
        return detected
    }

    private fun saveConfig(config: BrowserConfig) {
        prefs.edit().apply {
            putString("home_page", config.homePage)
            putString("search_engine", config.searchEngine)
            putInt("font_size", config.fontSize)
            putString("dark_mode", config.darkMode)
            putBoolean("data_saver", config.dataSaver)
            putBoolean("auto_play", config.autoPlayVideos)
            putBoolean("block_popups", config.blockPopups)
            putString("language", config.language)
            putBoolean("use_desktop", config.useDesktopMode)
            putString("download_dir", config.downloadDir)
        }.apply()
    }
}
