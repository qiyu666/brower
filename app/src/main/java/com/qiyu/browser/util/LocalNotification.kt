package com.qiyu.browser.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.qiyu.browser.MainActivity
import com.qiyu.browser.R

/** blob/data 本地下载路径的通知（HTTP 路径由 DownloadService 自管，id 段不冲突） */
object LocalNotification {

    const val CHANNEL_ID = "nova_downloads"
    private const val BASE_ID = 20000

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun postProgress(context: Context, downloadId: Long, fileName: String, downloaded: Long, total: Long) {
        try {
            ensureChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_noti_download)
                .setContentTitle("正在下载 $fileName")
                .setContentText("${downloaded} / ${total}")
                .setOngoing(true)
                .setShowWhen(false)
            if (total > 0) builder.setProgress(100, (downloaded * 100 / total).toInt(), false)
            context.getSystemService(NotificationManager::class.java)
                .notify(BASE_ID + (downloadId % 8000), builder.build())
        } catch (e: Exception) {
        }
    }

    fun postCompleted(context: Context, downloadId: Long, fileName: String) {
        try {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_noti_download)
                .setContentTitle("下载完成")
                .setContentText(fileName)
                .setContentIntent(pending)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(BASE_ID + 1 + (downloadId % 8000), n)
        } catch (e: Exception) {
        }
    }

    fun cancel(context: Context, downloadId: Long) {
        try {
            context.getSystemService(NotificationManager::class.java)
                .cancel(BASE_ID + (downloadId % 8000))
        } catch (e: Exception) {
        }
    }
}
