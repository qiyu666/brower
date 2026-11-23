package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiyu.browser.util.AppStrings
import com.qiyu.browser.util.CredentialStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 保存的密码管理：
 * - 列表只显示用户名/站点/时间（数据模型本身不含密码）
 * - 「查看」时瞬态解密；隐藏/关闭弹窗立即把明文从 state 清掉
 * - 修改/删除都走 CredentialStore（Keystore AES-GCM 密文落盘）
 */
private const val MASK = "••••••••"

@Composable
fun PasswordsScreen(
    store: CredentialStore,
    onBack: () -> Unit
) {
    var rows by remember { mutableStateOf(store.allRows().sortedByDescending { it.lastUsed }) }
    var detail by remember { mutableStateOf<CredentialStore.CredRow?>(null) }
    var clearDialog by remember { mutableStateOf(false) }

    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

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
                    Icon(Icons.Default.ArrowBack, null)
                }
                Text(AppStrings.t("saved_passwords"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                if (rows.isNotEmpty()) {
                    TextButton(onClick = { clearDialog = true }) {
                        Text(AppStrings.t("clear_all"), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Key, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(
                    AppStrings.t("password_security_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (rows.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(AppStrings.t("no_passwords"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { detail = row }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Key, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(row.username.ifEmpty { AppStrings.t("username") },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.origin, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                AppStrings.t("last_used") + " " + df.format(Date(row.lastUsed)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    detail?.let { row ->
        PasswordDetailDialog(store, row) {
            detail = null
            rows = store.allRows().sortedByDescending { it.lastUsed }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(AppStrings.t("clear_all")) },
            text = { Text(AppStrings.t("clear_passwords_confirm")) },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAll()
                    rows = emptyList()
                    clearDialog = false
                }) { Text(AppStrings.t("delete"), color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text(AppStrings.t("cancel")) }
            }
        )
    }
}

@Composable
private fun PasswordDetailDialog(store: CredentialStore, row: CredentialStore.CredRow, onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(MASK) }
    var editingPwd by remember { mutableStateOf(false) }
    var newPwd by remember { mutableStateOf("") }
    var editingUser by remember { mutableStateOf(false) }
    var newUser by remember { mutableStateOf(row.username) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun closeAll() {
        if (shown) {
            revealed = MASK
            shown = false
        }
        onDone()
    }

    // 查看：瞬态解密；「查看/隐藏」切换时，隐藏立即把明文从 state 换掉
    LaunchedEffect(shown) {
        if (shown && revealed == MASK) {
            val chars = store.getPassword(row.id)
            if (chars != null) {
                revealed = String(chars)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { closeAll() },
        title = { Text(row.origin) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(AppStrings.t("last_used") + " " + df.format(Date(row.lastUsed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

                if (editingUser) {
                    OutlinedTextField(
                        value = newUser,
                        onValueChange = { newUser = it },
                        singleLine = true,
                        label = { Text(AppStrings.t("username")) }
                    )
                    TextButton(onClick = {
                        if (newUser.isNotBlank()) {
                            store.setUsername(row.id, newUser)
                            closeAll()
                        }
                    }) { Text(AppStrings.t("save"), color = MaterialTheme.colorScheme.primary) }
                } else {
                    Text(AppStrings.t("username") + "：" + row.username.ifEmpty { "—" })
                    Text(
                        revealed,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = {
                            if (shown) {
                                shown = false
                                revealed = MASK
                            } else {
                                shown = true
                            }
                        }) {
                            Text(if (shown) AppStrings.t("hide_password") else AppStrings.t("show_password"))
                        }
                        TextButton(onClick = { editingUser = true }) { Text(AppStrings.t("edit_username")) }
                    }
                }

                if (editingPwd) {
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        singleLine = true,
                        label = { Text(AppStrings.t("edit_password")) }
                    )
                    TextButton(onClick = {
                        if (newPwd.length >= 4) {
                            store.setPassword(row.id, newPwd)
                            newPwd = ""
                            shown = false
                            revealed = MASK
                            onDone()
                        }
                    }) { Text(AppStrings.t("save"), color = MaterialTheme.colorScheme.primary) }
                } else {
                    TextButton(onClick = { editingPwd = true }) { Text(AppStrings.t("edit_password")) }
                }

                if (!deleteConfirm) {
                    TextButton(onClick = { deleteConfirm = true }) {
                        Text(AppStrings.t("delete"), color = Color(0xFFF44336))
                    }
                } else {
                    Row {
                        Text(AppStrings.t("clear_passwords_confirm"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            store.delete(row.id)
                            closeAll()
                        }) { Text(AppStrings.t("delete"), color = Color(0xFFF44336)) }
                        TextButton(onClick = { deleteConfirm = false }) { Text(AppStrings.t("cancel")) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { closeAll() }) { Text(AppStrings.t("close")) }
        }
    )
}
