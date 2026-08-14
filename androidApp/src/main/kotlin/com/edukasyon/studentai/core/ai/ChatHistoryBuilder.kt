package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.AiConversationMessage
import com.edukasyon.studentai.domain.model.GizmoChatMessage

/**
 * Builds OpenAI-style chat history for Jevi tutor requests from persisted messages.
 */
object ChatHistoryBuilder {
    private const val MAX_MESSAGES = 40
    private const val MAX_CHARS = 24_000

    /** Prefer Room-backed messages over in-memory UI state when sending follow-ups. */
    fun fromConversationMessages(messages: List<AiConversationMessage>): List<AiChatHistoryMessage> =
        fromGizmoMessages(messages.map { msg ->
            GizmoChatMessage(
                sender = if (msg.isUser) "You" else "Jevi",
                content = msg.content,
                isUser = msg.isUser,
                timestamp = msg.sentAt,
                attachmentName = msg.attachmentName,
                attachmentIsImage = msg.attachmentIsImage,
                reasoning = if (!msg.isUser) {
                    AiConversationMetadata.decodeTutorReasoning(msg.metadataJson)
                } else {
                    null
                },
            )
        })

    fun fromGizmoMessages(messages: List<GizmoChatMessage>): List<AiChatHistoryMessage> {
        val turns = messages.mapNotNull { msg ->
            val content = buildContent(msg)
            if (content.isBlank()) return@mapNotNull null
            AiChatHistoryMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = content,
            )
        }
        return applySlidingWindow(turns)
    }

    private fun buildContent(msg: GizmoChatMessage): String {
        val parts = mutableListOf<String>()
        if (msg.content.isNotBlank()) {
            parts.add(msg.content.trim())
        } else if (!msg.isUser && !msg.reasoning.isNullOrBlank()) {
            // Assistant turn stored with reasoning only — include for follow-up context.
            parts.add(msg.reasoning.trim())
        }
        msg.attachmentName?.let { name ->
            parts.add("[Attached: $name${if (msg.attachmentIsImage) " (image)" else ""}]")
        }
        return parts.joinToString("\n")
    }

    private fun applySlidingWindow(turns: List<AiChatHistoryMessage>): List<AiChatHistoryMessage> {
        val recent = turns.takeLast(MAX_MESSAGES).toMutableList()
        var totalChars = recent.sumOf { it.content.length }
        while (totalChars > MAX_CHARS && recent.size > 2) {
            val removed = recent.removeAt(0)
            totalChars -= removed.content.length
        }
        return recent
    }
}
