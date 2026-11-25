package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.viewmodel.AiChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: AiChatViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streamingText by vm.streamingText.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appModel = (context.applicationContext as com.qiyu.browser.NovaBrowserApp).settingsRepository.config.value.aiModel
    val configured = vm.isConfigured()
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val count = messages.size + if (streaming) 1 else 0
    LaunchedEffect(count, streamingText) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.t("ai_chat"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                if (appModel.isNotBlank()) {
                    Text(appModel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp))
                }
                IconButton(onClick = { vm.clear() }, enabled = messages.isNotEmpty() || streaming) {
                    Icon(Icons.Default.Delete, AppStrings.t("clear"))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!configured) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text(AppStrings.t("ai_not_configured"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onNavigateToSettings) { Text(AppStrings.t("ai_config_cta")) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(count) { i ->
                        val m = messages.getOrNull(i)
                        if (m != null) Bubble(m.content, m.role == "user")
                        else Bubble(streamingText, false, streaming = true)
                    }
                }
            }

            if (error != null) {
                Text(error!!, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = configured,
                    maxLines = 4,
                    placeholder = { Text(AppStrings.t("ai_input_hint")) }
                )
                Spacer(Modifier.width(8.dp))
                if (streaming) {
                    IconButton(onClick = { vm.stop() }) { Icon(Icons.Default.Stop, AppStrings.t("ai_stop")) }
                } else {
                    IconButton(
                        onClick = {
                            val msg = input
                            if (msg.isNotBlank()) { input = ""; scope.launch { vm.send(msg) } }
                        },
                        enabled = configured && input.isNotBlank()
                    ) { Icon(Icons.Default.Send, AppStrings.t("ai_send")) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(content: String, isUser: Boolean, streaming: Boolean = false) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            if (streaming && content.isEmpty()) "…" else content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
