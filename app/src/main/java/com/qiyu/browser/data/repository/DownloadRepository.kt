package com.qiyu.browser.data.repository

import com.qiyu.browser.data.local.DownloadDao
import com.qiyu.browser.model.DownloadItem
import com.qiyu.browser.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val dao: DownloadDao) {

    suspend fun add(item: DownloadItem): Long = dao.insert(item)

    suspend fun update(item: DownloadItem) = dao.update(item)

    suspend fun delete(item: DownloadItem) = dao.delete(item)

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteCompletedAndCancelled() = dao.deleteCompletedAndCancelled()

    fun getAll(): Flow<List<DownloadItem>> = dao.getAll()

    fun getByStatus(status: DownloadStatus): Flow<List<DownloadItem>> = dao.getByStatus(status)

    suspend fun getById(id: Long): DownloadItem? = dao.getById(id)
}
