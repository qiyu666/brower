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
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
            SettingRow(icon = {}, title = AppStrings.t("version"), value = "1.0.0") {}
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
