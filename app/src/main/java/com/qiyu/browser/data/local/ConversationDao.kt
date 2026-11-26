package com.qiyu.browser.data.local

import androidx.room.*
import com.qiyu.browser.model.AiMessage
import com.qiyu.browser.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<Conversation>>

    @Insert
    suspend fun insertConversation(c: Conversation): Long

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Long): Conversation?

    @Update
    suspend fun updateConversation(c: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("SELECT * FROM ai_messages WHERE conversationId = :cid ORDER BY id ASC")
    fun observeMessages(cid: Long): Flow<List<AiMessage>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :cid ORDER BY id ASC")
    suspend fun getMessages(cid: Long): List<AiMessage>

    @Insert
    suspend fun insertMessage(m: AiMessage)

    @Query("DELETE FROM ai_messages WHERE conversationId = :cid")
    suspend fun deleteMessages(cid: Long)
}
