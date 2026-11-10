package com.qiyu.browser.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
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

    val container = FrameLayout(context)
    private val webViews = mutableMapOf<Int, WebView>()

    fun createWebView(tabId: Int, incognito: Boolean): WebView {
        val config = settingsRepo.config.value
        val webView = WebView(context).apply {
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            if (config.darkMode) {
                setBackgroundColor(0xFF1E1E1E.toInt())
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
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
                if (config.darkMode && android.os.Build.VERSION.SDK_INT >= 28) {
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
        return webView
    }

    fun getWebView(tabId: Int): WebView? = webViews[tabId]

    fun showTab(tabId: Int) {
        webViews.forEach { (id, view) ->
            view.visibility = if (id == tabId) View.VISIBLE else View.GONE
        }
    }

    fun destroyWebView(tabId: Int) {
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
