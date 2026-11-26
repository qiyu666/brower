package com.qiyu.browser.data.repository

import com.qiyu.browser.data.local.ConversationDao
import com.qiyu.browser.model.AiMessage
import com.qiyu.browser.model.Conversation
import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val dao: ConversationDao) {

    fun observeConversations(): Flow<List<Conversation>> = dao.observeConversations()

    suspend fun insertConversation(c: Conversation): Long = dao.insertConversation(c)

    suspend fun getConversation(id: Long): Conversation? = dao.getConversation(id)

    suspend fun updateConversation(c: Conversation) = dao.updateConversation(c)

    suspend fun deleteConversation(id: Long) {
        dao.deleteMessages(id)
        dao.deleteConversation(id)
    }

    fun observeMessages(cid: Long): Flow<List<AiMessage>> = dao.observeMessages(cid)

    suspend fun getMessages(cid: Long): List<AiMessage> = dao.getMessages(cid)

    suspend fun addMessage(m: AiMessage) = dao.insertMessage(m)
}
