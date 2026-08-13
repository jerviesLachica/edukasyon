package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.AiConversation
import com.edukasyon.studentai.domain.model.AiConversationMessage
import com.edukasyon.studentai.domain.model.AiConversationType
import kotlinx.coroutines.flow.Flow

interface AiConversationRepository {
    fun observeConversations(types: List<AiConversationType>): Flow<List<AiConversation>>
    suspend fun getConversation(id: String): AiConversation?
    suspend fun getMessages(conversationId: String): List<AiConversationMessage>
    suspend fun createConversation(type: AiConversationType, title: String): AiConversation
    suspend fun saveMessage(message: AiConversationMessage)
    suspend fun updateBackendConversationId(conversationId: String, backendConversationId: String)
    suspend fun updateTitle(conversationId: String, title: String)
}
