package com.edukasyon.studentai.core.study

import com.edukasyon.studentai.domain.model.ExamReadinessContext
import com.edukasyon.studentai.domain.model.ExamReadinessNote
import com.edukasyon.studentai.domain.model.ExamReadinessRecommendations
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.TopicStrength
import com.edukasyon.studentai.domain.model.TopicStrengthLevel
import kotlin.math.roundToInt

object ExamReadinessCalculator {

    fun computeReadinessPercent(
        cards: List<Flashcard>,
        context: ExamReadinessContext = ExamReadinessContext(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        if (cards.isEmpty() && context.subjectNotes.isEmpty()) return 0

        val mastery = if (cards.isNotEmpty()) deckMasteryScore(cards) else 0.0
        val quizProxy = if (cards.isNotEmpty()) quizPerformanceProxy(cards) else 50.0
        val dueHealth = if (cards.isNotEmpty()) dueHealthScore(cards, nowMillis) else 0.0
        val notesCoverage = notesCoverageScore(context.subjectNotes, nowMillis)
        val focusScore = focusStudyScore(context.focusMinutesForSubject)
        val timeAlignment = timeAlignmentScore(cards, context.daysUntilExam, nowMillis)

        val cardBlend = if (cards.isNotEmpty()) {
            mastery * 0.45 + quizProxy * 0.30 + dueHealth * 0.25
        } else {
            0.0
        }

        val blended = when {
            cards.isNotEmpty() && context.subjectNotes.isNotEmpty() ->
                cardBlend * 0.72 + notesCoverage * 0.13 + focusScore * 0.10 + timeAlignment * 0.05
            cards.isNotEmpty() ->
                cardBlend * 0.85 + focusScore * 0.10 + timeAlignment * 0.05
            context.subjectNotes.isNotEmpty() ->
                notesCoverage * 0.70 + focusScore * 0.20 + timeAlignment * 0.10
            else -> 0.0
        }

        return blended.roundToInt().coerceIn(0, 100)
    }

    fun classifyTopics(
        cards: List<Flashcard>,
        subjectNotes: List<ExamReadinessNote> = emptyList(),
    ): Triple<List<TopicStrength>, List<TopicStrength>, List<TopicStrength>> {
        val cardTopics = if (cards.isNotEmpty()) classifyCardTopics(cards) else emptyList()
        val noteTopics = noteTagTopics(subjectNotes, cardTopics.map { it.name }.toSet())

        val merged = (cardTopics + noteTopics)
            .sortedByDescending { it.scorePercent }
            .distinctBy { it.name.lowercase() }

        return Triple(
            merged.filter { it.level == TopicStrengthLevel.STRONG }.take(4),
            merged.filter { it.level == TopicStrengthLevel.MODERATE }.take(4),
            merged.filter { it.level == TopicStrengthLevel.WEAK }.take(4),
        )
    }

    fun buildRecommendations(
        cards: List<Flashcard>,
        weakTopics: List<TopicStrength>,
        context: ExamReadinessContext = ExamReadinessContext(),
        nowMillis: Long = System.currentTimeMillis(),
    ): ExamReadinessRecommendations {
        val dueCount = cards.count { it.nextReviewAt == null || it.nextReviewAt <= nowMillis }
        val cardsToReview = dueCount.coerceAtLeast(if (cards.isEmpty()) 0 else 1)
        val weakCount = weakTopics.size.coerceAtLeast(1)
        val quizFromWeak = (weakCount * 3).coerceIn(5, 15)
        val quizQuestionCount = when {
            context.deckQuizQuestionCount > 0 ->
                context.deckQuizQuestionCount.coerceIn(5, 15)
            context.deckQuizCount > 0 -> quizFromWeak
            else -> quizFromWeak
        }

        val rawMinutes = (dueCount * 1.5).roundToInt()
        var reviewMinutes = when {
            cards.isEmpty() && context.subjectNotes.isNotEmpty() ->
                (context.subjectNotes.size * 5).coerceIn(10, 30)
            cards.isEmpty() -> 15
            rawMinutes < 10 -> 10
            else -> ((rawMinutes + 4) / 5) * 5
        }.coerceAtMost(45)

        // Exam urgency: add review time when the exam is close and cards are due.
        val daysLeft = context.daysUntilExam
        if (daysLeft != null && daysLeft <= 3 && dueCount > 0) {
            reviewMinutes = (reviewMinutes + 5).coerceAtMost(45)
        }

        return ExamReadinessRecommendations(
            cardsToReview = cardsToReview,
            quizQuestionCount = quizQuestionCount,
            reviewMinutes = reviewMinutes,
        )
    }

    private fun classifyCardTopics(cards: List<Flashcard>): List<TopicStrength> {
        val grouped = cards.groupBy { card ->
            card.topic?.trim()?.takeIf { it.isNotEmpty() } ?: "General"
        }
        return grouped.map { (topic, topicCards) ->
            val score = topicScore(topicCards).roundToInt().coerceIn(0, 100)
            TopicStrength(
                name = topic,
                level = strengthLevelForScore(score),
                scorePercent = score,
            )
        }
    }

    private fun noteTagTopics(
        notes: List<ExamReadinessNote>,
        existingTopics: Set<String>,
    ): List<TopicStrength> {
        if (notes.isEmpty()) return emptyList()

        val tagCounts = mutableMapOf<String, Int>()
        notes.forEach { note ->
            val tags = note.tags.filter { it.isNotBlank() }
            if (tags.isEmpty()) {
                val title = note.title.trim().ifBlank { "Notes" }
                if (title.lowercase() !in existingTopics.map { it.lowercase() }.toSet()) {
                    tagCounts[title] = tagCounts.getOrDefault(title, 0) + 1
                }
            } else {
                tags.forEach { tag ->
                    if (tag.lowercase() !in existingTopics.map { it.lowercase() }.toSet()) {
                        tagCounts[tag] = tagCounts.getOrDefault(tag, 0) + 1
                    }
                }
            }
        }

        return tagCounts.map { (tag, count) ->
            val score = (40 + count * 10).coerceAtMost(65)
            TopicStrength(
                name = tag,
                level = TopicStrengthLevel.MODERATE,
                scorePercent = score,
            )
        }
    }

    private fun notesCoverageScore(notes: List<ExamReadinessNote>, nowMillis: Long): Double {
        if (notes.isEmpty()) return 0.0
        val countScore = (notes.size.coerceAtMost(5) / 5.0) * 60.0
        val recentCount = notes.count { note ->
            nowMillis - note.updatedAt <= RECENT_NOTE_WINDOW_MS
        }
        val recencyScore = (recentCount.coerceAtMost(3) / 3.0) * 40.0
        return (countScore + recencyScore).coerceIn(0.0, 100.0)
    }

    private fun focusStudyScore(focusMinutes: Int): Double =
        (focusMinutes.coerceAtMost(200) / 200.0 * 100.0).coerceIn(0.0, 100.0)

    private fun timeAlignmentScore(
        cards: List<Flashcard>,
        daysUntilExam: Long?,
        nowMillis: Long,
    ): Double {
        if (daysUntilExam == null) return 50.0
        if (cards.isEmpty()) {
            return when {
                daysUntilExam > 14 -> 70.0
                daysUntilExam > 7 -> 50.0
                else -> 30.0
            }
        }
        val dueRatio = cards.count { it.nextReviewAt == null || it.nextReviewAt <= nowMillis }
            .toDouble() / cards.size
        return when {
            daysUntilExam <= 3 && dueRatio > 0.5 -> 20.0
            daysUntilExam <= 7 && dueRatio > 0.4 -> 40.0
            daysUntilExam > 14 && dueRatio < 0.2 -> 85.0
            else -> 60.0
        }
    }

    private fun strengthLevelForScore(score: Int): TopicStrengthLevel = when {
        score >= 70 -> TopicStrengthLevel.STRONG
        score >= 40 -> TopicStrengthLevel.MODERATE
        else -> TopicStrengthLevel.WEAK
    }

    private fun deckMasteryScore(cards: List<Flashcard>): Double =
        cards.map(::cardMasteryScore).average()

    private fun cardMasteryScore(card: Flashcard): Double {
        if (card.reviewCount >= 3 && card.intervalDays >= 21) return 100.0
        if (card.reviewCount == 0) return 0.0
        val accuracy = accuracyRatio(card)
        val intervalBonus = (card.intervalDays.coerceAtMost(21) / 21.0) * 30.0
        return (accuracy * 70.0 + intervalBonus).coerceIn(0.0, 100.0)
    }

    private fun quizPerformanceProxy(cards: List<Flashcard>): Double {
        val reviewed = cards.filter { it.reviewCount > 0 }
        if (reviewed.isEmpty()) return 50.0
        return reviewed.map { accuracyRatio(it) * 100.0 }.average()
    }

    private fun dueHealthScore(cards: List<Flashcard>, nowMillis: Long): Double {
        if (cards.isEmpty()) return 0.0
        val due = cards.count { it.nextReviewAt == null || it.nextReviewAt <= nowMillis }
        return (1.0 - due.toDouble() / cards.size) * 100.0
    }

    private fun topicScore(cards: List<Flashcard>): Double =
        cards.map(::cardMasteryScore).average()

    private fun accuracyRatio(card: Flashcard): Double {
        val total = card.correctCount + card.incorrectCount
        return if (total == 0) 0.5 else card.correctCount.toDouble() / total
    }

    private const val RECENT_NOTE_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
}
