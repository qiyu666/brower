package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.detectTapGestures
import androidx.compose.ui.input.pointer.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.qiyu.browser.model.Conversation
import com.qiyu.browser.model.ModelProvider
import com.qiyu.browser.ui.component.MarkdownText
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
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val activeProvider by vm.activeProvider.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val activeProviderId by vm.activeProviderId.collectAsStateWithLifecycle()
    val configured = vm.isConfigured()
    var input by remember { mutableStateOf("") }
    var showList by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>?>(null) }
    var showAddProvider by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var viewImage by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { pendingImage = imageToDataUrl(context, uri) }
    }

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
                Row(
                    modifier = Modifier
                        .clickable { showPicker = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        activeProvider?.let { it.name + if (it.model.isNotBlank()) " · " + it.model else "" }
                            ?: AppStrings.t("ai_select_model"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeProvider != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                    Icon(Icons.Filled.ExpandMore, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                IconButton(onClick = { showList = true }) {
                    Icon(Icons.Filled.Chat, AppStrings.t("ai_conversations"))
                }
                IconButton(onClick = { vm.newConversation() }) {
                    Icon(Icons.Filled.Add, AppStrings.t("ai_new_chat"))
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
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (messages.isEmpty() && !streaming) {
                        EmptyChatState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(count) { i ->
                                val m = messages.getOrNull(i)
                                if (m != null) {
                                    if (m.role == "user") UserBubble(m.content, m.image) { viewImage = it }
                                    else AssistantBubble(m.content)
                                } else {
                                    StreamingBubble(streamingText)
                                }
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Text(error!!, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (pendingImage != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DataUrlImage(pendingImage!!, 48.dp, 48.dp, 8.dp) { viewImage = pendingImage }
                    Spacer(Modifier.width(8.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { pendingImage = null }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Add, AppStrings.t("ai_attach_image"),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(4.dp))
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
                            val img = pendingImage
                            if (msg.isNotBlank() || img != null) {
                                input = ""; pendingImage = null
                                scope.launch { vm.send(msg, img) }
                            }
                        },
                        enabled = configured && (input.isNotBlank() || pendingImage != null)
                    ) { Icon(Icons.Default.Send, AppStrings.t("ai_send")) }
                }
            }
        }
    }

    if (showList) {
        ConversationDialog(
            conversations = conversations,
            currentId = currentId,
            onPick = { id -> vm.switchConversation(id); showList = false },
            onNew = { vm.newConversation(); showList = false },
            onDelete = { id -> scope.launch { vm.deleteConversation(id) } },
            onClose = { showList = false }
        )
    }

    if (showPicker) {
        ProviderPicker(
            providers = providers,
            activeId = activeProviderId,
            onSwitch = { vm.switchProvider(it) },
            onFetchModels = { prov -> vm.fetchModels(prov, { modelList = it }, { _ -> }) },
            onAdd = { showPicker = false; onNavigateToSettings() },
            onDelete = { vm.deleteProvider(it) },
            onClose = { showPicker = false }
        )
    }
    if (modelList != null && activeProviderId != 0L) {
        ModelListDialog(
            models = modelList ?: emptyList(),
            onPick = { m -> vm.setProviderModel(activeProviderId, m); modelList = null },
            onClose = { modelList = null }
        )
    }
    if (showAddProvider) {
        AddProviderDialog(
            onAdd = { n, u, k, m -> vm.addProvider(n, u, k, m); showAddProvider = false },
            onClose = { showAddProvider = false }
        )
    }

    viewImage?.let { ImageViewerDialog(it) { viewImage = null } }
}

@Composable
private fun ConversationDialog(
    conversations: List<Conversation>,
    currentId: Long?,
    onPick: (Long) -> Unit,
    onNew: () -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    AppStrings.t("ai_conversations"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, AppStrings.t("ai_new_chat")) }
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(20.dp)) }
            }
            if (conversations.isEmpty()) {
                Text(AppStrings.t("ai_no_conversations"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    conversations.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(c.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (c.id == currentId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Filled.Delete, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataUrlImage(dataUrl: String, w: Dp, h: Dp, radius: Dp = 10.dp, onClick: (() -> Unit)? = null) {
    val bmp = remember(dataUrl) {
        try {
            val b64 = dataUrl.substringAfter("base64,")
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    if (bmp != null) {
        var m = Modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(radius))
        if (onClick != null) m = m.clickable(onClick)
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = m,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap) {
                detectTransformGestures { _, _, zoom, pan ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    tx += pan.x
                    ty += pan.y
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(onDoubleTap = { scale = 1f; tx = 0f; ty = 0f })
            }
            .graphicsLayer {
                translateX = tx
                translateY = ty
                val s = scale
                scaleX = s
                scaleY = s
            }
    )
}

@Composable
private fun ImageViewerDialog(dataUrl: String, onClose: () -> Unit) {
    val bmp = remember(dataUrl) {
        try {
            val b64 = dataUrl.substringAfter("base64,")
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    Dialog(onDismissRequest = onClose, usePlatformDefaultWidth = false) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) ZoomableImage(bmp)
            Text(
                "✕",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onClose)
                    .padding(20.dp)
            )
        }
    }
}

@Composable
private fun UserBubble(content: String, image: String? = null, onImageClick: ((String) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
        if (image != null) {
            DataUrlImage(image, 160.dp, 120.dp, 12.dp) { onImageClick?.invoke(image) }
            if (content.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (content.isNotBlank() || image == null) {
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            MarkdownText(content)
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.Start) {
        if (text.isBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.t("ai_thinking"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                MarkdownText(text)
                Text("▍", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(46.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        Spacer(Modifier.height(12.dp))
        Text(AppStrings.t("ai_empty_hint"), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

@Composable
private fun ProviderPicker(
    providers: List<ModelProvider>,
    activeId: Long,
    onSwitch: (Long) -> Unit,
    onFetchModels: (ModelProvider) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Text(AppStrings.t("ai_model_provider"), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                providers.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSwitch(p.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint = if (p.id == activeId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge,
                                color = if (p.id == activeId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                            Text(p.model.ifBlank { p.baseUrl }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!p.builtin) {
                            IconButton(onClick = { onDelete(p.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Delete, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (p.id == activeId) {
                        Row(Modifier.fillMaxWidth().padding(start = 26.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(AppStrings.t("ai_model"), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { onFetchModels(p) }) { Text(AppStrings.t("ai_fetch")) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onAdd) { Text(AppStrings.t("ai_add_provider")) }
                TextButton(onClick = onClose) { Text(AppStrings.t("ai_close")) }
            }
        }
    }
}

@Composable
private fun ModelListDialog(models: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Text(AppStrings.t("ai_fetch"), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            if (models.isEmpty()) {
                Text(AppStrings.t("ai_no_models"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    models.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 8.dp)) {
                            Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(AppStrings.t("ai_close")) }
        }
    }
}

@Composable
private fun AddProviderDialog(onAdd: (String, String, String, String) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Text(AppStrings.t("ai_add_provider"), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(name, { name = it }, label = { Text(AppStrings.t("ai_provider_name")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(AppStrings.t("ai_base_url")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text(AppStrings.t("ai_api_key")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(model, { model = it }, label = { Text(AppStrings.t("ai_model")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(AppStrings.t("ai_cancel")) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (baseUrl.isNotBlank() || model.isNotBlank()) onAdd(name, baseUrl, apiKey, model) }) {
                    Text(AppStrings.t("ai_save"))
                }
            }
        }
    }
}

/** 读取并缩放图片为 base64 data URL（JPEG，最长边 1024px），供多模态发送；IO 线程执行。 */
suspend fun imageToDataUrl(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext null
            val maxSide = maxOf(raw.width, raw.height)
            val bmp = if (maxSide > 1024) {
                val f = 1024f / maxSide
                Bitmap.createScaledBitmap(raw, (raw.width * f).toInt(), (raw.height * f).toInt(), true)
            } else raw
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
