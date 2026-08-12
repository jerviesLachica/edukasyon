package com.edukasyon.studentai.core.network

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
}
