package com.edukasyon.studentai.data.repository

import com.edukasyon.studentai.data.local.dao.AiConversationDao
import com.edukasyon.studentai.data.local.entity.ConversationEntity
import com.edukasyon.studentai.data.local.entity.MessageEntity
import com.edukasyon.studentai.domain.model.ChatMessage
import com.edukasyon.studentai.domain.model.Conversation
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: AiConversationDao
) : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeStudyGroups().map { list ->
            list.map { Conversation(it.id, it.title, it.isGroup, it.updatedAt) }
        }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        conversationDao.observeMessages(conversationId).map { list ->
            list.map {
                ChatMessage(it.id, it.conversationId, it.senderId, it.content, it.sentAt, it.isRead)
            }
        }

    override suspend fun createConversation(title: String, isGroup: Boolean): Conversation {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        conversationDao.insert(
            ConversationEntity(id, title, isGroup, now, now, SyncState.LOCAL_ONLY.name)
        )
        return Conversation(id, title, isGroup, now)
    }

    override suspend fun sendMessage(conversationId: String, senderId: String, content: String) {
        val now = System.currentTimeMillis()
        conversationDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                content = content,
                sentAt = now,
                isRead = true,
                syncState = SyncState.LOCAL_ONLY.name
            )
        )
        conversationDao.touchUpdatedAt(conversationId, now)
    }
}
