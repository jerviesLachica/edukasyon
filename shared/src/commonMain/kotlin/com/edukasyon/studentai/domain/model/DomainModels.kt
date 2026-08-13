package com.edukasyon.studentai.domain.model

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String?,
    val school: String,
    val gradeLevel: String,
    val section: String,
    val schoolYear: String,
    val semester: String,
    val isGuest: Boolean,
    val avatarUri: String? = null,
    val bio: String = "",
    val preferredStatus: String = "",
    val lastProfileEditAt: Long? = null,
)

data class Subject(
    val id: String,
    val name: String,
    val code: String?,
    val teacher: String?,
    val colorHex: String,
    val semester: String,
    val schoolYear: String,
)

data class ScheduleItem(
    val id: String,
    val subjectId: String?,
    val subjectName: String,
    val teacher: String?,
    val room: String?,
    val building: String?,
    val dayOfWeek: DayOfWeek,
    val startTime: String,
    val endTime: String,
    val colorHex: String,
    val notes: String?,
    val semester: String,
    val schoolYear: String,
    val isRecurring: Boolean = true,
)

data class Flashcard(
    val id: String,
    val question: String,
    val answer: String,
    val subjectId: String?,
    val deckId: String? = null,
    val topic: String?,
    val difficulty: String,
    val reviewCount: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 1,
)

data class Quiz(
    val id: String,
    val title: String,
    val subjectId: String?,
    val sourceNoteId: String?,
    val questions: List<QuizQuestion>,
    val createdAt: Long,
)

data class QuizQuestion(
    val id: String,
    val quizId: String,
    val type: QuestionType,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
) {
    fun isAnswerCorrect(selected: String): Boolean {
        val normalizedSelected = selected.trim()
        val normalizedCorrect = correctAnswer.trim()
        if (normalizedSelected.equals(normalizedCorrect, ignoreCase = true)) return true
        val letterIndex = normalizedCorrect.uppercase().singleOrNull()?.let { letter ->
            if (letter in 'A'..'Z') letter - 'A' else null
        }
        if (letterIndex != null && letterIndex in options.indices) {
            return normalizedSelected.equals(options[letterIndex].trim(), ignoreCase = true)
        }
        return false
    }
}

data class StudyPlanItem(
    val id: String,
    val planId: String,
    val dayOfWeek: DayOfWeek,
    val startTime: String,
    val endTime: String,
    val subjectName: String,
    val topic: String,
    val activity: String,
    val priority: Priority,
)

data class StudyPlan(
    val id: String,
    val title: String,
    val examId: String?,
    val items: List<StudyPlanItem>,
    val createdAt: Long,
)

data class GradeEntry(
    val id: String,
    val subjectId: String,
    val assessment: String,
    val category: String,
    val score: Double,
    val maxScore: Double,
    val weight: Double,
    val term: String,
)
