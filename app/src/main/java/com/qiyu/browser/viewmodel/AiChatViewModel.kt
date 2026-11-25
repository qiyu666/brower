package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.ChatMessage
import com.qiyu.browser.util.AiClient
import com.qiyu.browser.util.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = (application as NovaBrowserApp).settingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var job: Job? = null

    fun isConfigured(): Boolean {
        val c = settings.config.value
        return c.aiBaseUrl.isNotBlank() && c.aiApiKey.isNotBlank() && c.aiModel.isNotBlank()
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _streaming.value || !isConfigured()) return
        _error.value = null
        val cfg = settings.config.value
        _messages.value = _messages.value + ChatMessage("user", t)
        _streaming.value = true
        _streamingText.value = ""
        job = scope.launch {
            val req = _messages.value.toList()
            try {
                val full = AiClient.streamChat(cfg.aiBaseUrl, cfg.aiApiKey, cfg.aiModel, req) { d ->
                    _streamingText.value += d
                }
                if (full.isNotEmpty()) _messages.value = _messages.value + ChatMessage("assistant", full)
            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = _streamingText.value
                if (partial.isNotEmpty()) _messages.value = _messages.value + ChatMessage("assistant", partial)
            } catch (e: Exception) {
                val partial = _streamingText.value
                if (partial.isNotEmpty()) _messages.value = _messages.value + ChatMessage("assistant", partial)
                _error.value = e.message ?: AppStrings.t("ai_error")
            } finally {
                _streaming.value = false
                _streamingText.value = ""
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        job?.cancel()
        _messages.value = emptyList()
        _streaming.value = false
        _streamingText.value = ""
        _error.value = null
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}