package com.qiyu.browser.util

import com.qiyu.browser.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object AiClient {

    /** 拉取模型列表（IO 线程）。base 需为 OpenAI 兼容根地址（含 /v1）。 */
    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val conn = URL(normalize(baseUrl) + "/models").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim())
            conn.setRequestProperty("Accept", "application/json")
            try {
                if (conn.responseCode != 200) throw IOException("HTTP " + conn.responseCode)
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("id", "") ?: ""
                    if (id.isNotEmpty()) list.add(id)
                }
                list
            } finally {
                conn.disconnect()
            }
        }

    /** 流式对话：逐块回调 [onDelta]，返回全文；取消时抛 CancellationException。 */
    suspend fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(normalize(baseUrl) + "/chat/completions").openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim())
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")

        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            val ma = JSONArray()
            for (m in messages) {
                ma.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
            put("messages", ma)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode != 200) {
            val err = try { conn.errorStream?.bufferedReader()?.readText().orEmpty() } catch (e: Exception) { "" }
            val msg = try {
                val j = JSONObject(err)
                j.optJSONObject("error")?.optString("message", err) ?: j.optString("message", err)
            } catch (e: Exception) { err }
            throw IOException("HTTP " + conn.responseCode + (if (msg.isNotBlank()) " " + msg.take(200) else ""))
        }

        val sb = StringBuilder()
        conn.inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
                ensureActive()
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = JSONObject(data)
                        val delta = obj.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content", "") ?: ""
                        if (delta.isNotEmpty()) {
                            sb.append(delta)
                            onDelta(delta)
                        }
                    } catch (e: Exception) {
                        // 忽略单条格式错误
                    }
                }
                line = reader.readLine()
            }
        }
        sb.toString()
    }

    /** 各常用端点的预设模型（远端获取失败或无密钥时兜底）。 */
    fun presetModels(baseUrl: String): List<String> {
        val u = baseUrl.lowercase()
        return when {
            u.contains("deepseek") -> listOf("deepseek-chat", "deepseek-reasoner")
            u.contains("dashscope") || u.contains("qwen") -> listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-long")
            u.contains("openai") -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
            else -> emptyList()
        }
    }

    /** 归一化 base URL：去首尾空白与结尾斜杠。 */
    private fun normalize(baseUrl: String): String = baseUrl.trim().trimEnd('/')
}
