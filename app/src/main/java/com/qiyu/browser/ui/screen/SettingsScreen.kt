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

    var installPermPending by remember { mutableStateOf(false) }

    /** 查安装权限：未声明 REQUEST_INSTALL_PACKAGES 时系统会抛 SecurityException，必须包一层 */
    fun canInstallPackages(): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (e: Exception) {
        false
    }

    fun installUpdate(file: File, version: String) {
        // 先无条件发一次原生安装请求：国内 ROM（MIUI/EMUI 等）往往只有发起过一次安装后，
        // 本应用才会出现在「安装未知应用」列表里；未拦截的机型则直接完成安装。
        var launchOk = false
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            launchOk = true
        } catch (e: Exception) {
            Toast.makeText(context, "发起安装失败（${e.message}），可改用“用文件管理器安装”", Toast.LENGTH_LONG).show()
        }
        // Android 9+ 未授予安装权限 → 弹「需要安装权限」引导框（保持对话框，保留文件管理器兑底入口）
        if (android.os.Build.VERSION.SDK_INT >= 29 && !canInstallPackages()) {
            // 小米/红米 ROM：安装请求被系统路由到应用包安装器（com.miui.packageinstaller），
            // 用户在系统安装界面点「继续」即可完成安装，无需去设置授予「安装未知应用」权限
            val xiaomiRom = android.os.Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco", "mi")
            if (launchOk && xiaomiRom) {
                Toast.makeText(context, "安装请求已交给系统安装器，请在系统安装界面点“继续”", Toast.LENGTH_LONG).show()
            } else {
                installPermPending = true
            }
        } else if (launchOk) {
            updateState = UpdateCheckState.Idle
        }
    }

    /** 国内 ROM 兜底：APK 存到公共 Download 目录，用系统文件管理器打开安装 */
    fun openInFileManager(st: UpdateCheckState.ReadyToInstall) {
        val name = "qiyu-browser-${st.version}.apk"
        val uri = UpdateChecker.saveToPublicDownloads(context, st.file, name)
        if (uri != null) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                Toast.makeText(context, "APK 已保存到 Download 目录，请在文件管理器中安装", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "已保存到 Download 目录（$name），请手动打开文件管理器安装", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "无法保存到 Download 目录，请在系统设置允许“安装未知应用”后重试安装", Toast.LENGTH_LONG).show()
        }
        updateState = UpdateCheckState.Idle
    }

    // 从「安装未知应用」设置页返回：已授权则直接安装；未授权不再自动重试（降低被 ROM 拦截时的崩溃面），提示用户
    val installPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        try {
            if (updateState is UpdateCheckState.ReadyToInstall) {
                val st = updateState as UpdateCheckState.ReadyToInstall
                if (canInstallPackages()) {
                    installUpdate(st.file, st.version)
                } else {
                    Toast.makeText(context, "未检测到安装权限，可关闭对话框改用“用文件管理器安装”", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            // 任何异常都不崩：用户仍可手动重试或走文件管理器兑底
        }
    }

    // 申请「安装未知应用」权限引导（设置页跳出去授权，返回后自动重试安装）
    if (installPermPending && updateState is UpdateCheckState.ReadyToInstall) {
        AlertDialog(
            onDismissRequest = { installPermPending = false },
            title = { Text("需要安装权限") },
            text = {
                Text("系统要求先允许本应用“安装未知应用”。如设置页里找不到开关或被拦截，可直接点“稍后”，在“下载完成”对话框里改用“用文件管理器安装”。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    try {
                        installPermLauncher.launch(settingsIntent)
                    } catch (e: Exception) {
                        // 部分 ROM 上 startActivityForResult 会异常 → 退化为普通 startActivity
                        try {
                            context.startActivity(settingsIntent)
                        } catch (e2: Exception) {
                            installPermPending = false
                            Toast.makeText(context, "无法打开安装权限设置页，请手动安装 Download 目录里的 APK", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { installPermPending = false }) { Text("稍后") }
            }
        )
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
            title = { Text("v${st.version} 下载完成") },
            text = {
                Column {
                    Text("现在安装？")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { openInFileManager(st) }) { Text("用文件管理器安装") }
                    Text(
                        "若安装被系统拦截：APK 会同时存到 Download 目录，可用文件管理器/快速访问安装",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { installUpdate(st.file, st.version) }) { Text("立即安装") }
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
                    "auto" -> AppStrings.t("dark_auto")
                    "dark" -> AppStrings.t("dark_dark")
                    else -> AppStrings.t("dark_light")
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
