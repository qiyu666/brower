package com.qiyu.browser.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 搜索引擎联想（地址栏下拉）：按引擎拉取 suggest，失败/超时返回空，UI 回退本地历史。
 * - google / bing / duckduckgo 有公开 suggest 端点
 * - baidu 的 sugrec 需要 cookie，常拿不到，拿不到就只给本地历史
 */
object SearchSuggester {

    suspend fun fetchSuggests(engine: String, query: String, timeoutMs: Long = 2500): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = when (engine) {
                    "bing" -> "https://api.bing.com/osjson.aspx?query=${enc(query)}"
                    "duckduckgo" -> "https://duckduckgo.com/ac/?q=${enc(query)}"
                    "baidu" -> "https://www.baidu.com/sugrec?prod=web&wd=${enc(query)}&json=1"
                    else -> "https://suggestqueries.google.com/complete/search?client=firefox&q=${enc(query)}"
                }
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.apply {
                    connectTimeout = timeoutMs.toInt()
                    readTimeout = timeoutMs.toInt()
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux) QiyuBrowser/1.3")
                    if (engine == "baidu") setRequestProperty("Referer", "https://www.baidu.com/")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parse(body, engine)
            }.getOrDefault(emptyList())
        }

    private fun parse(body: String, engine: String): List<String> {
        return try {
            val list = when (engine) {
                "bing" -> JSONArray(body).mapIndexed { i, v -> if (i > 0) v.toString() else null }.filterNotNull()
                "duckduckgo" -> {
                    val obj = JSONObject(body)
                    (obj.optJSONArray("results") ?: JSONArray()).map { (it as JSONObject).optString("sugg", "") }
                        .filter { it.isNotBlank() }
                }
                "baidu" -> {
                    val obj = JSONObject(body)
                    val arr = obj.optJSONArray("Q") ?: obj.optJSONArray("q") ?: JSONArray()
                    (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
                }
                else -> {
                    val arr = JSONArray(body)
                    val inner = arr.optJSONArray(1) ?: JSONArray()
                    (0 until inner.length()).map {
                        val v = inner.opt(it)
                        when (v) {
                            is JSONArray -> v.optString(0, "")
                            else -> v.toString()
                        }
                    }.filter { it.isNotBlank() }
                }
            }
            list.distinct().take(6)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
