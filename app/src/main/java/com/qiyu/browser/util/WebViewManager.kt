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
import kotlin.math.roundToInt

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

    // ---- 开发者面板：网络记录 / 控制台 / 远程调试 ----

    data class DevNetEntry(
        val time: String,
        val main: Boolean,
        val url: String,
        val status: String,
        val size: String
    )

    data class DevConsoleEntry(
        val level: String,
        val message: String,
        val source: String
    )

    private val netLog = ArrayDeque<DevNetEntry>()
    private val consoleLog = ArrayDeque<DevConsoleEntry>()
    var remoteDebugging = false
        private set

    private fun nowHms(): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    private fun fmtBytes(n: Long): String = when {
        n >= 1048576 -> ("%.1f MB").format(n / 1048576.0)
        n >= 1024 -> ("%.1f KB").format(n / 1024.0)
        else -> "$n B"
    }

    fun addDevNet(main: Boolean, url: String, status: String, sizeBytes: Long? = null) {
        val sizeStr = if (sizeBytes == null) "" else fmtBytes(sizeBytes)
        val entry = DevNetEntry(nowHms(), main, url, status, sizeStr)
        synchronized(netLog) {
            netLog.addLast(entry)
            while (netLog.size > 300) netLog.removeFirst()
        }
    }

    /** 子资源收到 HTTP 错误：把最近一条同 url 且未完成（status="–"）的记录改为错误码 */
    fun failDevNet(url: String, code: Int) {
        synchronized(netLog) {
            var i = netLog.size - 1
            while (i >= 0) {
                val e = netLog[i]
                if (e.url == url && e.status == "–") {
                    netLog[i] = e.copy(status = code.toString())
                    break
                }
                i--
            }
        }
    }

    fun addDevConsole(level: String, message: String, source: String) {
        val entry = DevConsoleEntry(level, message, source)
        synchronized(consoleLog) {
            consoleLog.addLast(entry)
            while (consoleLog.size > 300) consoleLog.removeFirst()
        }
    }

    fun devNetLog(): List<DevNetEntry> = synchronized(netLog) { netLog.toList() }
    fun devConsoleLog(): List<DevConsoleEntry> = synchronized(consoleLog) { consoleLog.toList() }
    fun clearDevLogs() {
        synchronized(netLog) { netLog.clear() }
        synchronized(consoleLog) { consoleLog.clear() }
    }

    /** 远程调试开关：开启后电脑 Chrome 打开 chrome://inspect 可连上本应用 WebView
     *  反射调用：当前编译环境解析不到该静态方法 */
    fun setRemoteDebugging(enabled: Boolean) {
        remoteDebugging = enabled
        try {
            WebSettings::class.java
                .getMethod("setWebContentsDebuggingEnabled", java.lang.Boolean::class.javaPrimitiveType)
                .invoke(null, enabled)
        } catch (e: Exception) {}
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
                setSupportZoom(true)
                builtInZoomControls = true
                useWideViewPort = true
                // 关闭 overview 模式：它会把页面整体拟合到屏幕宽度，视觉上覆盖/重置缩放；
                // 改成 Chrome 式“贴合宽度 + 缩放层”，菜单滑杆的 zoom 才可见
                loadWithOverviewMode = false
                // 显式把引擎缩放范围钉在 50~200（与菜单滑杆一致），中间档位不被引擎内部 clamp
                setMinimumScalePercentage(50)
                setMaximumScalePercentage(200)
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
            // 隐藏内建缩放胶囊（挡页面且丑），缩放统一走菜单「缩放」档位；双指缩放仍可用。
            // 反射调用：当前编译环境解析不到 setZoomControlsEnabled
            try {
                WebSettings::class.java
                    .getMethod("setZoomControlsEnabled", java.lang.Boolean::class.javaPrimitiveType)
                    .invoke(settings, false)
            } catch (e: Exception) {}
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

    /** 每个标签页上次设置的缩放档位（50%~200%） */
    private val zoomFactor = HashMap<Int, Float>()

    /** 应用缩放：WebSettings.setZoomScale（反射：环境对 WebView 级同名符号解析有缺陷）
     *  + JS style.zoom 双保险（引擎不支持时静默忽略）；onPageFinished 时重新应用 */
    private fun applyZoom(tabId: Int) {
        val factor = zoomFactor[tabId] ?: return
        val wv = webViews[tabId] ?: return
        try {
            android.webkit.WebSettings::class.java
                .getMethod("setZoomScale", java.lang.Float::class.javaPrimitiveType, java.lang.Boolean::class.javaPrimitiveType)
                .invoke(wv.settings, factor, false)
        } catch (e: Exception) {}
        try {
            wv.evaluateJavascript(
                "(function(){ try { var f = $factor; document.documentElement.style.zoom = f; if (document.body) document.body.style.zoom = f; } catch(e){} })()",
                null
            )
        } catch (e: Exception) {}
    }

    /** 页面缩放（菜单滑杆 / 双指缩放）：引擎按 ~10% 离散档位缩放，统一对齐 */
    fun setZoom(tabId: Int, scalePercent: Int) {
        val pct = ((scalePercent + 5) / 10 * 10).coerceIn(50, 200)
        val factor = (pct / 100).toFloat()
        zoomFactor[tabId] = factor
        applyZoom(tabId)
    }

    /** 当前缩放百分比（取整到 10 的倍数）：优先本地记录，回退反射读 WebSettings.getZoomScale */
    fun currentZoom(tabId: Int): Int {
        zoomFactor[tabId]?.let { f -> return ((f * 100).roundToInt() / 10) * 10 }
        return try {
            val wv = webViews[tabId] ?: return 100
            val m = android.webkit.WebSettings::class.java.getMethod("getZoomScale")
            val s = m.invoke(wv.settings) as Float
            ((s * 100).roundToInt() / 10) * 10
        } catch (e: Exception) {
            100
        }
    }

    /** 清除所有 WebView 的缓存（一键清理用，反射调 clearCache） */
    fun clearAllCache() {
        webViews.values.forEach { wv ->
            try {
                android.webkit.WebView::class.java.getMethod("clearCache").invoke(wv)
            } catch (e: Exception) {}
        }
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
                applyZoom(tabId)
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
                            // 请求头里只有 UA 重要（决定桌面/移动版渲染）；其余（Referer 等）对主文档无关紧要，
                            // 不遍历 Java Map（当前编译环境对其类型推断有缺陷）
                            val ua = webViews[tabId]?.settings?.userAgentString
                            if (ua != null) conn.setRequestProperty("User-Agent", ua)
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
                            addDevNet(true, url.toString(), conn.responseCode.toString(), bytes.size.toLong())
                            return android.webkit.WebResourceResponse(
                                conn.contentType ?: "text/html",
                                "UTF-8",
                                java.io.ByteArrayInputStream(bytes)
                            )
                        } catch (e: Exception) {
                            addDevNet(true, request.url?.toString() ?: "", "error")
                            return null
                        }
                    } else {
                        // file/about 等非 http 主文档
                        addDevNet(true, request.url?.toString() ?: "", "–")
                    }
                } else {
                    // 子资源：只记请求，后续错误码由 onReceivedHttpError 补上
                    addDevNet(false, request.url?.toString() ?: "", "–")
                }
                return null
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse) {
                request?.let { req ->
                    failDevNet(req.url?.toString() ?: "", errorResponse.statusCode)
                }
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
        return object : WebChromeClient() {
            @Deprecated("Deprecated in Java")
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                val lvl = consoleMessage.messageLevel()
                val level = when (lvl.ordinal) {
                    0 -> "debug"
                    1 -> "log"
                    2 -> "info"
                    3 -> "warn"
                    else -> "error"
                }
                addDevConsole(level, consoleMessage.message(), consoleMessage.sourceId() ?: "")
                return true
            }
        }
    }
}
