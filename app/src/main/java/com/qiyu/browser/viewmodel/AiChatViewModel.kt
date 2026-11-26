package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.AiMessage
import com.qiyu.browser.model.ChatMessage
import com.qiyu.browser.model.Conversation
import com.qiyu.browser.util.AiClient
import com.qiyu.browser.util.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = (application as NovaBrowserApp).settingsRepository
    private val conv = (application as NovaBrowserApp).conversationRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _currentId = MutableStateFlow<Long?>(null)
    val currentId: StateFlow<Long?> = _currentId

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var job: Job? = null

    init {
        scope.launch { conv.observeConversations().collect { _conversations.value = it } }
        scope.launch {
            _currentId.flatMapLatest { cid ->
                val base: Flow<List<AiMessage>> = if (cid == null) emptyFlow() else conv.observeMessages(cid)
                base.map { list -> list.map { ChatMessage(it.role, it.content) } }
            }.collect { _messages.value = it }
        }
        scope.launch {
            val list = conv.observeConversations().first()
            _currentId.value = if (list.isEmpty()) newId() else list.first().id
        }
    }

    private suspend fun newId(): Long = conv.insertConversation(Conversation(title = AppStrings.t("ai_new_chat")))

    fun isConfigured(): Boolean {
        val c = settings.config.value
        return c.aiBaseUrl.isNotBlank() && c.aiApiKey.isNotBlank() && c.aiModel.isNotBlank()
    }

    fun newConversation() {
        stop()
        scope.launch { _currentId.value = newId() }
    }

    fun switchConversation(id: Long) {
        if (_currentId.value == id) return
        stop()
        _currentId.value = id
    }

    fun deleteConversation(id: Long) {
        stop()
        scope.launch {
            conv.deleteConversation(id)
            if (_currentId.value == id) {
                val rest = conv.observeConversations().first().filter { it.id != id }
                _currentId.value = rest.firstOrNull()?.id ?: newId()
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _streaming.value || !isConfigured()) return
        _error.value = null
        val cfg = settings.config.value
        _streaming.value = true
        _streamingText.value = ""
        job = scope.launch {
            val cid = _currentId.value ?: newId().also { _currentId.value = it }
            val isFirst = conv.getMessages(cid).isEmpty()
            conv.addMessage(AiMessage(conversationId = cid, role = "user", content = t))
            if (isFirst) {
                conv.getConversation(cid)?.let { c ->
                    conv.updateConversation(c.copy(title = t.take(24), updatedAt = System.currentTimeMillis()))
                }
            }
            val req = conv.getMessages(cid).takeLast(HISTORY_LIMIT).map { ChatMessage(it.role, it.content) }
            try {
                val full = AiClient.streamChat(cfg.aiBaseUrl, cfg.aiApiKey, cfg.aiModel, req) { d ->
                    _streamingText.value += d
                }
                if (full.isNotEmpty()) conv.addMessage(AiMessage(conversationId = cid, role = "assistant", content = full))
            } catch (e: CancellationException) {
                val partial = _streamingText.value
                if (partial.isNotEmpty()) conv.addMessage(AiMessage(conversationId = cid, role = "assistant", content = partial))
            } catch (e: Exception) {
                val partial = _streamingText.value
                if (partial.isNotEmpty()) conv.addMessage(AiMessage(conversationId = cid, role = "assistant", content = partial))
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

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    companion object {
        private const val HISTORY_LIMIT = 40
    }
}
