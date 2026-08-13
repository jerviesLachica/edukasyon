package com.edukasyon.studentai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.edukasyon.studentai.data.local.dao.*
import com.edukasyon.studentai.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        SubjectEntity::class,
        ScheduleItemEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        AssignmentEntity::class,
        ExamEntity::class,
        NoteEntity::class,
        NoteTagEntity::class,
        FlashcardEntity::class,
        JeviDeckEntity::class,
        JeviReviewRecordEntity::class,
        QuizEntity::class,
        QuizQuestionEntity::class,
        StudySessionEntity::class,
        StudyPlanEntity::class,
        StudyPlanItemEntity::class,
        CalendarEventEntity::class,
        GradeEntryEntity::class,
        NotificationEntity::class,
        SyncMetadataEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        CachedHolidayEntity::class,
        LectureFileEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class StudentAiDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun examDao(): ExamDao
    abstract fun noteDao(): NoteDao
    abstract fun noteTagDao(): NoteTagDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun jeviDeckDao(): JeviDeckDao
    abstract fun jeviReviewRecordDao(): JeviReviewRecordDao
    abstract fun quizDao(): QuizDao
    abstract fun quizQuestionDao(): QuizQuestionDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun studyPlanItemDao(): StudyPlanItemDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun gradeEntryDao(): GradeEntryDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun aiConversationDao(): AiConversationDao
    abstract fun cachedHolidayDao(): CachedHolidayDao
    abstract fun lectureFileDao(): LectureFileDao
}
