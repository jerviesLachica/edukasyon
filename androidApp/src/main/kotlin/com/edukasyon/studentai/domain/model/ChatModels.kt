package com.edukasyon.studentai.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val updatedAt: Long
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val sentAt: Long,
    val isRead: Boolean
)
