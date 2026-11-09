package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qiyu.browser.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsStateWithLifecycle()

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
                    "Settings",
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
            // Appearance
            SectionTitle("Appearance")

            SettingRow(
                icon = { Icon(Icons.Default.DarkMode, "", modifier = Modifier.size(20.dp)) },
                title = "Dark Mode",
                value = if (config.darkMode) "On" else "Off"
            ) {
                viewModel.setDarkMode(!config.darkMode)
            }

            SettingRow(
                icon = { Icon(Icons.Default.TextFields, "", modifier = Modifier.size(20.dp)) },
                title = "Font Size",
                value = "${config.fontSize}%"
            ) {
                val sizes = listOf(75, 85, 100, 115, 125, 150)
                val currentIndex = sizes.indexOf(config.fontSize).coerceAtLeast(0)
                val newSize = sizes[(currentIndex + 1) % sizes.size]
                viewModel.setFontSize(newSize)
            }

            // Behavior
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Behavior")

            SettingRow(
                icon = { Icon(Icons.Default.Gesture, "", modifier = Modifier.size(20.dp)) },
                title = "Data Saver",
                value = if (config.dataSaver) "On" else "Off"
            ) {
                viewModel.setDataSaver(!config.dataSaver)
            }

            SettingRow(
                icon = { Icon(Icons.Default.PlayCircle, "", modifier = Modifier.size(20.dp)) },
                title = "Auto-play Videos",
                value = if (config.autoPlayVideos) "On" else "Off"
            ) {
                viewModel.setAutoPlayVideos(!config.autoPlayVideos)
            }

            SettingRow(
                icon = { Icon(Icons.Default.Shield, "", modifier = Modifier.size(20.dp)) },
                title = "Block Popups",
                value = if (config.blockPopups) "On" else "Off"
            ) {
                viewModel.setBlockPopups(!config.blockPopups)
            }

            // Home page
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Homepage")

            SettingsHomePageRow(
                currentUrl = config.homePage,
                onSave = { viewModel.setHomePage(it) }
            )

            // About
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("About")

            SettingRow(
                icon = {},
                title = "Version",
                value = "1.0.0"
            ) {}

            Spacer(modifier = Modifier.height(32.dp))
        }
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
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsHomePageRow(
    currentUrl: String,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentUrl) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Home, "", modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
        )
        Text(
            "Save",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable {
                    if (input.isNotEmpty()) onSave(input)
                }
                .padding(8.dp)
        )
    }
}
