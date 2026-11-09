package com.qiyu.browser.util

import android.net.Uri

object WebUtils {

    fun isUrl(input: String): Boolean {
        val trimmed = input.trim()
        return try {
            val uri = Uri.parse(trimmed)
            (uri.scheme == "http" || uri.scheme == "https") &&
                !uri.host.isNullOrEmpty()
        } catch (e: Exception) {
            trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (isUrl(trimmed)) return trimmed
        return "https://$trimmed"
    }

    fun buildSearchUrl(query: String, engine: String = "google"): String {
        val encoded = Uri.encode(query)
        return when (engine) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "baidu" -> "https://www.baidu.com/s?wd=$encoded"
            "yahoo" -> "https://search.yahoo.com/search?p=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun resolveUrlOrSearch(input: String, engine: String = "google"): String {
        return if (isUrl(input)) normalizeUrl(input) else buildSearchUrl(input, engine)
    }

    fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    fun getFileNameFromUrl(url: String): String {
        return try {
            val path = Uri.parse(url).path
            path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
                ?: "download"
        } catch (e: Exception) {
            "download"
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
            diff < 7 * 86_400_000 -> "${diff / 86_400_000} days ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}
