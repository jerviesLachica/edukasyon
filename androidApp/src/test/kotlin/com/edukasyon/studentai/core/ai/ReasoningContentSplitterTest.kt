package com.edukasyon.studentai.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun split_movesAllReasoningWhenNoAnswerPresent() {
        val raw =
            "Got it, let's tackle this essay on Jose Rizal. First, I need to make it appropriate " +
                "for a student... Wait, the user is a student so I should keep the tone simple."
        val result = ReasoningContentSplitter.split(raw)
        assertEquals("", result.reply)
        assertTrue(result.reasoning?.contains("Got it, let's tackle") == true)
    }

    @Test
    fun split_preservesNormalTutorReply() {
        val raw =
            "Photosynthesis is how plants turn sunlight into food. Chlorophyll in leaves absorbs " +
                "light energy and converts carbon dioxide and water into glucose."
        val result = ReasoningContentSplitter.split(raw)
        assertEquals(raw, result.reply)
        assertNull(result.reasoning)
    }

    @Test
    fun split_recoversLongEssayMisclassifiedAsReasoning() {
        val reasoningOnly = buildString {
            repeat(150) {
                append("Jose Rizal was a national hero who shaped Philippine history through his writings and sacrifice. ")
            }
        }
        val split = ReasoningContentSplitter.split(
            "Got it, let's tackle this essay. First, I need to plan the structure...\n\n$reasoningOnly"
        )
        val recovered = ReasoningContentSplitter.recoverEmptyReply(split)
        assertTrue(recovered.reply.isNotEmpty())
    }

    @Test
    fun split_mergesExistingProviderReasoning() {
        val raw = "Here is the answer."
        val result = ReasoningContentSplitter.split(raw, existingReasoning = "Provider-side trace")
        assertEquals("Here is the answer.", result.reply)
        assertEquals("Provider-side trace", result.reasoning)
    }
}
