package com.qiyu.browser.data.repository

import com.qiyu.browser.data.local.BookmarkDao
import com.qiyu.browser.model.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val dao: BookmarkDao) {

    suspend fun add(bookmark: Bookmark) = dao.insert(bookmark)

    suspend fun update(bookmark: Bookmark) = dao.update(bookmark)

    suspend fun delete(bookmark: Bookmark) = dao.delete(bookmark)

    suspend fun deleteByUrl(url: String) = dao.deleteByUrl(url)

    suspend fun findByUrl(url: String): Bookmark? = dao.findByUrl(url)

    fun getAll(): Flow<List<Bookmark>> = dao.getAll()

    fun search(query: String): Flow<List<Bookmark>> = dao.search(query)

    suspend fun isBookmarked(url: String): Boolean = dao.exists(url)
}
