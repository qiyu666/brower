package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.util.UpdateChecker
import com.qiyu.browser.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var homeInput by remember { mutableStateOf(config.homePage) }
    var engineDialog by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var downloadDirDialog by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {}
            viewModel.setDownloadDir(it.toString())
        }
        downloadDirDialog = false
    }

    // 点击“版本”行：查 GitHub Releases Latest，结果弹框
    fun startCheck() {
        if (updateState is UpdateCheckState.Checking || updateState is UpdateCheckState.Downloading) return
        updateState = UpdateCheckState.Checking
        scope.launch {
            val current = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (e: Exception) { "" }
            val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            val newState: UpdateCheckState = when {
                info == null -> UpdateCheckState.Failed("检查失败：无法连接 GitHub，请稍后重试")
                !UpdateChecker.isNewer(info.version, current) -> UpdateCheckState.UpToDate(info.version)
                else -> UpdateCheckState.NewVersion(info)
            }
            updateState = newState
        }
    }

    fun startDownload(info: UpdateChecker.UpdateInfo) {
        updateState = UpdateCheckState.Downloading(info, 0)
        scope.launch {
            val target = File(context.getExternalFilesDir(null), "updates/qiyu-browser-${info.version}.apk")
            val ok = UpdateChecker.download(info.directUrl, target) { pct ->
                updateState = UpdateCheckState.Downloading(info, pct)
            }
            val newState: UpdateCheckState = if (ok) {
                UpdateCheckState.ReadyToInstall(target, info.version)
            } else {
                UpdateCheckState.Failed("下载失败：直连与 gh-proxy 代理均不可用，请稍后重试")
            }
            updateState = newState
        }
    }

    fun installUpdate(file: File, version: String) {
        updateState = UpdateCheckState.Idle
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开安装器：${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    // 更新检查弹窗
    when (val st = updateState) {
        is UpdateCheckState.Checking -> AlertDialog(
            onDismissRequest = { },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text("正在检查更新…")
                }
            },
            confirmButton = {}
        )
        is UpdateCheckState.UpToDate -> AlertDialog(
            onDismissRequest = { updateState = UpdateCheckState.Idle },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "已是最新版本 v${st.latestVersion}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { updateState = UpdateCheckState.Idle }) { Text("好的") }
            }
        )
        is UpdateCheckState.NewVersion -> AlertDialog(
            onDismissRequest = { updateState = UpdateCheckState.Idle },
            title = { Text("发现新版本 v${st.info.version}") },
            text = { Text(if (st.info.notes.isNotEmpty()) st.info.notes else "点击“更新”下载最新版本") },
            confirmButton = {
                TextButton(onClick = { startDownload(st.info) }) { Text("更新") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateCheckState.Idle }) { Text("稍后") }
            }
        )
        is UpdateCheckState.Downloading -> AlertDialog(
            onDismissRequest = { },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("正在下载 v${st.info.version}… ${st.progress}%")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { st.progress / 100f }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
        is UpdateCheckState.ReadyToInstall -> AlertDialog(
            onDismissRequest = { updateState = UpdateCheckState.Idle },
            title = { Text("下载完成") },
            text = { Text("现在安装 v${st.version}？") },
            confirmButton = {
                TextButton(onClick = { installUpdate(st.file, st.version) }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateCheckState.Idle }) { Text("取消") }
            }
        )
        is UpdateCheckState.Failed -> AlertDialog(
            onDismissRequest = { updateState = UpdateCheckState.Idle },
            text = { Text(st.message) },
            confirmButton = {
                TextButton(onClick = { updateState = UpdateCheckState.Idle }) { Text("知道了") }
            }
        )
        UpdateCheckState.Idle -> {}
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    AppStrings.t("settings"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Language
            SectionTitle(AppStrings.t("language"))
            SettingRow(
                title = AppStrings.t("language"),
                value = if (config.language == "zh") "中文" else "English",
                onClick = { langDialog = true }
            )

            // Search engine
            SectionTitle(AppStrings.t("search_engine"))
            SettingRow(
                title = AppStrings.t("search_engine"),
                value = config.searchEngine.replaceFirstChar { it.uppercase() },
                onClick = { engineDialog = true }
            )

            // Appearance
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(AppStrings.t("appearance"))

            SettingRow(
                icon = { Icon(Icons.Default.DarkMode, "", modifier = Modifier.size(20.dp)) },
                title = AppStrings.t("dark_mode"),
                value = when (config.darkMode) {
                    "auto" -> "Auto"
                    "dark" -> "Dark"
                    else -> "Light"
                }
            ) {
                // Cycle: auto -> light -> dark -> auto
                val next = when (config.darkMode) {
                    "auto" -> "light"
                    "light" -> "dark"
                    else -> "auto"
                }
                viewModel.setDarkMode(next)
            }

            SettingRow(
                icon = { Icon(Icons.Default.TextFields, "", modifier = Modifier.size(20.dp)) },
                title = AppStrings.t("font_size"),
                value = "${config.fontSize}%"
            ) {
                val sizes = listOf(75, 85, 100, 115, 125, 150)
                val idx = sizes.indexOf(config.fontSize).coerceAtLeast(0)
                viewModel.setFontSize(sizes[(idx + 1) % sizes.size])
            }

            // Behavior
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(AppStrings.t("behavior"))

            SettingRow(
                icon = { Icon(Icons.Default.Gesture, "", modifier = Modifier.size(20.dp)) },
                title = AppStrings.t("data_saver"),
                value = if (config.dataSaver) AppStrings.t("on") else AppStrings.t("off")
            ) {
                viewModel.setDataSaver(!config.dataSaver)
            }

            SettingRow(
                icon = { Icon(Icons.Default.PlayCircle, "", modifier = Modifier.size(20.dp)) },
                title = AppStrings.t("auto_play"),
                value = if (config.autoPlayVideos) AppStrings.t("on") else AppStrings.t("off")
            ) {
                viewModel.setAutoPlayVideos(!config.autoPlayVideos)
            }

            SettingRow(
                icon = { Icon(Icons.Default.Shield, "", modifier = Modifier.size(20.dp)) },
                title = AppStrings.t("block_popups"),
                value = if (config.blockPopups) AppStrings.t("on") else AppStrings.t("off")
            ) {
                viewModel.setBlockPopups(!config.blockPopups)
            }

            SettingRow(
                icon = { Icon(Icons.Default.Computer, "", modifier = Modifier.size(20.dp)) },
                title = "\u684c\u9762\u6a21\u5f0f",
                value = if (config.useDesktopMode) AppStrings.t("on") else AppStrings.t("off")
            ) {
                viewModel.setUseDesktopMode(!config.useDesktopMode)
            }

            SettingRow(
                icon = { Icon(Icons.Default.Folder, "", modifier = Modifier.size(20.dp)) },
                title = "\u4e0b\u8f7d\u76ee\u5f55",
                value = when (config.downloadDir) {
                    "pictures" -> "\u56fe\u7247"
                    "movies" -> "\u89c6\u9891"
                    "music" -> "\u97f3\u4e50"
                    else -> if (config.downloadDir.startsWith("content://")) "\u81ea\u5b9a\u4e49" else "\u9ed8\u8ba4"
                }
            ) {
                downloadDirDialog = true
            }

            // Homepage
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(AppStrings.t("homepage"))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, "", modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = homeInput,
                    onValueChange = { homeInput = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    AppStrings.t("save"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { if (homeInput.isNotEmpty()) viewModel.setHomePage(homeInput) }
                        .padding(8.dp)
                )
            }

            // About
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(AppStrings.t("about"))
            SettingRow(icon = {}, title = AppStrings.t("version"), value = viewModel.versionName) { startCheck() }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Search engine dialog
    if (engineDialog) {
        AlertDialog(
            onDismissRequest = { engineDialog = false },
            title = { Text(AppStrings.t("search_engine")) },
            text = {
                Column {
                    listOf("google" to "Google", "bing" to "Bing", "duckduckgo" to "DuckDuckGo", "baidu" to "Baidu").forEach { (code, name) ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSearchEngine(code)
                                    engineDialog = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Download dir dialog
    if (downloadDirDialog) {
        AlertDialog(
            onDismissRequest = { downloadDirDialog = false },
            title = { Text("\u4e0b\u8f7d\u76ee\u5f55") },
            text = {
                Column {
                    val dirs = listOf(
                        "default" to "\u9ed8\u8ba4",
                        "pictures" to "\u56fe\u7247",
                        "movies" to "\u89c6\u9891",
                        "music" to "\u97f3\u4e50"
                    )
                    dirs.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDownloadDir(code)
                                    downloadDirDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (config.downloadDir == code) {
                                Icon(Icons.Default.Check, "", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    // 自定义目录（SAF）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dirPicker.launch(null) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.downloadDir.startsWith("content://")) {
                            Icon(Icons.Default.Check, "", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("\u81ea\u5b9a\u4e49", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Language dialog
    if (langDialog) {
        AlertDialog(
            onDismissRequest = { langDialog = false },
            title = { Text(AppStrings.t("language")) },
            text = {
                Column {
                    listOf("en" to "English", "zh" to "中文").forEach { (code, name) ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(code)
                                    langDialog = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/** 手动检查更新的状态机（点“版本”行触发；不用 sealed：当前构建环境的编译器对 sealed 嵌套 object 的子类型判定有兼容问题） */
private interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class UpToDate(val latestVersion: String) : UpdateCheckState
    data class NewVersion(val info: UpdateChecker.UpdateInfo) : UpdateCheckState
    data class Downloading(val info: UpdateChecker.UpdateInfo, val progress: Int) : UpdateCheckState
    data class ReadyToInstall(val file: File, val version: String) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit = {},
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        if (icon != {}) Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}
