package com.edukasyon.studentai.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionParserTest {

    @Test
    fun parse_actionsFence_extractsActions() {
        val reply = """
            Sure! I'll add that.

            ```actions
            {"actions":[{"type":"add_task","title":"Homework","priority":"MEDIUM"}]}
            ```
        """.trimIndent()

        val parsed = AiActionParser.parse(reply)
        assertEquals(1, parsed.actions.size)
        assertEquals("add_task", parsed.actions[0].type)
        assertEquals("Homework", parsed.actions[0].title)
        assertTrue(parsed.displayText.contains("Sure"))
    }

    @Test
    fun parse_trailingJson_extractsActions() {
        val reply = """Done! {"actions":[{"type":"add_task","title":"Quiz prep"}]}"""
        val parsed = AiActionParser.parse(reply)
        assertEquals(1, parsed.actions.size)
        assertEquals("Quiz prep", parsed.actions[0].title)
        assertEquals("Done!", parsed.displayText)
    }

    @Test
    fun parse_plainText_returnsEmptyActions() {
        val reply = "Recursion is when a function calls itself."
        val parsed = AiActionParser.parse(reply)
        assertEquals(reply, parsed.displayText)
        assertTrue(parsed.actions.isEmpty())
    }
}
