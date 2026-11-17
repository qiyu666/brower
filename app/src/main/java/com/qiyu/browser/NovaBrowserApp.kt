package com.qiyu.browser

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import com.qiyu.browser.data.local.AppDatabase
import com.qiyu.browser.data.repository.BookmarkRepository
import com.qiyu.browser.data.repository.DownloadRepository
import com.qiyu.browser.data.repository.HistoryRepository
import com.qiyu.browser.data.repository.SettingsRepository
import com.qiyu.browser.util.AppStrings
import java.io.File

class NovaBrowserApp : Application() {

    lateinit var database: AppDatabase
    lateinit var bookmarkRepository: BookmarkRepository
    lateinit var historyRepository: HistoryRepository
    lateinit var downloadRepository: DownloadRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        bookmarkRepository = BookmarkRepository(database.bookmarkDao())
        historyRepository = HistoryRepository(database.historyDao())
        downloadRepository = DownloadRepository(database.downloadDao())
        settingsRepository = SettingsRepository(this)
        AppStrings.setLanguage(settingsRepository.config.value.language)
        installCrashHandler()
    }

    /**
     * 崩溃兜底：把完整堆栈通过启动 Intent 带到新进程（MainActivity 会复制到剪贴板），
     * 同时落盘 crash.log；然后交还系统处理让进程结束。
     */
    private fun installCrashHandler() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder("=== 崩溃日志 ===\n")
                sb.append("异常：").append(e).append('\n')
                sb.append(e.stackTraceToString()).append('\n')
                sb.append("\n设备：").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append('\n')
                sb.append("Android：API ").append(Build.VERSION.SDK_INT)
                    .append(' ').append(Build.VERSION.RELEASE).append('\n')
                sb.append("应用：Qiyu Browser 1.5 (versionCode 6)\n")
                val log = sb.toString()
                try {
                    java.io.FileOutputStream(File(getExternalFilesDir(null), "crash.log"))
                        .use { it.write(log.toByteArray()) }
                } catch (ignored: Exception) {
                }
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("crash_log", log)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION)
                })
            } catch (ignored: Exception) {
            }
            if (old != null) old.uncaughtException(t, e) else Process.killProcess(Process.myPid())
        }
    }
}
