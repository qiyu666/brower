package com.qiyu.browser.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.ValueCallback
import android.webkit.WebView
import com.qiyu.browser.data.repository.DownloadRepository
import com.qiyu.browser.model.BrowserConfig
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Base64
import kotlin.math.min

/**
 * 处理 blob: / data: 这类非 HTTP 下载源。
 * blob: 的数据在页面 JS 内存里，用页面内 fetch + 分块 base64 抽取；
 * data: 直接解码 URI 载荷。
 */
object LocalDownload {

    private const val MAX_SIZE = 100L * 1024 * 1024
    private const val CHUNK = 1024 * 1024

    suspend fun fromBlob(
        context: Context,
        webView: WebView,
        repo: DownloadRepository,
        config: BrowserConfig,
        base: DownloadItem,
        url: String,
        fileName: String
    ) {
        val key = "__dl${base.id}"
        try {
            val jsUrl = url.replace("\\", "\\\\").replace("'", "\\'")
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "fetch('$jsUrl').then(function(r){return r.blob();})" +
                        ".then(function(b){window['$key.mime']=b.type||'';return b.arrayBuffer();})" +
                        ".then(function(ab){window['$key.buf']=ab;window['$key.total']=ab.byteLength;window['$key.ready']=true;})" +
                        ".catch(function(e){window['$key.err']=String(e);window['$key.ready']=true;});'ok'",
                    null
                )
            }

            var total = 0L
            val deadline = System.currentTimeMillis() + 20_000
            while (true) {
                delay(250)
                val r = jsEval(
                    webView,
                    "window['$key.ready'] ? (window['$key.err'] ? 'ERR:' + window['$key.err'] : String(window['$key.total'] || -1)) : 'PENDING'"
                )
                when {
                    r == "PENDING" -> {
                        if (System.currentTimeMillis() > deadline) {
                            throw Exception("blob URL 不可解析（页面可能已跳转）")
                        }
                    }
                    r.startsWith("ERR:") -> throw Exception("blob 读取失败: ${r.substring(4)}")
                    else -> {
                        total = r.toLongOrNull() ?: -1
                        if (total <= 0) throw Exception("blob URL 没有数据")
                        break
                    }
                }
            }
            if (total > MAX_SIZE) {
                throw Exception("blob 文件过大（${total / 1024 / 1024}MB，上限 ${MAX_SIZE / 1024 / 1024}MB）")
            }

            // 真实文件名：优先取点击时记录的锚点 download 名，其次 DOM 锚点，最后按 MIME 生成
            val mime = jsEval(webView, "window['$key.mime']||''").ifEmpty { null }
            val anchorName = jsEval(webView, nameLookupScript(url))
            val saveName = when {
                anchorName.isNotEmpty() -> anchorName
                mime != null -> defaultName(mime)
                else -> fileName
            }

            val target = openTarget(context, config, saveName, mime)
            var off = 0L
            var lastUpdate = 0L
            target.second.use { os ->
                while (off < total) {
                    val len = min(CHUNK, (total - off).toInt())
                    val b64 = jsEval(webView, chunkScript(key, off, len))
                    val bytes = Base64.getDecoder().decode(b64)
                    os.write(bytes)
                    off += bytes.size
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        lastUpdate = now
                        repo.update(base.copy(
                            filePath = target.first,
                            fileSize = total,
                            downloadedSize = off,
                            mimeType = mime,
                            status = DownloadStatus.IN_PROGRESS
                        ))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "window['$key.buf']=null;window['$key.total']=null;window['$key.err']=null;window['$key.mime']=null;window['$key.ready']=null;'ok'",
                    null
                )
            }
            repo.update(base.copy(
                fileName = saveName,
                mimeType = mime,
                filePath = target.first,
                fileSize = total,
                downloadedSize = total,
                status = DownloadStatus.COMPLETED
            ))
        } catch (e: Exception) {
            android.util.Log.e("QiyuDownload", "blob download failed: $url", e)
            repo.update(base.copy(status = DownloadStatus.FAILED, errorMsg = e.message ?: e.javaClass.simpleName))
        }
    }

    private fun chunkScript(key: String, off: Long, len: Int): String =
        "(function(){var u8=new Uint8Array(window['$key.buf'],$off,$len);var s='';" +
            "for(var i=0;i<u8.length;i+=32768){s+=String.fromCharCode.apply(null,u8.subarray(i,Math.min(i+32768,u8.length)));}" +
            "return btoa(s);})()"

    /** 真实文件名：优先用点击时记录的快照（临时锚点已移除时 DOM 查不到），其次 DOM 锚点，取不到则交给 MIME 兜底 */
    private fun nameLookupScript(url: String): String {
        val safe = url.replace("\\", "\\\\").replace("\"", "\\\"")
        return "(function(){var t=\"$safe\";var n='';" +
            "if(window.__dlNames){n=window.__dlNames[t]||'';}" +
            "if(!n){var ls=document.links;for(var i=0;i<ls.length;i++){if(ls[i].href===t&&ls[i].download){n=ls[i].download;break;}}}" +
            "return n;})()"
    }

    suspend fun fromDataUri(
        context: Context,
        repo: DownloadRepository,
        config: BrowserConfig,
        base: DownloadItem,
        url: String,
        fileName: String?,
        mimeType: String?
    ) {
        try {
            val comma = url.indexOf(',')
            if (comma < 6) throw Exception("Invalid data URI")
            val head = url.substring(5, comma)
            val payload = url.substring(comma + 1)
            if (payload.length > MAX_SIZE * 4 / 3 + 1024) throw Exception("data URI 过大（上限 ${MAX_SIZE / 1024 / 1024}MB）")
            val isB64 = head.contains(";base64", ignoreCase = true)
            val mime = head.substringBefore(';').ifEmpty { "application/octet-stream" }
            val bytes = if (isB64) {
                Base64.getDecoder().decode(payload.replace("\n", "").replace("\r", ""))
            } else {
                Uri.decode(payload).toByteArray()
            }
            val name = fileName?.takeIf { it.isNotBlank() && it != "download" } ?: defaultName(mime)
            val target = openTarget(context, config, name, mime)
            target.second.use { it.write(bytes) }
            repo.update(base.copy(
                fileName = name,
                mimeType = mime,
                filePath = target.first,
                fileSize = bytes.size.toLong(),
                downloadedSize = bytes.size.toLong(),
                status = DownloadStatus.COMPLETED
            ))
        } catch (e: Exception) {
            android.util.Log.e("QiyuDownload", "data URI download failed: $url", e)
            repo.update(base.copy(status = DownloadStatus.FAILED, errorMsg = e.message ?: e.javaClass.simpleName))
        }
    }

    private fun defaultName(mime: String): String {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/wav" -> "wav"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-m4v" -> "m4v"
            "application/json" -> "json"
            "text/csv" -> "csv"
            "text/plain" -> "txt"
            "application/zip" -> "zip"
            else -> null
        }
        return if (ext != null) "data-${System.currentTimeMillis()}.$ext" else "download"
    }

    private suspend fun openTarget(context: Context, config: BrowserConfig, fileName: String, mime: String?): Pair<String, OutputStream> =
        withContext(Dispatchers.Main) {
            if (config.downloadDir.startsWith("content://")) {
                val dirUri = Uri.parse(config.downloadDir)
                val doc = DocumentsContract.createDocument(
                    context.contentResolver, dirUri, mime ?: "application/octet-stream", fileName
                ) ?: throw Exception("Cannot create file in custom directory")
                val out = context.contentResolver.openOutputStream(doc) ?: throw Exception("Cannot open output stream")
                Pair(doc.toString(), out)
            } else {
                val baseDir = when (config.downloadDir) {
                    "pictures" -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    "movies" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    "music" -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    else -> context.getExternalFilesDir(null)
                } ?: throw Exception("External storage not available")
                val dir = File(baseDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                Pair(file.absolutePath, FileOutputStream(file))
            }
        }

    private suspend fun jsEval(view: WebView, script: String): String {
        val deferred = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            view.evaluateJavascript(script, ValueCallback { raw ->
                deferred.complete(when (raw) {
                    null, "null" -> ""
                    else -> raw.trim('"')
                })
            })
        }
        return withTimeout(30_000) { deferred.await() }
    }
}
