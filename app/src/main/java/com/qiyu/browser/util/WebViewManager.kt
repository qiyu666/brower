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

        /**
         * 页面下载触发时记录「blob/data URL → 锚点 download 属性名」快照。
         * 三层捕获：
         * 1) document 捕获阶段点击（真人点 <a download>）；
         * 2) 重写 HTMLAnchorElement.prototype.click（程序化 a.click()，含未挂 DOM 的临时锚点，
         *    这种点击 document 监听器在部分时序下收不到，原型重写最可靠）；
         * 这样下载触发后查 DOM 已找不到锚点也能拿到真名。
         */
        private val DOWNLOAD_NAME_HOOK_JS = """
            (function(){
              if (window.__dlNamesInstalled) return;
              window.__dlNamesInstalled = true;
              window.__dlNames = window.__dlNames || {};
              window.__dlBlobCache = window.__dlBlobCache || {};
              function record(a){
                try {
                  if (!a) return;
                  var href = a.href || a.getAttribute('href') || '';
                  var name = a.download || a.getAttribute('download') || '';
                  if (href && name) window.__dlNames[href] = name;
                  // 预缓存 blob 数据：站点常在点击后 ~100ms 就 revokeObjectURL，
                  // 之后 Android 侧再 fetch 该 URL 会失败。这里在点击瞬间先 fetch 存成 Promise，
                  // revoke 只令 URL 字符串失效，不影响已持有的 Blob。
                  if (href && href.indexOf('blob:') === 0 && !window.__dlBlobCache[href]) {
                    window.__dlBlobCache[href] = fetch(href).then(function(r){ return r.blob(); });
                  }
                } catch(e){}
              }
              document.addEventListener('click', function(ev){
                try {
                  var t = ev.target;
                  while (t && t.nodeType !== 1) t = t.parentNode;
                  if (!t) return;
                  if (t.closest) record(t.closest('a[download]'));
                  else if (t.tagName === 'A' && t.hasAttribute('download')) record(t);
                } catch(e){}
              }, true);
              try {
                var origClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function(){
                  record(this);
                  return origClick.apply(this, arguments);
                };
              } catch(e){}
            })();
            """.trimIndent()
    }

    val container = FrameLayout(context)
    val refreshLayout = object : SwipeRefreshLayout(context) {
        override fun canChildScrollUp(): Boolean {
            // WebView 滚到顶部（scrollY==0）才允许下拉刷新
            webViews.values.lastOrNull()?.let { wv ->
                return wv.scrollY > 0
            }
            return false
        }
    }.apply {
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

    // 流量统计：主文档走 HttpURLConnection 拦截计数（转发 Cookie 保持会话），
    // lastPageBytes=当前页已加载字节，sessionBytes=历史页累计
    private val lastPageBytes = mutableMapOf<Int, Long>()
    private var sessionBytes: Long = 0

    /** 当前页已加载字节数（主文档） */
    fun currentPageBytes(tabId: Int): Long = lastPageBytes[tabId] ?: 0L

    /** 会话内累计流量（含当前未加载完的页） */
    fun sessionBytesTotal(): Long = sessionBytes + lastPageBytes.values.sum()

    /** 把上一页的字节计入会话累计 */
    private fun settlePage(tabId: Int) {
        sessionBytes += lastPageBytes[tabId] ?: 0L
        lastPageBytes[tabId] = 0L
    }

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
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                val cdName = contentDisposition?.let { cd ->
                    val raw = when {
                        cd.contains("filename*=") -> cd.substringAfter("filename*=")
                        cd.contains("filename=") -> cd.substringAfter("filename=")
                        else -> ""
                    }
                    raw.split(";").firstOrNull()
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.removePrefix("UTF-8''")
                        ?.takeIf { name -> name.isNotEmpty() && !name.contains(" ") }
                }
                val fileName = when {
                    cdName != null -> cdName
                    url.startsWith("blob:") || url.startsWith("data:") -> null
                    else -> WebUtils.getFileNameFromUrl(url)
                }
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
                settlePage(tabId)
                if (favicon != null) favicons[tabId] = favicon
                incognitoDomains[tabId]?.let { set ->
                    val host = try { android.net.Uri.parse(url)?.host } catch (e: Exception) { null }
                    if (host != null) set.add(host)
                }
                listener.onPageStarted(tabId, url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(DOWNLOAD_NAME_HOOK_JS, null)
                listener.onPageFinished(tabId, view?.title ?: "", url ?: "")
            }

            /** 主文档走 HttpURLConnection 加载：精确计数字节；失败时返回 null 让 WebView 正常加载。
             *  跳过缓存命中（CACHE_ONLY）；转发请求/响应 Cookie 保持登录会话。 */
            /** 主文档走 HttpURLConnection 加载：精确计数字节；失败时返回 null 让 WebView 正常加载。
             *  转发请求头与 Cookie（WebView 与 HttpURLConnection 不共享 cookie 库）保持登录会话。 */
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                if (request.isForMainFrame) {
                    val scheme = request.url?.scheme
                    if (scheme == "http" || scheme == "https") {
                        try {
                            val url = java.net.URL(request.url.toString())
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.instanceFollowRedirects = true
                            conn.connectTimeout = 15_000
                            val reqHeaders = request.requestHeaders
                            if (reqHeaders != null) {
                                for (key in reqHeaders.keys) {
                                    val values = reqHeaders[key]
                                    if (values != null) {
                                        for (value in values) {
                                            conn.setRequestProperty(key, value)
                                        }
                                    }
                                }
                            }
                            val cm = android.webkit.CookieManager.getInstance()
                            cm.getCookie(url.toString())?.let { conn.setRequestProperty("Cookie", it) }
                            conn.connect()
                            conn.headerFields.entries.forEach { entry ->
                                val key = entry.key ?: return@forEach
                                val vals = entry.value
                                if (vals.isNotEmpty()) {
                                    val setCookie = key.lowercase()
                                    if (setCookie == "set-cookie" || setCookie == "set-cookie2") {
                                        vals.forEach { sc -> cm.setCookie(url.toString(), sc) }
                                    }
                                }
                            }
                            cm.flush()
                            val bytes = conn.inputStream.readBytes()
                            lastPageBytes[tabId] = bytes.size.toLong()
                            return android.webkit.WebResourceResponse(
                                conn.contentType ?: "text/html",
                                "UTF-8",
                                java.io.ByteArrayInputStream(bytes)
                            )
                        } catch (e: Exception) {
                            return null
                        }
                    }
                }
                return null
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
