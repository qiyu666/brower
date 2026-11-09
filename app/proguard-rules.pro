# WebView
-keep class android.webkit.** { *; }

# Room
-keep class com.qiyu.browser.model.** { *; }
-keep class com.qiyu.browser.data.local.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
