package com.edukasyon.studentai.domain.model

enum class AiConversationType {
    TUTOR,
    SUMMARIZE,
    FLASHCARDS,
    QUIZ;

    val displayName: String
        get() = when (this) {
            TUTOR -> "Tutor"
            SUMMARIZE -> "Summarize"
            FLASHCARDS -> "Flashcards"
            QUIZ -> "Quiz"
        }

    companion object {
        val TOOL_TYPES = listOf(SUMMARIZE, FLASHCARDS, QUIZ)
    }
}

data class AiConversation(
    val id: String,
    val title: String,
    val type: AiConversationType,
    val backendConversationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AiConversationMessage(
    val id: String,
    val conversationId: String,
    val isUser: Boolean,
    val content: String,
    val sentAt: Long,
    val attachmentName: String? = null,
    val attachmentIsImage: Boolean = false,
    val metadataJson: String? = null,
)
