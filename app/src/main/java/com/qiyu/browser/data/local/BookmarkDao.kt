package com.qiyu.browser.data.local

import androidx.room.*
import com.qiyu.browser.model.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Insert
    suspend fun insertAll(bookmarks: List<Bookmark>)

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: Long): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    suspend fun findByUrl(url: String): Bookmark?

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<Bookmark>>

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE url = :url")
    suspend fun exists(url: String): Boolean
}
