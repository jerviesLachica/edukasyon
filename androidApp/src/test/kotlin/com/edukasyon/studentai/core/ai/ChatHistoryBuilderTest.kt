package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.AiConversationMessage
import com.edukasyon.studentai.domain.model.GizmoChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryBuilderTest {

    @Test
    fun fromGizmoMessages_mapsRolesAndContent() {
        val messages = listOf(
            GizmoChatMessage(
                sender = "You",
                content = "Write me an essay about Jose Rizal",
                isUser = true,
                timestamp = 1L,
            ),
            GizmoChatMessage(
                sender = "Jarvis",
                content = "Here is your essay...",
                isUser = false,
                timestamp = 2L,
            ),
        )
        val history = ChatHistoryBuilder.fromGizmoMessages(messages)
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("assistant", history[1].role)
        assertTrue(history[0].content.contains("Jose Rizal"))
    }

    @Test
    fun fromConversationMessages_mapsPersistedRoomMessages() {
        val messages = listOf(
            AiConversationMessage(
                id = "1",
                conversationId = "conv-1",
                isUser = true,
                content = "What is mitosis?",
                sentAt = 1L,
            ),
            AiConversationMessage(
                id = "2",
                conversationId = "conv-1",
                isUser = false,
                content = "Mitosis is cell division...",
                sentAt = 2L,
                metadataJson = """{"kind":"TUTOR","reasoning":"Checked biology basics."}""",
            ),
        )
        val history = ChatHistoryBuilder.fromConversationMessages(messages)
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("assistant", history[1].role)
        assertTrue(history[0].content.contains("mitosis"))
    }

    @Test
    fun fromGizmoMessages_usesReasoningWhenAssistantContentEmpty() {
        val messages = listOf(
            GizmoChatMessage(
                sender = "Jarvis",
                content = "",
                isUser = false,
                timestamp = 2L,
                reasoning = "Essay body stored in reasoning only.",
            ),
        )
        val history = ChatHistoryBuilder.fromGizmoMessages(messages)
        assertEquals(1, history.size)
        assertEquals("assistant", history[0].role)
        assertTrue(history[0].content.contains("Essay body"))
    }
}
