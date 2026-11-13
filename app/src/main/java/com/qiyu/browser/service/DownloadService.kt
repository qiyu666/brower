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
import com.qiyu.browser.NovaBrowserApp
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

        fun start(context: Context, url: String, fileName: String?, mimeType: String?, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
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

            activeDownloads++
            startForegroundAsDataSync()

            serviceScope.launch {
                downloadFile(url, fileName, mimeType, downloadId)
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
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
        downloadId: Long
    ) {
        val app = application as NovaBrowserApp
        val repo = app.downloadRepository
        val config = app.settingsRepository.config.value

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()

            val totalSize = connection.contentLength.toLong()
            val filePath: String

            if (config.downloadDir.startsWith("content://")) {
                // SAF 自定义目录
                val dirUri = Uri.parse(config.downloadDir)
                val mimeForDoc = mimeType ?: "application/octet-stream"
                val docUri = DocumentsContract.createDocument(
                    contentResolver, dirUri, mimeForDoc, fileName
                ) ?: throw Exception("Cannot create file in custom directory")
                filePath = docUri.toString()

                connection.inputStream.use { input ->
                    contentResolver.openOutputStream(docUri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500 && totalSize > 0) {
                                lastUpdate = now
                                withContext(Dispatchers.Main) {
                                    repo.update(DownloadItem(
                                        id = downloadId, url = url, fileName = fileName,
                                        filePath = filePath, mimeType = mimeType,
                                        fileSize = totalSize, downloadedSize = downloaded,
                                        status = DownloadStatus.IN_PROGRESS,
                                        createdAt = System.currentTimeMillis()
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
                val file = File(downloadDir, fileName)
                filePath = file.absolutePath

                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500 && totalSize > 0) {
                                lastUpdate = now
                                withContext(Dispatchers.Main) {
                                    repo.update(DownloadItem(
                                        id = downloadId, url = url, fileName = fileName,
                                        filePath = filePath, mimeType = mimeType,
                                        fileSize = totalSize, downloadedSize = downloaded,
                                        status = DownloadStatus.IN_PROGRESS,
                                        createdAt = System.currentTimeMillis()
                                    ))
                                }
                            }
                        }
                    }
                }
            }

            connection.disconnect()

            // Mark as completed
            withContext(Dispatchers.Main) {
                repo.update(
                    DownloadItem(
                        id = downloadId,
                        url = url,
                        fileName = fileName,
                        filePath = filePath,
                        mimeType = mimeType,
                        fileSize = totalSize,
                        downloadedSize = totalSize,
                        status = DownloadStatus.COMPLETED,
                        createdAt = System.currentTimeMillis()
                    )
                )

                // 完成通知
                val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    putExtra(android.content.Intent.EXTRA_TEXT, filePath)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pending = androidx.core.app.ActivityOptionsCompat.makeBasic()
                    .toPendingIntent(this, 0, null)
                val doneNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("\u4e0b\u8f7d\u5b8c\u6210")
                    .setContentText(fileName)
                    .setContentIntent(openIntent)
                    .setOngoing(false)
                    .build()
                notificationManager.notify(downloadId.toInt(), doneNotification)

                activeDownloads--
                if (activeDownloads <= 0) {
                    stopSelf()
                    notificationManager.cancel(NOTIFICATION_ID_BASE)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                repo.update(
                    DownloadItem(
                        id = downloadId,
                        url = url,
                        fileName = fileName,
                        mimeType = mimeType,
                        status = DownloadStatus.FAILED,
                        createdAt = System.currentTimeMillis()
                    )
                )
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
}
