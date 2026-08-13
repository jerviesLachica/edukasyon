package com.edukasyon.studentai.data.repository

import com.edukasyon.studentai.data.local.dao.AiConversationDao
import com.edukasyon.studentai.data.local.entity.ConversationEntity
import com.edukasyon.studentai.data.local.entity.MessageEntity
import com.edukasyon.studentai.domain.model.AiConversation
import com.edukasyon.studentai.domain.model.AiConversationMessage
import com.edukasyon.studentai.domain.model.AiConversationType
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.repository.AiConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiConversationRepositoryImpl @Inject constructor(
    private val conversationDao: AiConversationDao,
) : AiConversationRepository {

    override fun observeConversations(types: List<AiConversationType>): Flow<List<AiConversation>> =
        conversationDao.observeByTypes(types.map { it.name }).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getConversation(id: String): AiConversation? =
        conversationDao.getById(id)?.toDomain()

    override suspend fun getMessages(conversationId: String): List<AiConversationMessage> =
        conversationDao.getMessages(conversationId).map { it.toDomain() }

    override suspend fun createConversation(type: AiConversationType, title: String): AiConversation {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = ConversationEntity(
            id = id,
            title = title,
            isGroup = false,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.LOCAL_ONLY.name,
            conversationType = type.name,
            backendConversationId = null,
        )
        conversationDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun saveMessage(message: AiConversationMessage) {
        conversationDao.insertMessage(
            MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                senderId = if (message.isUser) SENDER_USER else SENDER_ASSISTANT,
                content = message.content,
                sentAt = message.sentAt,
                isRead = true,
                syncState = SyncState.LOCAL_ONLY.name,
                attachmentName = message.attachmentName,
                attachmentIsImage = message.attachmentIsImage,
                metadataJson = message.metadataJson,
            )
        )
        conversationDao.touchUpdatedAt(message.conversationId, message.sentAt)
    }

    override suspend fun updateBackendConversationId(conversationId: String, backendConversationId: String) {
        conversationDao.updateBackendConversationId(
            conversationId,
            backendConversationId,
            System.currentTimeMillis(),
        )
    }

    override suspend fun updateTitle(conversationId: String, title: String) {
        conversationDao.updateTitle(conversationId, title, System.currentTimeMillis())
    }

    private fun ConversationEntity.toDomain(): AiConversation = AiConversation(
        id = id,
        title = title,
        type = AiConversationType.valueOf(conversationType ?: AiConversationType.TUTOR.name),
        backendConversationId = backendConversationId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MessageEntity.toDomain(): AiConversationMessage = AiConversationMessage(
        id = id,
        conversationId = conversationId,
        isUser = senderId == SENDER_USER,
        content = content,
        sentAt = sentAt,
        attachmentName = attachmentName,
        attachmentIsImage = attachmentIsImage,
        metadataJson = metadataJson,
    )

    companion object {
        const val SENDER_USER = "user"
        const val SENDER_ASSISTANT = "assistant"
    }
}
