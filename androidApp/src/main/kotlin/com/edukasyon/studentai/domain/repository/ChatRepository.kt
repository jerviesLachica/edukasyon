package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.ChatMessage
import com.edukasyon.studentai.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun createConversation(title: String, isGroup: Boolean = false): Conversation
    suspend fun sendMessage(conversationId: String, senderId: String, content: String)
}
