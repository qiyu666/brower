package com.qiyu.browser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qiyu.browser.model.Bookmark
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.HistoryItem

@Database(
    entities = [Bookmark::class, HistoryItem::class, DownloadItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nova_browser.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
