package com.qiyu.browser.data.local

import androidx.room.*
import com.qiyu.browser.model.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem): Long

    @Update
    suspend fun update(item: HistoryItem)

    @Delete
    suspend fun delete(item: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT * FROM history WHERE isIncognito = 0 ORDER BY visitTime DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int = 200, offset: Int = 0): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE isIncognito = 0 AND (title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY visitTime DESC")
    fun search(query: String): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE url = :url AND isIncognito = 0 LIMIT 1")
    suspend fun findByUrl(url: String): HistoryItem?

    @Query("DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY visitTime ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("SELECT * FROM history WHERE isIncognito = 0 ORDER BY visitTime DESC LIMIT 10")
    fun getRecent(): Flow<List<HistoryItem>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
