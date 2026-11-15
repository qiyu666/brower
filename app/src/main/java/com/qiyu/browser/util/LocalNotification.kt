package com.qiyu.browser.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.qiyu.browser.MainActivity
import com.qiyu.browser.R

/**
 * blob/data 本地下载路径的通知（HTTP 路径由 DownloadService 自管，id 段不冲突）。
 * 一个下载对应**同一条**通知（id = BASE_ID + downloadId%8000），随生命周期原地更新：
 * 开始（转圈）→ 进度（百分比）→ 完成。
 */
object LocalNotification {

    const val CHANNEL_ID = "nova_downloads"
    private const val BASE_ID = 20000

    private fun notifyId(downloadId: Long): Int = (BASE_ID + (downloadId % 8000)).toInt()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun nm(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** 下载一开始就发：持续 + 转圈，让用户立刻看到「开始下载」 */
    fun postStarted(context: Context, downloadId: Long, fileName: String) {
        try {
            ensureChannel(context)
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_noti_download)
                .setContentTitle("正在下载 $fileName")
                .setContentText("开始下载…")
                .setOngoing(true)
                .setShowWhen(false)
                .setProgress(0, 0, true)
                .build()
            nm(context).notify(notifyId(downloadId), n)
        } catch (e: Exception) {
        }
    }

    /** 进度更新：同一条通知，换成可定进度条 */
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
            nm(context).notify(notifyId(downloadId), builder.build())
        } catch (e: Exception) {
        }
    }

    /** 完成：同一条通知原地变成「下载完成」，不再持续 */
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
            nm(context).notify(notifyId(downloadId), n)
        } catch (e: Exception) {
        }
    }

    /** 失败时撤掉该下载的通知 */
    fun cancel(context: Context, downloadId: Long) {
        try {
            nm(context).cancel(notifyId(downloadId))
        } catch (e: Exception) {
        }
    }
}
