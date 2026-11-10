package com.qiyu.browser

import android.app.Application
import com.qiyu.browser.data.local.AppDatabase
import com.qiyu.browser.data.repository.BookmarkRepository
import com.qiyu.browser.data.repository.DownloadRepository
import com.qiyu.browser.data.repository.HistoryRepository
import com.qiyu.browser.data.repository.SettingsRepository
import com.qiyu.browser.util.AppStrings

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
    }
}
