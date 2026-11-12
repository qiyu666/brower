package com.qiyu.browser.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.qiyu.browser.data.repository.SettingsRepository
import com.qiyu.browser.model.BrowserConfig

interface WebViewListener {
    fun onPageStarted(tabId: Int, url: String)
    fun onPageFinished(tabId: Int, title: String, url: String)
    fun onProgressChanged(tabId: Int, progress: Int)
    fun onNewPage(tabId: Int, url: String)
    fun onDownloadRequested(tabId: Int, url: String, fileName: String?, mimeType: String?)
}

@SuppressLint("SetJavaScriptEnabled")
class WebViewManager(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val listener: WebViewListener
) {

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    val container = FrameLayout(context)
    val refreshLayout = SwipeRefreshLayout(context).apply {
        addView(container)
        setProgressBackgroundColorSchemeColor(0xFF1E1E1E.toInt())
        setColorSchemeColors(0xFF2196F3.toInt())
        setOnRefreshListener {
            webViews.values.lastOrNull()?.let { wv ->
                wv.reload()
            }
            Handler(Looper.getMainLooper()).postDelayed({ isRefreshing = false }, 2000)
        }
    }
    private val webViews = mutableMapOf<Int, WebView>()
    private val incognitoTabs = mutableSetOf<Int>()
    private val incognitoDomains = mutableMapOf<Int, MutableSet<String>>()
    private val favicons = mutableMapOf<Int, android.graphics.Bitmap>()

    fun createWebView(tabId: Int, incognito: Boolean): WebView {
        val config = settingsRepo.config.value
        val webView = WebView(context).apply {
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            if (config.darkMode == "dark" || (config.darkMode == "auto" && android.os.Build.VERSION.SDK_INT >= 29)) {
                setBackgroundColor(0xFF1E1E1E.toInt())
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = !incognito
                databaseEnabled = !incognito
                setSupportZoom(false)
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = config.fontSize
                cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                @Suppress("DEPRECATION")
                mediaPlaybackRequiresUserGesture = !config.autoPlayVideos
                if (config.blockPopups) {
                    setSupportMultipleWindows(false)
                }
                if (config.useDesktopMode) {
                    userAgentString = DESKTOP_UA
                }
                if (config.darkMode == "dark" && android.os.Build.VERSION.SDK_INT >= 28) {
                    @Suppress("DEPRECATION")
                    forceDark = WebSettings.FORCE_DARK_ON
                } else if (config.darkMode == "auto" && android.os.Build.VERSION.SDK_INT >= 28) {
                    @Suppress("DEPRECATION")
                    forceDark = WebSettings.FORCE_DARK_ON
                }
            }
            webViewClient = createWebViewClient(tabId)
            webChromeClient = createWebChromeClient(tabId)
            setDownloadListener { url, _, _, mimeType, _ ->
                val fileName = WebUtils.getFileNameFromUrl(url)
                listener.onDownloadRequested(tabId, url, fileName, mimeType)
            }
            visibility = View.GONE
            loadUrl("about:blank")
        }
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webViews[tabId] = webView
        if (incognito) {
            incognitoTabs.add(tabId)
            incognitoDomains[tabId] = mutableSetOf()
        }
        return webView
    }

    fun getWebView(tabId: Int): WebView? = webViews[tabId]

    fun showTab(tabId: Int) {
        webViews.forEach { (id, view) ->
            view.visibility = if (id == tabId) View.VISIBLE else View.GONE
        }
    }

    fun getFavicon(tabId: Int): android.graphics.Bitmap? = favicons[tabId]

    fun destroyWebView(tabId: Int) {
        clearIncognitoCookies(tabId)
        incognitoTabs.remove(tabId)
        webViews.remove(tabId)?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
            container.removeView(it)
            it.destroy()
        }
    }

    fun destroyAll() {
        webViews.values.forEach {
            it.stopLoading()
            it.loadUrl("about:blank")
            container.removeView(it)
            it.destroy()
        }
        webViews.clear()
    }

    fun clearIncognitoCookies(tabId: Int) {
        if (tabId !in incognitoTabs) return
        val domains = incognitoDomains.remove(tabId) ?: return
        try {
            val cm = android.webkit.CookieManager.getInstance()
            domains.forEach { domain ->
                listOf("https://$domain", "http://$domain").forEach { url ->
                    val cookieStr = cm.getCookie(url)
                    if (!cookieStr.isNullOrEmpty()) {
                        cookieStr.split("; ")
                            .filter { it.isNotBlank() }
                            .forEach { c ->
                                val name = c.substringBefore("=")
                                if (name.isNotEmpty()) cm.setCookie(url, "$name=deleted; Max-Age=0")
                            }
                    }
                }
            }
            cm.flush()
        } catch (e: Exception) {}
    }

    fun getThumbnail(tabId: Int, maxWidth: Int = 400, maxHeight: Int = 300): Bitmap? {
        val webView = webViews[tabId] ?: return null
        return try {
            if (webView.width == 0 || webView.height == 0) return null
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            webView.draw(canvas)
            val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height, 1f)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } catch (e: Exception) {
            null
        }
    }

    fun pauseTab(tabId: Int) {
        webViews[tabId]?.let { wv ->
            wv.onPause()
            wv.pauseTimers()
        }
    }

    fun resumeTab(tabId: Int) {
        webViews[tabId]?.let { wv ->
            wv.onResume()
            wv.resumeTimers()
        }
    }

    fun applyDesktopMode(enabled: Boolean) {
        webViews.values.forEach { wv ->
            wv.settings.userAgentString = if (enabled) DESKTOP_UA else null
        }
    }

    fun applyDarkMode(dark: Boolean) {
        webViews.forEach { (tabId, webView) ->
            webView.setBackgroundColor(if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                @Suppress("DEPRECATION")
                webView.settings.forceDark = if (dark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
            webView.reload()
        }
    }

    fun refresh(tabId: Int) {
        webViews[tabId]?.reload()
    }

    fun goBack(tabId: Int): Boolean {
        val webView = webViews[tabId] ?: return false
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun goForward(tabId: Int): Boolean {
        val webView = webViews[tabId] ?: return false
        if (webView.canGoForward()) {
            webView.goForward()
            return true
        }
        return false
    }

    fun loadUrl(tabId: Int, url: String) {
        webViews[tabId]?.loadUrl(url)
    }

    private fun createWebViewClient(tabId: Int): WebViewClient {
        return object : WebViewClient() {
            @Deprecated("Deprecated in API 23")
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (favicon != null) favicons[tabId] = favicon
                incognitoDomains[tabId]?.let { set ->
                    val host = try { android.net.Uri.parse(url)?.host } catch (e: Exception) { null }
                    if (host != null) set.add(host)
                }
                listener.onPageStarted(tabId, url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                listener.onPageFinished(tabId, view?.title ?: "", url ?: "")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url != null) {
                    val scheme = request.url?.scheme ?: ""
                    return when (scheme) {
                        "http", "https" -> false
                        "mailto" -> {
                            try { context.startActivity(Intent(Intent.ACTION_SENDTO, request.url)) } catch (e: Exception) {}
                            true
                        }
                        "tel" -> {
                            try { context.startActivity(Intent(Intent.ACTION_DIAL, request.url)) } catch (e: Exception) {}
                            true
                        }
                        else -> {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) {}
                            true
                        }
                    }
                }
                return true
            }

            @Deprecated("Deprecated in API 29")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    val scheme = Uri.parse(url).scheme ?: ""
                    if (scheme == "http" || scheme == "https") return false
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
                    return true
                }
                return false
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
            }
        }
    }

    private fun createWebChromeClient(tabId: Int): WebChromeClient {
        return WebChromeClient()
    }
}
