package com.edukasyon.studentai.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReasoningContentSplitterTest {

    @Test
    fun split_stripsThinkTags() {
        val thinkOpen = "<" + "think" + ">"
        val thinkClose = "</" + "think" + ">"
        val raw = thinkOpen + "Planning steps" + thinkClose + "\n\n# Jose Rizal\n\nEssay body here."
        val result = ReasoningContentSplitter.split(raw)
        assertEquals("Planning steps", result.reasoning)
        assertTrue(result.reply.contains("Jose Rizal"))
    }

    @Test
    fun split_movesUntaggedPreambleToReasoning() {
        val raw =
            "Got it, let's tackle this 1000-word essay on Jose Rizal. First, I need to make it " +
                "appropriate for a student... Wait, the user is a student so...\n\n" +
                "# Jose Rizal: A Student's Perspective\n\nEssay content starts here."
        val result = ReasoningContentSplitter.split(raw)
        assertTrue(result.reasoning?.contains("Got it, let's tackle") == true)
        assertTrue(result.reply.contains("Jose Rizal: A Student's Perspective"))
        assertTrue(!result.reply.contains("Wait, the user"))
    }

    @Test
    fun split_preservesExistingReasoning() {
        val result = ReasoningContentSplitter.split("Hello!", existingReasoning = "Prior thought")
        assertEquals("Hello!", result.reply)
        assertEquals("Prior thought", result.reasoning)
    }

    @Test
    fun split_blankInput() {
        val result = ReasoningContentSplitter.split("   ")
        assertEquals("", result.reply)
        assertNull(result.reasoning)
    }
}
