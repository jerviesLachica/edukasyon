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

    @Test
    fun sanitizeReply_unescapesNewlines() {
        val raw = "  Hello\\nWorld\\r  "
        val cleaned = AiActionParser.sanitizeReply(raw)
        assertEquals("Hello\nWorld\r", cleaned)
    }

    @Test
    fun parseAndExtract_extractsStudyBlocks() {
        val reply = """
            Here's a study plan.

            ```actions
            {"actions":[{"type":"propose_study_blocks","blocks":[{"subject":"Math","date":"2025-01-15","startTime":"09:00","endTime":"10:00"}]}]}
            ```
        """.trimIndent()

        val parsed = AiActionParser.parseAndExtract(reply)
        assertEquals(1, parsed.studyBlocks.size)
        assertEquals("Math", parsed.studyBlocks[0].subject)
        assertTrue(parsed.directActions.isEmpty())
        assertTrue(parsed.displayText.contains("study plan"))
    }

    @Test
    fun parseAndExtract_extractsFollowUps() {
        val reply = """
            Great question!

            ```actions
            {"actions":[{"type":"suggest_followups","items":["What is recursion?","How does a stack work?","What is recursion?"]}]}
            ```
        """.trimIndent()

        val parsed = AiActionParser.parseAndExtract(reply)
        // Duplicates should be removed
        assertEquals(2, parsed.followUps.size)
        assertTrue(parsed.followUps.contains("What is recursion?"))
        assertTrue(parsed.followUps.contains("How does a stack work?"))
    }

    @Test
    fun parseAndExtract_separatesDirectAndProposalActions() {
        val reply = """
            Done!

            ```actions
            {"actions":[{"type":"add_task","title":"Homework"},{"type":"suggest_followups","items":["Next step?"]}]}
            ```
        """.trimIndent()

        val parsed = AiActionParser.parseAndExtract(reply)
        assertEquals(1, parsed.directActions.size)
        assertEquals("add_task", parsed.directActions[0].type)
        assertEquals(1, parsed.followUps.size)
        assertEquals("Next step?", parsed.followUps[0])
    }
}
