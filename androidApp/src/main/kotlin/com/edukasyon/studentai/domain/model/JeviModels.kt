package com.edukasyon.studentai.domain.model

/** Spaced-repetition rating mapped to SM-2 quality scores. */
enum class ReviewRating(val quality: Int, val label: String) {
    AGAIN(0, "Again"),
    HARD(1, "Hard"),
    GOOD(3, "Good"),
    EASY(5, "Easy"),
}

data class JeviDeck(
    val id: String,
    val title: String,
    val description: String?,
    val subjectId: String?,
    val sourceNoteId: String?,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long,
    val cardCount: Int = 0,
    val dueCount: Int = 0,
    val masteredCount: Int = 0,
)

data class JeviReviewRecord(
    val id: String,
    val flashcardId: String,
    val deckId: String?,
    val quality: Int,
    val reviewedAt: Long,
    val intervalBefore: Int,
    val intervalAfter: Int,
    val easeFactorAfter: Double,
)

data class JeviDashboard(
    val dueCount: Int,
    val totalCards: Int,
    val deckCount: Int,
    val quizCount: Int = 0,
    val streakDays: Int,
    val xp: Int,
    val level: Int,
    val xpProgress: Float,
    val decks: List<JeviDeck>,
)

object JeviConstants {
    const val DEFAULT_DECK_ID = "jevi-default-deck"
    const val DEFAULT_DECK_TITLE = "General"
    const val DEFAULT_DECK_COLOR = "#6366F1"

    const val XP_REVIEW_AGAIN = 2
    const val XP_REVIEW_HARD = 5
    const val XP_REVIEW_GOOD = 10
    const val XP_REVIEW_EASY = 15
    const val XP_COMPLETE_SESSION = 20
    const val XP_COMPLETE_QUIZ = 25
    const val XP_GENERATE_QUIZ = 10
    const val XP_SAVE_QUIZ = 15

    fun xpForRating(quality: Int): Int = when (quality) {
        ReviewRating.AGAIN.quality -> XP_REVIEW_AGAIN
        ReviewRating.HARD.quality -> XP_REVIEW_HARD
        ReviewRating.GOOD.quality -> XP_REVIEW_GOOD
        ReviewRating.EASY.quality -> XP_REVIEW_EASY
        else -> XP_REVIEW_GOOD
    }
}
