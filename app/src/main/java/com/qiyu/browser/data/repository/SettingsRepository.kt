package com.qiyu.browser.data.repository

import android.content.Context
import android.content.res.Resources
import com.qiyu.browser.model.BrowserConfig
import com.qiyu.browser.model.ModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<BrowserConfig> = _config

    private val _menuOrder = MutableStateFlow(loadMenuOrder())
    val menuOrder: StateFlow<List<String>> = _menuOrder

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
    fun setAiBaseUrl(url: String) = updateConfig { it.copy(aiBaseUrl = url) }
    fun setAiApiKey(key: String) = updateConfig { it.copy(aiApiKey = key) }
    fun setAiModel(model: String) = updateConfig { it.copy(aiModel = model) }

    fun setMenuOrder(order: List<String>) {
        _menuOrder.value = order
        prefs.edit().putString("menu_order", JSONArray(order).toString()).apply()
    }

    /** 恢复默认菜单顺序 */
    fun resetMenuOrder() {
        _menuOrder.value = DEFAULT_MENU_ORDER
        prefs.edit().remove("menu_order").apply()
    }

    // ---- AI 模型供应商 ----
    private val _activeProviderId = MutableStateFlow(prefs.getLong("ai_active_provider", 0L))
    val activeProviderId: StateFlow<Long> = _activeProviderId

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ModelProvider>> = _providers

    fun upsertProvider(p: ModelProvider) {
        val list = _providers.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        _providers.value = list
        saveProviders(list)
    }

    fun deleteProvider(id: Long) {
        _providers.value = _providers.value.filter { it.id != id }
        saveProviders(_providers.value)
        if (_activeProviderId.value == id) setActiveProvider(_providers.value.firstOrNull()?.id ?: 0L)
    }

    fun setActiveProvider(id: Long) {
        _activeProviderId.value = id
        prefs.edit().putLong("ai_active_provider", id).apply()
    }

    /** 让全局 AI 字段（接口地址/密钥/模型）跟随指定供应商，保持设置页与实际使用一致 */
    fun syncGlobalFromProvider(providerId: Long) {
        val p = _providers.value.find { it.id == providerId } ?: return
        updateConfig { c -> c.copy(aiBaseUrl = p.baseUrl, aiApiKey = p.apiKey, aiModel = p.model) }
    }

    fun addProvider(name: String, baseUrl: String, apiKey: String, model: String) {
        val id = System.currentTimeMillis()
        upsertProvider(ModelProvider(id, name.ifBlank { "自定义" }, baseUrl, apiKey, model, false))
        setActiveProvider(id)
    }

    private fun saveProviders(list: List<ModelProvider>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("apiKey", p.apiKey)
                put("model", p.model)
                put("builtin", p.builtin)
            })
        }
        prefs.edit().putString("ai_providers", arr.toString()).apply()
    }

    private fun loadProviders(): List<ModelProvider> {
        val raw = prefs.getString("ai_providers", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                return (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ModelProvider(o.getLong("id"), o.getString("name"), o.optString("baseUrl"), o.optString("apiKey"), o.optString("model"), o.optBoolean("builtin"))
                }
            } catch (e: Exception) {
            }
        }
        return seedProviders()
    }

    private fun seedProviders(): List<ModelProvider> {
        val g = _config.value
        val list = mutableListOf<ModelProvider>()
        if (g.aiBaseUrl.isNotBlank() || g.aiModel.isNotBlank()) list.add(ModelProvider(1, "默认", g.aiBaseUrl, g.aiApiKey, g.aiModel))
        list.add(ModelProvider(2, "DeepSeek", "https://api.deepseek.com/v1", "", "deepseek-chat", true))
        list.add(ModelProvider(3, "OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini", true))
        list.add(ModelProvider(4, "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", true))
        saveProviders(list)
        if (_activeProviderId.value == 0L) _activeProviderId.value = list.firstOrNull()?.id ?: 0L
        return list
    }

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
            downloadDir = prefs.getString("download_dir", "default") ?: "default",
            aiBaseUrl = prefs.getString("ai_base_url", "") ?: "",
            aiApiKey = prefs.getString("ai_api_key", "") ?: "",
            aiModel = prefs.getString("ai_model", "") ?: ""
        )
    }

    /** 菜单项默认顺序（与溢出菜单定义一致；新增项在此追加，旧存档缺项时自动补全） */
    companion object {
        val DEFAULT_MENU_ORDER = listOf(
            "bookmarks", "history", "downloads",
            "ai_chat",
            "incognito", "settings", "dev_tools", "share", "desktop_mode",
            "traffic", "scan_qr", "clear_data", "copy_link"
        )
    }

    /** 读取持久化的菜单顺序；过滤未知项、补全新增项，缺失/损坏时回落默认顺序 */
    private fun loadMenuOrder(): List<String> {
        val raw = prefs.getString("menu_order", null) ?: return DEFAULT_MENU_ORDER
        val known = DEFAULT_MENU_ORDER
        return try {
            val arr = JSONArray(raw)
            val saved = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            val ordered = saved.filter { it in known } + known.filter { it !in saved }
            if (ordered.size != known.size || ordered.toSet().size != known.size) DEFAULT_MENU_ORDER else ordered
        } catch (e: Exception) {
            DEFAULT_MENU_ORDER
        }
    }

    /** 首次打开（尚无语言偏好）时自动识别系统语言环境并写入偏好；
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
            putString("ai_base_url", config.aiBaseUrl)
            putString("ai_api_key", config.aiApiKey)
            putString("ai_model", config.aiModel)
        }.apply()
    }
}
