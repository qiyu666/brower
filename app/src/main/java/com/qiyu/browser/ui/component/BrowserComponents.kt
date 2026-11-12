package com.qiyu.browser.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.input.ImeAction
import com.qiyu.browser.model.TabItem
import com.qiyu.browser.util.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBar(
    url: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    onUrlSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(if (url == "about:blank" || url.isEmpty()) "" else url) }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (!isEditing) inputText = if (url == "about:blank" || url.isEmpty()) "" else url
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back",
                modifier = Modifier.size(20.dp),
                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // URL input
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search, "",
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    isEditing = it != url
                },
                singleLine = true,
                placeholder = { Text(AppStrings.t("search_or_type_url")) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (inputText.isNotBlank()) onUrlSubmit(inputText) },
                    onDone = { if (inputText.isNotBlank()) onUrlSubmit(inputText) },
                    onGo = { if (inputText.isNotBlank()) onUrlSubmit(inputText) }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }

        // Action buttons
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                if (isBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                "Bookmark",
                modifier = Modifier.size(18.dp),
                tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TabStrip(
    tabs: List<TabItem>,
    activeTabId: Int?,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tabs, key = { it.id }) { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .widthIn(min = 80.dp, max = 120.dp)
                        .height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tab.title.isEmpty() || tab.title == "about:blank") "New Tab" else tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onTabClose(tab.id) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, "Close",
                                modifier = Modifier.size(12.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // New tab button
        IconButton(onClick = onNewTab, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Add, "New Tab",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BottomNav(
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    tabCount: Int,
    onTabSwitcher: () -> Unit,
    onMenu: () -> Unit,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back
        IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.ArrowBack, "Back",
                modifier = Modifier.size(24.dp),
                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // Forward
        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.ArrowForward, "Forward",
                modifier = Modifier.size(24.dp),
                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // New tab
        IconButton(onClick = onNewTab, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Add, "New Tab", modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        }

        // Tab count
        Surface(
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onTabSwitcher),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$tabCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Menu
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Menu, "Menu", modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
