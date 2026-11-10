package com.qiyu.browser.model

data class BrowserConfig(
    val homePage: String = "",
    val searchEngine: String = "google",
    val fontSize: Int = 100,
    val darkMode: String = "auto",
    val dataSaver: Boolean = false,
    val autoPlayVideos: Boolean = false,
    val blockPopups: Boolean = true,
    val language: String = "en"
)
