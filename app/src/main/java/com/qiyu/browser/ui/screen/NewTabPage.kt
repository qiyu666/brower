package com.qiyu.browser.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import com.qiyu.browser.util.AppStrings

@Composable
fun NewTabPage(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchEngine: String = "google"
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) Color.Black else Color.White
    val inputBg = if (isDark) Color.Black else Color.White
    val inputBorder = if (isDark) Color(0xFF5F5F5F) else Color(0xFFDFE1E5)
    val inputBorderFocus = Color(0xFF8AB4F8)
    val iconColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val textColor = if (isDark) Color(0xFFE8EAED) else Color(0xFF202124)

    fun buildSearch(q: String): String = when (searchEngine) {
        "google" -> "https://www.google.com/search?q=${q.replace(" ", "+")}"
        "bing" -> "https://www.bing.com/search?q=${q.replace(" ", "+")}"
        "duckduckgo" -> "https://duckduckgo.com/?q=${q.replace(" ", "+")}"
        "baidu" -> "https://www.baidu.com/s?wd=${q.replace(" ", "+")}"
        else -> "https://www.google.com/search?q=${q.replace(" ", "+")}"
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
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Logo
            AsyncImage(
                model = "https://www.gstatic.com/android/external_chrome/dynamic_color/img/logo_g_color_2x.png",
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Fit
            )

            // Search box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(inputBg)
                    .border(
                        width = 1.dp,
                        color = if (focused) inputBorderFocus else inputBorder,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { focused = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = if (AppStrings.currentLanguage == "zh") "在此处输入关键词" else "Search here",
                            color = iconColor.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 20.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        if (query.isNotBlank()) {
                            val url = if (query.startsWith("http://") || query.startsWith("https://"))
                                query
                            else
                                buildSearch(query)
                            onNavigate(url)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedLabelColor = Color.Transparent,
                        unfocusedLabelColor = Color.Transparent,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                )
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp)
                )
            }
        }
    }
}
