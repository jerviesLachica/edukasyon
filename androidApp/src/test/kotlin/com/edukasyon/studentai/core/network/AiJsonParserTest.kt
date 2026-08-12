package com.edukasyon.studentai.core.network

import com.edukasyon.studentai.domain.model.QuestionType
import org.junit.Assert.*
import org.junit.Test

class AiJsonParserTest {
    @Test
    fun parseScheduleAnalysis_validJson() {
        val json = """{"classes":[{"subject":"Programming 2","teacher":"Juan Santos","room":"304","day":"MONDAY","startTime":"08:00","endTime":"09:30"}],"uncertainFields":[]}"""
        val result = AiJsonParser.parseScheduleAnalysis(json)
        assertNotNull(result)
        assertEquals(1, result!!.classes.size)
        assertEquals("Programming 2", result.classes[0].subject)
    }

    @Test
    fun parseScheduleAnalysis_invalidJson_returnsNull() {
        assertNull(AiJsonParser.parseScheduleAnalysis("not json"))
    }

    @Test
    fun parseScheduleAnalysis_missingFields_returnsNull() {
        assertNull(AiJsonParser.parseScheduleAnalysis("{}"))
    }

    @Test
    fun parseFlashcards_validJson() {
        val json = """{"cards":[{"question":"Q1","answer":"A1"},{"question":"Q2","answer":"A2"}]}"""
        val result = AiJsonParser.parseFlashcards(json)
        assertNotNull(result)
        assertEquals(2, result!!.size)
    }

    @Test
    fun parseFlashcards_withMarkdownFences() {
        val json = """
            ```json
            {"cards":[{"question":"Q1","answer":"A1"}]}
            ```
        """.trimIndent()
        val result = AiJsonParser.parseFlashcards(json)
        assertNotNull(result)
        assertEquals(1, result!!.size)
    }

    @Test
    fun parseQuiz_withMarkdownFences() {
        val json = """
            ```json
            {"title":"Test Quiz","questions":[{"type":"MULTIPLE_CHOICE","question":"Q?","options":["A","B"],"correctAnswer":"A"}]}
            ```
        """.trimIndent()
        val result = AiJsonParser.parseQuiz(json)
        assertNotNull(result)
        assertEquals("Test Quiz", result!!.title)
        assertEquals(1, result.questions.size)
    }

    @Test
    fun normalizeQuestionType_handlesVariations() {
        assertEquals(QuestionType.TRUE_FALSE, AiJsonParser.normalizeQuestionType("true_false"))
        assertEquals(QuestionType.MULTIPLE_CHOICE, AiJsonParser.normalizeQuestionType("multiple-choice"))
    }
}
