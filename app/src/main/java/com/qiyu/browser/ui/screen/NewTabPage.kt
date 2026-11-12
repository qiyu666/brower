package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.qiyu.browser.R
import com.qiyu.browser.util.AppStrings

@Composable
fun NewTabPage(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchEngine: String = "google",
    recentItems: List<com.qiyu.browser.model.HistoryItem> = emptyList()
) {
    var query by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) Color.Black else Color.White
    val inputBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    val inputBorder = if (isDark) Color(0xFF5F5F5F) else Color(0xFFDFE1E5)
    val iconColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val textColor = if (isDark) Color(0xFFE8EAED) else Color(0xFF202124)

    fun buildSearch(q: String): String = when (searchEngine) {
        "google" -> "https://www.google.com/search?q=${q.replace(" ", "+")}"
        "bing" -> "https://www.bing.com/search?q=${q.replace(" ", "+")}"
        "duckduckgo" -> "https://duckduckgo.com/?q=${q.replace(" ", "+")}"
        "baidu" -> "https://www.baidu.com/s?wd=${q.replace(" ", "+")}"
        else -> "https://www.google.com/search?q=${q.replace(" ", "+")}"
    }

    fun doSearch() {
        if (query.isBlank()) return
        val url = if (com.qiyu.browser.util.WebUtils.isUrl(query))
            com.qiyu.browser.util.WebUtils.normalizeUrl(query)
        else
            buildSearch(query)
        onNavigate(url)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(48.dp))

            // Search box with centered text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(inputBg)
                    .border(
                        width = 1.dp,
                        color = inputBorder,
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { doSearch() },
                        onDone = { doSearch() },
                        onGo = { doSearch() },
                        onSend = { doSearch() }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (query.isEmpty()) {
                                Text(
                                    text = if (AppStrings.currentLanguage == "zh") "在此处输入关键词" else "Search here",
                                    color = iconColor.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            inner()
                        }
                    }
                )
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(20.dp)
                )
            }

            // 最近访问
            if (recentItems.isNotEmpty()) {
                Spacer(Modifier.height(36.dp))
                Text(
                    text = if (AppStrings.currentLanguage == "zh") "最近访问" else "Recently visited",
                    style = MaterialTheme.typography.labelLarge,
                    color = iconColor.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val half = recentItems.chunked(2)
                    half.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(inputBg)
                                        .border(1.dp, inputBorder, RoundedCornerShape(12.dp))
                                        .clickable { onNavigate(item.url) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Text(
                                            text = com.qiyu.browser.util.WebUtils.extractDomain(item.url),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = item.title.ifEmpty { "-" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = iconColor.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
