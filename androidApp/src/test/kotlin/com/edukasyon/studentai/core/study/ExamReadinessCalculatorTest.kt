package com.edukasyon.studentai.core.study

import com.edukasyon.studentai.domain.model.ExamReadinessContext
import com.edukasyon.studentai.domain.model.ExamReadinessNote
import com.edukasyon.studentai.domain.model.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamReadinessCalculatorTest {

    @Test
    fun emptyDeck_returnsZeroReadiness() {
        assertEquals(0, ExamReadinessCalculator.computeReadinessPercent(emptyList()))
    }

    @Test
    fun notesOnly_yieldModerateReadiness() {
        val notes = listOf(
            ExamReadinessNote("Chapter 1", listOf("ERD"), System.currentTimeMillis()),
            ExamReadinessNote("Chapter 2", listOf("SQL"), System.currentTimeMillis()),
        )
        val context = ExamReadinessContext(subjectNotes = notes, daysUntilExam = 10)
        val percent = ExamReadinessCalculator.computeReadinessPercent(emptyList(), context)
        assertTrue(percent in 20..80)
    }

    @Test
    fun masteredCards_yieldHighReadiness() {
        val cards = listOf(
            masteredCard("ERD"),
            masteredCard("SQL"),
            masteredCard("Normalization"),
        )
        val percent = ExamReadinessCalculator.computeReadinessPercent(cards)
        assertTrue(percent >= 85)
    }

    @Test
    fun weakTopics_classifiedCorrectly() {
        val cards = listOf(
            masteredCard("ERD", topic = "ERD"),
            newCard("Normalization", topic = "Normalization"),
        )
        val (_, _, weak) = ExamReadinessCalculator.classifyTopics(cards)
        assertTrue(weak.any { it.name == "Normalization" })
    }

    @Test
    fun noteTags_appearAsModerateWhenNotInCards() {
        val cards = listOf(masteredCard("ERD", topic = "ERD"))
        val notes = listOf(
            ExamReadinessNote("Lecture notes", listOf("Normalization"), System.currentTimeMillis()),
        )
        val (_, moderate, _) = ExamReadinessCalculator.classifyTopics(cards, notes)
        assertTrue(moderate.any { it.name == "Normalization" })
    }

    @Test
    fun recommendations_scaleWithDueCards() {
        val cards = List(6) { index ->
            newCard("Topic $index", due = true)
        }
        val weak = ExamReadinessCalculator.classifyTopics(cards).third
        val rec = ExamReadinessCalculator.buildRecommendations(cards, weak)
        assertEquals(6, rec.cardsToReview)
        assertTrue(rec.reviewMinutes >= 10)
    }

    @Test
    fun recommendations_boostReviewWhenExamSoon() {
        val cards = List(3) { index -> newCard("Topic $index", due = true) }
        val weak = ExamReadinessCalculator.classifyTopics(cards).third
        val urgent = ExamReadinessCalculator.buildRecommendations(
            cards,
            weak,
            ExamReadinessContext(daysUntilExam = 2),
        )
        val relaxed = ExamReadinessCalculator.buildRecommendations(
            cards,
            weak,
            ExamReadinessContext(daysUntilExam = 30),
        )
        assertTrue(urgent.reviewMinutes >= relaxed.reviewMinutes)
    }

    private fun masteredCard(question: String, topic: String = question): Flashcard =
        Flashcard(
            id = question,
            question = question,
            answer = "answer",
            subjectId = "sub1",
            deckId = "deck1",
            topic = topic,
            difficulty = "medium",
            reviewCount = 5,
            correctCount = 8,
            incorrectCount = 1,
            lastReviewedAt = System.currentTimeMillis(),
            nextReviewAt = System.currentTimeMillis() + 86_400_000L,
            easeFactor = 2.5,
            intervalDays = 30,
        )

    private fun newCard(question: String, topic: String = question, due: Boolean = true): Flashcard =
        Flashcard(
            id = question,
            question = question,
            answer = "answer",
            subjectId = "sub1",
            deckId = "deck1",
            topic = topic,
            difficulty = "medium",
            reviewCount = if (due) 1 else 0,
            correctCount = if (due) 1 else 0,
            incorrectCount = if (due) 2 else 0,
            lastReviewedAt = System.currentTimeMillis(),
            nextReviewAt = if (due) System.currentTimeMillis() - 1 else System.currentTimeMillis() + 86_400_000L,
            easeFactor = 2.5,
            intervalDays = 1,
        )
}
