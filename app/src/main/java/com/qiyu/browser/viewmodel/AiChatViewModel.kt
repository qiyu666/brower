package com.qiyu.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.model.AiMessage
import com.qiyu.browser.model.ChatMessage
import com.qiyu.browser.model.Conversation
import com.qiyu.browser.model.ModelProvider
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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

    val providers: StateFlow<List<ModelProvider>> = settings.providers
    val activeProviderId: StateFlow<Long> = settings.activeProviderId
    val activeProvider: StateFlow<ModelProvider?> =
        settings.providers.combine(settings.activeProviderId) { list, id -> list.find { it.id == id } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { conv.observeConversations().collect { _conversations.value = it } }
        scope.launch {
            _currentId.flatMapLatest { cid ->
                val base: Flow<List<AiMessage>> = if (cid == null) emptyFlow() else conv.observeMessages(cid)
                base.map { list -> list.map { ChatMessage(it.role, it.content, it.image) } }
            }.collect { _messages.value = it }
        }
        scope.launch {
            val list = conv.observeConversations().first()
            _currentId.value = if (list.isEmpty()) newId() else list.first().id
        }
    }

    private suspend fun newId(): Long = conv.insertConversation(Conversation(title = AppStrings.t("ai_new_chat")))

    fun effectiveProvider(): ModelProvider? =
        settings.providers.value.find { it.id == settings.activeProviderId.value }

    fun isConfigured(): Boolean {
        val p = effectiveProvider()
        return p != null && p.baseUrl.isNotBlank() && p.model.isNotBlank()
    }

    fun activeModelName(): String =
        effectiveProvider()?.let { it.name + " · " + it.model } ?: settings.config.value.aiModel

    fun switchProvider(id: Long) {
        stop()
        settings.setActiveProvider(id)
        settings.syncGlobalFromProvider(id)
    }

    fun setProviderModel(providerId: Long, model: String) {
        val p = settings.providers.value.find { it.id == providerId } ?: return
        settings.upsertProvider(p.copy(model = model))
    }

    fun addProvider(name: String, baseUrl: String, apiKey: String, model: String) {
        val id = System.currentTimeMillis()
        val u = AiClient.normalizeBaseUrl(baseUrl)
        settings.upsertProvider(ModelProvider(id, name.ifBlank { "自定义" }, u, apiKey, model, false))
        settings.setActiveProvider(id)
    }

    fun deleteProvider(id: Long) {
        settings.deleteProvider(id)
    }

    fun fetchModels(provider: ModelProvider, onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try { onResult(AiClient.fetchModels(provider.baseUrl, provider.apiKey)) }
            catch (e: Exception) {
                val preset = AiClient.presetModels(provider.baseUrl)
                if (preset.isNotEmpty()) onResult(preset) else onError(e.message ?: AppStrings.t("ai_error"))
            }
        }
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

    fun send(text: String, image: String? = null) {
        val t = text.trim()
        if ((t.isEmpty() && image == null) || _streaming.value || !isConfigured()) return
        _error.value = null
        val p = effectiveProvider() ?: return
        _streaming.value = true
        _streamingText.value = ""
        job = scope.launch {
            val cid = _currentId.value ?: newId().also { _currentId.value = it }
            val isFirst = conv.getMessages(cid).isEmpty()
            conv.addMessage(AiMessage(conversationId = cid, role = "user", content = t, image = image))
            if (isFirst && t.isNotEmpty()) {
                conv.getConversation(cid)?.let { c ->
                    conv.updateConversation(c.copy(title = t.take(24), updatedAt = System.currentTimeMillis()))
                }
            }
            val base = conv.getMessages(cid).takeLast(HISTORY_LIMIT).map { ChatMessage(it.role, it.content) }
            val req = if (image != null && base.isNotEmpty()) {
                val last = base.last()
                base.dropLast(1) + ChatMessage(last.role, last.content, image)
            } else base
            try {
                val full = AiClient.streamChat(p.baseUrl, p.apiKey, p.model, req) { d ->
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
