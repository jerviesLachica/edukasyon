package com.edukasyon.studentai.core.study

import com.edukasyon.studentai.domain.model.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2AlgorithmTest {

    private fun sampleCard(
        reviewCount: Int = 0,
        intervalDays: Int = 1,
        easeFactor: Double = 2.5,
        nextReviewAt: Long? = null,
    ) = Flashcard(
        id = "test-1",
        question = "Q",
        answer = "A",
        subjectId = null,
        deckId = null,
        topic = null,
        difficulty = "medium",
        reviewCount = reviewCount,
        correctCount = 0,
        incorrectCount = 0,
        lastReviewedAt = null,
        nextReviewAt = nextReviewAt,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
    )

    @Test
    fun isDue_whenNextReviewNull_returnsTrue() {
        assertTrue(Sm2Algorithm.isDue(sampleCard(nextReviewAt = null)))
    }

    @Test
    fun isDue_whenNextReviewInPast_returnsTrue() {
        val past = System.currentTimeMillis() - 86_400_000L
        assertTrue(Sm2Algorithm.isDue(sampleCard(nextReviewAt = past)))
    }

    @Test
    fun isDue_whenNextReviewInFuture_returnsFalse() {
        val future = System.currentTimeMillis() + 86_400_000L * 7
        assertFalse(Sm2Algorithm.isDue(sampleCard(nextReviewAt = future)))
    }

    @Test
    fun review_again_resetsInterval() {
        val card = sampleCard(reviewCount = 3, intervalDays = 10)
        val result = Sm2Algorithm.review(card, quality = 0)
        assertEquals(0, result.reviewCount)
        assertEquals(1, result.intervalDays)
        assertEquals(1, result.incorrectCount)
    }

    @Test
    fun review_good_incrementsRepetitions() {
        val card = sampleCard(reviewCount = 0)
        val result = Sm2Algorithm.review(card, quality = 3)
        assertEquals(1, result.reviewCount)
        assertEquals(1, result.intervalDays)
        assertEquals(1, result.correctCount)
    }

    @Test
    fun review_good_secondRepetition_setsSixDayInterval() {
        val card = sampleCard(reviewCount = 1, intervalDays = 1)
        val result = Sm2Algorithm.review(card, quality = 3)
        assertEquals(2, result.reviewCount)
        assertEquals(6, result.intervalDays)
    }

    @Test
    fun review_easy_increasesEaseFactor() {
        val card = sampleCard(easeFactor = 2.5)
        val result = Sm2Algorithm.review(card, quality = 5)
        assertTrue(result.easeFactor >= 2.5)
    }

    @Test
    fun review_hard_decreasesEaseFactor() {
        val card = sampleCard(easeFactor = 2.5)
        val result = Sm2Algorithm.review(card, quality = 1)
        assertTrue(result.easeFactor < 2.5)
    }

    @Test
    fun review_setsNextReviewAt() {
        val before = System.currentTimeMillis()
        val result = Sm2Algorithm.review(sampleCard(), quality = 3)
        assertTrue(result.nextReviewAt != null)
        assertTrue(result.nextReviewAt!! >= before)
        assertTrue(result.lastReviewedAt != null)
    }

    @Test
    fun easeFactor_clampedToMinimum() {
        val card = sampleCard(easeFactor = 1.3)
        repeat(5) {
            val result = Sm2Algorithm.review(card, quality = 0)
            assertTrue(result.easeFactor >= 1.3)
        }
    }
}
