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

    /**
     * CHANGED with the thinking-mode answer-collapse fix: used to assert
     * reply == "" (the whole deliberation blob demoted into reasoning with
     * nothing left to show the student). Now, with no provider-side reasoning
     * to show instead, the original text must survive as the reply.
     */
    @Test
    fun split_keepsUntaggedDeliberationAsReplyWhenNoProviderReasoning() {
        val raw =
            "Got it, let's tackle this essay on Jose Rizal. First, I need to make it appropriate " +
                "for a student... Wait, the user is a student so I should keep the tone simple."
        val result = ReasoningContentSplitter.split(raw)
        assertEquals(raw, result.reply)
        assertNull(result.reasoning)
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

    // ---- thinking-mode answer-collapse regression tests ----

    private val longDeliberationBlob = buildString {
        append("I'll start by breaking this down. I should check what the student ")
        repeat(15) {
            append("really needs is a clear, simple explanation that covers the ")
        }
        append("key ideas without overwhelming them, so I should keep the tone ")
        append("friendly and focus on examples they can relate to every day. ")
    }

    @Test
    fun longUntaggedDeliberation_withoutProviderReasoning_neverLeavesBlankReply() {
        assertTrue(longDeliberationBlob.length > 600)
        val result = ReasoningContentSplitter.split(longDeliberationBlob)
        // With no provider-side reasoning to show instead, the student must
        // still get an answer: the original text comes back as the reply.
        assertTrue(result.reply.isNotEmpty())
        assertEquals(longDeliberationBlob.trim(), result.reply)
        assertNull(result.reasoning)
    }

    @Test
    fun longUntaggedDeliberation_withProviderReasoning_stillDemotesIntoReasoning() {
        val result = ReasoningContentSplitter.split(longDeliberationBlob, existingReasoning = "Provider trace")
        // A provider trace means the thinking panel has something to show, so
        // the demotion path stays intact (RemoteAiService restores the visible
        // reply one level up via its own safety net).
        assertEquals("", result.reply)
        assertTrue(result.reasoning!!.contains("Provider trace"))
        assertTrue(result.reasoning!!.contains("I'll start by breaking this down"))
    }
}
