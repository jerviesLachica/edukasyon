package com.edukasyon.studentai.domain.model

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
}
