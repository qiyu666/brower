package com.qiyu.browser.data.repository

import com.qiyu.browser.data.local.HistoryDao
import com.qiyu.browser.model.HistoryItem
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    suspend fun add(item: HistoryItem) {
        val existing = dao.findByUrl(item.url)
        if (existing != null) {
            dao.update(existing.copy(visitCount = existing.visitCount + 1, visitTime = System.currentTimeMillis()))
        } else {
            dao.insert(item)
        }
    }

    suspend fun clearAll() = dao.clearAll()

    fun getAll(limit: Int = 200, offset: Int = 0): Flow<List<HistoryItem>> = dao.getAll(limit, offset)

    fun search(query: String): Flow<List<HistoryItem>> = dao.search(query)

    fun getRecent(): Flow<List<HistoryItem>> = dao.getRecent()

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
