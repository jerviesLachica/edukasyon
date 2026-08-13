package com.edukasyon.studentai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String?,
    val school: String,
    val gradeLevel: String,
    val section: String,
    val schoolYear: String,
    val semester: String,
    val isGuest: Boolean,
    val avatarUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String?,
    val teacher: String?,
    val colorHex: String,
    val semester: String,
    val schoolYear: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "schedule_items")
data class ScheduleItemEntity(
    @PrimaryKey val id: String,
    val subjectId: String?,
    val subjectName: String,
    val teacher: String?,
    val room: String?,
    val building: String?,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val colorHex: String,
    val notes: String?,
    val semester: String,
    val schoolYear: String,
    val isRecurring: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val subjectId: String?,
    val priority: String,
    val dueDate: Long?,
    val dueTime: String?,
    val status: String,
    val category: String?,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isCompleted: Boolean,
    val sortOrder: Int
)

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subjectId: String?,
    val description: String?,
    val dueDate: Long?,
    val dueTime: String?,
    val attachmentUri: String?,
    val priority: String,
    val status: String,
    val grade: String?,
    val notes: String?,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subjectId: String?,
    val examDate: Long,
    val examTime: String?,
    val location: String?,
    val coverage: String?,
    val notes: String?,
    val reminderAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val subjectId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(
    tableName = "note_tags",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("tag")]
)
data class NoteTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val tag: String
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val question: String,
    val answer: String,
    val subjectId: String?,
    val topic: String?,
    val difficulty: String,
    val reviewCount: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subjectId: String?,
    val sourceNoteId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuizQuestionEntity(
    @PrimaryKey val id: String,
    val quizId: String,
    val type: String,
    val question: String,
    val optionsJson: String,
    val correctAnswer: String
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val subjectId: String?,
    val topic: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMinutes: Int,
    val notes: String?,
    val createdAt: Long,
    val syncState: String
)

@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val examId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(
    tableName = "study_plan_items",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class StudyPlanItemEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val subjectName: String,
    val topic: String,
    val activity: String,
    val priority: String
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val startAt: Long,
    val endAt: Long,
    val type: String,
    val referenceId: String?,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "grade_entries")
data class GradeEntryEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val assessment: String,
    val category: String,
    val score: Double,
    val maxScore: Double,
    val weight: Double,
    val term: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val message: String,
    val scheduledAt: Long,
    val referenceId: String?,
    val isEnabled: Boolean,
    val createdAt: Long
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val entityType: String,
    val lastSyncedAt: Long,
    val pendingCount: Int,
    val failedCount: Int,
    val status: String
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isGroup: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String,
    val conversationType: String? = null,
    val backendConversationId: String? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val sentAt: Long,
    val isRead: Boolean,
    val syncState: String,
    val attachmentName: String? = null,
    val attachmentIsImage: Boolean = false,
    val metadataJson: String? = null,
)

@Entity(
    tableName = "ph_holidays_cache",
    indices = [Index(value = ["year"])]
)
data class CachedHolidayEntity(
    @PrimaryKey val date: String,
    val name: String,
    val localName: String,
    val type: String,
    val year: Int,
    val fetchedAt: Long
)

@Entity(
    tableName = "lecture_files",
    indices = [Index("subjectId"), Index("createdAt")]
)
data class LectureFileEntity(
    @PrimaryKey val id: String,
    val subjectId: String?,
    val title: String,
    val fileUri: String,
    val mimeType: String,
    val createdAt: Long
)
