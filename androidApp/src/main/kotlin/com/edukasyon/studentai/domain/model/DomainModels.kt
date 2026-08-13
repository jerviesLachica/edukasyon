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

enum class PreferredStudentStatus(val displayName: String) {
    STUDENT("Student"),
    WORKING_STUDENT("Working student"),
    GRADUATE("Graduate student"),
    HIGH_SCHOOL("High school student"),
    OTHER("Other");

    companion object {
        val options: List<PreferredStudentStatus> = entries

        fun fromStored(value: String?): PreferredStudentStatus? {
            if (value.isNullOrBlank()) return null
            return entries.find { it.name == value || it.displayName.equals(value, ignoreCase = true) }
        }
    }
}

data class Subject(
    val id: String,
    val name: String,
    val code: String?,
    val teacher: String?,
    val colorHex: String,
    val semester: String,
    val schoolYear: String
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
    val isRecurring: Boolean = true
)

data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val subjectId: String?,
    val priority: Priority,
    val dueDate: Long?,
    val dueTime: String?,
    val status: TaskStatus,
    val category: String?,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val subtasks: List<Subtask> = emptyList()
)

data class Subtask(
    val id: String,
    val taskId: String,
    val title: String,
    val isCompleted: Boolean,
    val sortOrder: Int
)

data class Assignment(
    val id: String,
    val title: String,
    val subjectId: String?,
    val description: String?,
    val dueDate: Long?,
    val dueTime: String?,
    val attachmentUri: String?,
    val priority: Priority,
    val status: TaskStatus,
    val grade: String?,
    val notes: String?,
    val reminderAt: Long?
)

data class Exam(
    val id: String,
    val title: String,
    val subjectId: String?,
    val linkedDeckId: String? = null,
    val examDate: Long,
    val examTime: String?,
    val location: String?,
    val coverage: String?,
    val notes: String?,
    val reminderAt: Long?
)

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val subjectId: String?,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val isFavorite: Boolean
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
    val intervalDays: Int = 1
)

data class Quiz(
    val id: String,
    val title: String,
    val subjectId: String?,
    val sourceNoteId: String?,
    val questions: List<QuizQuestion>,
    val createdAt: Long
)

data class QuizQuestion(
    val id: String,
    val quizId: String,
    val type: QuestionType,
    val question: String,
    val options: List<String>,
    val correctAnswer: String
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
    val priority: Priority
)

data class StudyPlan(
    val id: String,
    val title: String,
    val examId: String?,
    val items: List<StudyPlanItem>,
    val createdAt: Long
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startAt: Long,
    val endAt: Long,
    val type: String,
    val referenceId: String?,
    val colorHex: String
)

data class GradeEntry(
    val id: String,
    val subjectId: String,
    val assessment: String,
    val category: String,
    val score: Double,
    val maxScore: Double,
    val weight: Double,
    val term: String
)

data class GradeCategory(
    val name: String,
    val weight: Double
)

data class StudentContext(
    val currentSubjects: List<Subject>,
    val upcomingClasses: List<ScheduleItem>,
    val upcomingTasks: List<Task>,
    val upcomingExams: List<Exam>,
    val relevantNotes: List<Note>
)

data class LectureFile(
    val id: String,
    val subjectId: String?,
    val title: String,
    val fileUri: String,
    val mimeType: String,
    val createdAt: Long
)
