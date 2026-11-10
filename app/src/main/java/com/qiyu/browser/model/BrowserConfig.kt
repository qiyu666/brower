package com.qiyu.browser.model

data class BrowserConfig(
    val homePage: String = "https://www.google.com",
    val searchEngine: String = "google",
    val fontSize: Int = 100,
    val darkMode: Boolean = false,
    val dataSaver: Boolean = false,
    val autoPlayVideos: Boolean = false,
    val blockPopups: Boolean = true,
    val language: String = "en"
)
