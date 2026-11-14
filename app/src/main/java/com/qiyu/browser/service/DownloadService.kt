package com.qiyu.browser.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qiyu.browser.MainActivity
import com.qiyu.browser.NovaBrowserApp
import com.qiyu.browser.R
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import com.qiyu.browser.util.WebUtils
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "nova_downloads"
        const val NOTIFICATION_ID_BASE = 10000

        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_REFERER = "referer"

        fun start(context: Context, url: String, fileName: String?, mimeType: String?, downloadId: Long, referer: String? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_REFERER, referer)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var activeDownloads = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
            val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                ?: WebUtils.getFileNameFromUrl(url)
            val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
            val referer = intent.getStringExtra(EXTRA_REFERER)

            activeDownloads++
            startForegroundAsDataSync()

            serviceScope.launch {
                downloadFile(url, fileName, mimeType, downloadId, referer)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAsDataSync() {
        val notification = buildNotification("Download in progress...", null, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_BASE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_BASE, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, progress: Int?, finished: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti_download)
            .setContentTitle(title)
            .setOngoing(!finished)
            .setShowWhen(false)

        if (progress != null) {
            builder.setProgress(100, progress, false)
            builder.setContentText("$progress%")
        }

        return builder.build()
    }

    private suspend fun downloadFile(
        url: String,
        fileName: String,
        mimeType: String?,
        downloadId: Long,
        referer: String?
    ) {
        val app = application as NovaBrowserApp
        val repo = app.downloadRepository
        val config = app.settingsRepository.config.value
        val base = repo.getById(downloadId) ?: DownloadItem(url = url, fileName = fileName, mimeType = mimeType)

        try {
            android.util.Log.d("QiyuDownload", "url=$url fileName=$fileName mimeType=$mimeType")

            // 手动跟随跳转（JVM 不自动跟随 http<->https 跨协议跳转）
            var currentUrl = url
            var redirects = 0
            var connection: HttpURLConnection? = null
            while (true) {
                val c = URL(currentUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 30_000
                c.readTimeout = 60_000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                c.setRequestProperty("Accept", "*/*")
                if (!referer.isNullOrEmpty()) c.setRequestProperty("Referer", referer)
                c.connect()
                val code = c.responseCode
                android.util.Log.d("QiyuDownload", "url=$currentUrl responseCode=$code")
                when {
                    code in 300..399 -> {
                        val location = c.getHeaderField("Location")
                        c.disconnect()
                        if (location == null) throw Exception("HTTP $code without Location")
                        if (++redirects > 5) throw Exception("Too many redirects")
                        currentUrl = resolveLocation(currentUrl, location)
                        continue
                    }
                    code in 200..299 -> {
                        connection = c
                        break
                    }
                    else -> {
                        c.disconnect()
                        throw Exception("HTTP $code: ${c.responseMessage}")
                    }
                }
            }
            val conn = connection!!

            // 优先使用服务器响应头提供的文件名
            var effectiveName = fileName
            conn.getHeaderField("Content-Disposition")?.let { cd ->
                val raw = when {
                    cd.contains("filename*=") -> cd.substringAfter("filename*=")
                    cd.contains("filename=") -> cd.substringAfter("filename=")
                    else -> ""
                }
                val serverName = raw.split(";")
                    .firstOrNull()
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.removePrefix("UTF-8''")
                if (!serverName.isNullOrEmpty() && !serverName.contains(" ")) {
                    effectiveName = serverName
                }
            }

            var totalSize = conn.contentLength.toLong()
            var downloaded = 0L
            val filePath: String

            if (config.downloadDir.startsWith("content://")) {
                // SAF 自定义目录
                val dirUri = Uri.parse(config.downloadDir)
                val mimeForDoc = mimeType ?: "application/octet-stream"
                val docUri = DocumentsContract.createDocument(
                    contentResolver, dirUri, mimeForDoc, effectiveName
                ) ?: throw Exception("Cannot create file in custom directory")
                filePath = docUri.toString()

                conn.inputStream.use { input ->
                    contentResolver.openOutputStream(docUri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                lastUpdate = now
                                withContext(Dispatchers.Main) {
                                    repo.update(base.copy(
                                        filePath = filePath,
                                        fileSize = if (totalSize > 0) totalSize else -1,
                                        downloadedSize = downloaded,
                                        status = DownloadStatus.IN_PROGRESS
                                    ))
                                }
                            }
                        }
                    }
                }
            } else {
                // 应用内部目录
                val baseDir = when (config.downloadDir) {
                    "pictures" -> getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    "movies" -> getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    "music" -> getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    else -> getExternalFilesDir(null)
                } ?: throw Exception("External storage not available")
                val downloadDir = File(baseDir, "downloads")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, effectiveName)
                filePath = file.absolutePath

                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                lastUpdate = now
                                withContext(Dispatchers.Main) {
                                    repo.update(base.copy(
                                        filePath = filePath,
                                        fileSize = if (totalSize > 0) totalSize else -1,
                                        downloadedSize = downloaded,
                                        status = DownloadStatus.IN_PROGRESS
                                    ))
                                }
                            }
                        }
                    }
                }
            }

            conn.disconnect()
            if (totalSize < 0) totalSize = downloaded

            // Mark as completed
            val svc = this
            withContext(Dispatchers.Main) {
                repo.update(base.copy(
                    fileName = effectiveName,
                    filePath = filePath,
                    fileSize = totalSize,
                    downloadedSize = totalSize,
                    status = DownloadStatus.COMPLETED
                ))

                // 完成通知：点击跳转应用内下载列表
                val openIntent = Intent(svc, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pending = PendingIntent.getActivity(
                    svc, 0, openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val doneNotification = NotificationCompat.Builder(svc, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_noti_download)
                    .setContentTitle("\u4e0b\u8f7d\u5b8c\u6210")
                    .setContentText(effectiveName)
                    .setContentIntent(pending)
                    .setOngoing(false)
                    .build()
                svc.notificationManager.notify(downloadId.toInt(), doneNotification)
                android.widget.Toast.makeText(svc, "下载完成：$effectiveName", android.widget.Toast.LENGTH_SHORT).show()

                activeDownloads--
                if (activeDownloads <= 0) {
                    stopSelf()
                    notificationManager.cancel(NOTIFICATION_ID_BASE)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("QiyuDownload", "download failed: url=$url", e)
            withContext(Dispatchers.Main) {
                repo.update(base.copy(
                    status = DownloadStatus.FAILED,
                    errorMsg = e.message ?: e.javaClass.simpleName
                ))
                activeDownloads--
                if (activeDownloads <= 0) {
                    stopSelf()
                    notificationManager.cancel(NOTIFICATION_ID_BASE)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolveLocation(current: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        val u = URL(current)
        val host = u.host + if (u.port != -1) ":${u.port}" else ""
        return if (location.startsWith("/")) {
            "${u.protocol}://$host$location"
        } else {
            val dir = u.path.removeSuffix(u.path.substringAfterLast('/'))
            "${u.protocol}://$host$dir$location"
        }
    }
}
