package com.edukasyon.studentai.domain.model

enum class TopicStrengthLevel { STRONG, MODERATE, WEAK }

/** Lightweight note snapshot for readiness scoring (avoids pulling full note bodies). */
data class ExamReadinessNote(
    val title: String,
    val tags: List<String>,
    val updatedAt: Long,
)

/** Extra study signals layered on top of linked-deck flashcard data. */
data class ExamReadinessContext(
    val daysUntilExam: Long? = null,
    val subjectNotes: List<ExamReadinessNote> = emptyList(),
    val deckQuizCount: Int = 0,
    val deckQuizQuestionCount: Int = 0,
    val focusMinutesForSubject: Int = 0,
)

data class TopicStrength(
    val name: String,
    val level: TopicStrengthLevel,
    val scorePercent: Int,
)

data class ExamReadinessRecommendations(
    val cardsToReview: Int,
    val quizQuestionCount: Int,
    val reviewMinutes: Int,
)

enum class ExamReadinessMode { AUTO, MANUAL, UNLINKED }

enum class ExamReadinessStatus {
    UNLINKED,
    EMPTY_DECK,
    READY,
}

data class ExamReadiness(
    val examId: String,
    val status: ExamReadinessStatus,
    val mode: ExamReadinessMode,
    val readinessPercent: Int? = null,
    val strongTopics: List<TopicStrength> = emptyList(),
    val moderateTopics: List<TopicStrength> = emptyList(),
    val weakTopics: List<TopicStrength> = emptyList(),
    val recommendations: ExamReadinessRecommendations? = null,
    val linkedDeckId: String? = null,
    val linkedDeckTitle: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val totalCards: Int = 0,
    val dueCards: Int = 0,
) {
    val hasBreakdown: Boolean
        get() = status == ExamReadinessStatus.READY &&
            (strongTopics.isNotEmpty() || moderateTopics.isNotEmpty() || weakTopics.isNotEmpty())
}

const val EXAM_READINESS_DISCLAIMER =
    "Study progress estimate — not a grade prediction"
