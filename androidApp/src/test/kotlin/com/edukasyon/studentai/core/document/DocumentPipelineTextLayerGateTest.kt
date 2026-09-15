package com.edukasyon.studentai.core.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the hybrid text-layer vs vision gate. Pure logic — no Room, no network.
 */
class DocumentPipelineTextLayerGateTest {

    private fun wordList(n: Int): String = List(n) { "w$it" }.joinToString(" ")

    @Test
    fun thresholdConstant_is25Words() {
        assertEquals(25, DocumentPipeline.MIN_TEXTLAYER_WORDS)
    }

    @Test
    fun twentyFourWords_goesToVision() {
        assertFalse(DocumentPipeline.pageUsesTextLayer(wordList(24)))
    }

    @Test
    fun twentyFiveWords_usesFreeTextLayer() {
        assertTrue(DocumentPipeline.pageUsesTextLayer(wordList(25)))
    }

    @Test
    fun nullTextLayer_goesToVision() {
        assertFalse(DocumentPipeline.pageUsesTextLayer(null))
    }

    @Test
    fun blankTextLayer_goesToVision() {
        assertFalse(DocumentPipeline.pageUsesTextLayer("   \n  "))
    }
}
