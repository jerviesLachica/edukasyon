package com.edukasyon.studentai.domain.model

/**
 * Today's Battle Plan: single structured answer to "what do I do now?"
 * Composed from schedule + tasks + exams + exam readiness. Offline-first.
 */
data class BattlePlan(
    val nextClass: ScheduleItem? = null,
    val classesToday: Int = 0,
    val overdueTasks: List<Task> = emptyList(),
    val dueTodayTasks: List<Task> = emptyList(),
    val nextExam: Exam? = null,
    val weakestReadiness: List<ExamReadiness> = emptyList(),
    val dueFlashcardsCount: Int = 0,
    val suggestedAction: SuggestedAction = SuggestedAction.REVIEW_DUE_CARDS,
    val headline: String = "",
)

enum class SuggestedAction {
    ATTEND_NEXT_CLASS,
    CLEAR_OVERDUE,
    REVIEW_DUE_CARDS,
    START_FOCUS_WEAK_SUBJECT,
    PREPARE_NEXT_EXAM,
    PLAN_DAY,
}
