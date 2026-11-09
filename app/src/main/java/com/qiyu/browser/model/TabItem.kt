package com.qiyu.browser.model

data class TabItem(
    val id: Int,
    val title: String = "New Tab",
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val isIncognito: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
