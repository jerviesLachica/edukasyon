package com.edukasyon.studentai.core.study

import com.edukasyon.studentai.domain.model.Flashcard
import java.util.concurrent.TimeUnit

/** Simplified SM-2 spaced repetition. Quality: 0=Again, 1=Hard, 3=Good, 5=Easy */
object Sm2Algorithm {
    private const val DAY_MS = 86_400_000L

    fun review(card: Flashcard, quality: Int): Flashcard {
        val now = System.currentTimeMillis()
        val ease = card.easeFactor
        var interval = card.intervalDays
        var repetitions = card.reviewCount

        if (quality < 3) {
            repetitions = 0
            interval = 1
        } else {
            repetitions += 1
            interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> (interval * ease).toInt().coerceAtLeast(1)
            }
        }

        val newEase = (ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
            .coerceIn(1.3, 2.5)

        val nextReview = now + interval * DAY_MS
        val isCorrect = quality >= 3

        return card.copy(
            easeFactor = newEase,
            intervalDays = interval,
            reviewCount = repetitions,
            correctCount = card.correctCount + if (isCorrect) 1 else 0,
            incorrectCount = card.incorrectCount + if (isCorrect) 0 else 1,
            lastReviewedAt = now,
            nextReviewAt = nextReview
        )
    }

    fun isDue(card: Flashcard, now: Long = System.currentTimeMillis()): Boolean {
        val next = card.nextReviewAt ?: return true
        return next <= now
    }

    fun dueInDays(card: Flashcard): Long {
        val next = card.nextReviewAt ?: return 0
        return TimeUnit.MILLISECONDS.toDays((next - System.currentTimeMillis()).coerceAtLeast(0))
    }
}
