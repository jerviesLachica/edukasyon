package com.edukasyon.studentai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.edukasyon.studentai.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity)

    @Query("UPDATE subjects SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM subjects WHERE deletedAt IS NULL AND (name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects")
    suspend fun getAllForSync(): List<SubjectEntity>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_items WHERE deletedAt IS NULL ORDER BY dayOfWeek, startTime")
    fun observeAll(): Flow<List<ScheduleItemEntity>>

    @Query("SELECT * FROM schedule_items WHERE deletedAt IS NULL AND dayOfWeek = :day ORDER BY startTime")
    fun observeByDay(day: String): Flow<List<ScheduleItemEntity>>

    @Query("SELECT * FROM schedule_items WHERE deletedAt IS NULL AND dayOfWeek = :day ORDER BY startTime")
    suspend fun getByDay(day: String): List<ScheduleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleItemEntity)

    @Query("UPDATE schedule_items SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE schedule_items SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM schedule_items WHERE deletedAt IS NULL AND (subjectName LIKE '%' || :query || '%' OR teacher LIKE '%' || :query || '%' OR room LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<ScheduleItemEntity>>

    @Query("SELECT * FROM schedule_items")
    suspend fun getAllForSync(): List<ScheduleItemEntity>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY CASE status WHEN 'COMPLETED' THEN 1 ELSE 0 END, dueDate ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND status != 'COMPLETED' AND status != 'ARCHIVED' ORDER BY dueDate ASC LIMIT :limit")
    fun observeUpcoming(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND status != 'COMPLETED' AND status != 'ARCHIVED' ORDER BY dueDate ASC LIMIT :limit")
    suspend fun getUpcoming(limit: Int): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND status != 'COMPLETED' AND status != 'ARCHIVED' AND dueDate >= :from AND dueDate <= :to")
    suspend fun getDueInRange(from: Long, to: Long): List<TaskEntity>

    @Upsert
    suspend fun insert(task: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND title LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllForSync(): List<TaskEntity>
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder")
    fun observeByTask(taskId: String): Flow<List<SubtaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    @Query("SELECT * FROM subtasks")
    suspend fun getAllForSync(): List<SubtaskEntity>
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments WHERE deletedAt IS NULL ORDER BY dueDate ASC")
    fun observeAll(): Flow<List<AssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: AssignmentEntity)

    @Query("UPDATE assignments SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM assignments WHERE deletedAt IS NULL AND title LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments")
    suspend fun getAllForSync(): List<AssignmentEntity>
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE deletedAt IS NULL ORDER BY examDate ASC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE deletedAt IS NULL AND examDate >= :from ORDER BY examDate ASC LIMIT :limit")
    fun observeUpcoming(from: Long, limit: Int): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity)

    @Query("UPDATE exams SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE exams SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM exams WHERE deletedAt IS NULL AND title LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams")
    suspend fun getAllForSync(): List<ExamEntity>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllForSync(): List<NoteEntity>
}

@Dao
interface NoteTagDao {
    @Query("SELECT * FROM note_tags WHERE noteId = :noteId")
    fun observeByNote(noteId: String): Flow<List<NoteTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: NoteTagEntity)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: String)

    @Query("SELECT * FROM note_tags")
    suspend fun getAllForSync(): List<NoteTagEntity>
}

@Dao
interface JeviDeckDao {
    @Query("SELECT * FROM jevi_decks WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JeviDeckEntity>>

    @Query("SELECT * FROM jevi_decks WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeById(id: String): Flow<JeviDeckEntity?>

    @Query("SELECT * FROM jevi_decks WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): JeviDeckEntity?

    @Query(
        """SELECT * FROM jevi_decks
            WHERE deletedAt IS NULL AND subjectId = :subjectId
            ORDER BY updatedAt DESC LIMIT 1"""
    )
    suspend fun getFirstBySubject(subjectId: String): JeviDeckEntity?

    @Query(
        """SELECT * FROM jevi_decks
            WHERE deletedAt IS NULL AND subjectId = :subjectId
            ORDER BY updatedAt DESC LIMIT 1"""
    )
    fun observeFirstBySubject(subjectId: String): Flow<JeviDeckEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deck: JeviDeckEntity)

    @Query("UPDATE jevi_decks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM flashcards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun observeCardCount(deckId: String): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM flashcards
            WHERE deckId = :deckId AND deletedAt IS NULL
            AND (nextReviewAt IS NULL OR nextReviewAt <= :now)"""
    )
    fun observeDueCount(deckId: String, now: Long): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM flashcards
            WHERE deckId = :deckId AND deletedAt IS NULL
            AND reviewCount >= 3 AND intervalDays >= 21"""
    )
    fun observeMasteredCount(deckId: String): Flow<Int>

    @Query("SELECT * FROM jevi_decks")
    suspend fun getAllForSync(): List<JeviDeckEntity>
}

@Dao
interface JeviReviewRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: JeviReviewRecordEntity)

    @Query("SELECT * FROM jevi_review_records WHERE flashcardId = :flashcardId ORDER BY reviewedAt DESC")
    fun observeByFlashcard(flashcardId: String): Flow<List<JeviReviewRecordEntity>>

    @Query("SELECT COUNT(*) FROM jevi_review_records WHERE reviewedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT * FROM jevi_review_records")
    suspend fun getAllForSync(): List<JeviReviewRecordEntity>
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL ORDER BY nextReviewAt ASC")
    fun observeAll(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL AND (nextReviewAt IS NULL OR nextReviewAt <= :now) ORDER BY nextReviewAt ASC")
    fun observeDue(now: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL AND deckId = :deckId ORDER BY nextReviewAt ASC")
    fun observeByDeck(deckId: String): Flow<List<FlashcardEntity>>

    @Query(
        """SELECT * FROM flashcards WHERE deletedAt IS NULL AND deckId = :deckId
            AND (nextReviewAt IS NULL OR nextReviewAt <= :now) ORDER BY nextReviewAt ASC"""
    )
    fun observeDueByDeck(deckId: String, now: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE deletedAt IS NULL")
    fun observeTotalCount(): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM flashcards WHERE deletedAt IS NULL
            AND (nextReviewAt IS NULL OR nextReviewAt <= :now)"""
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flashcard: FlashcardEntity)

    @Query("UPDATE flashcards SET deckId = :deckId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun assignDeck(id: String, deckId: String, updatedAt: Long)

    @Query("UPDATE flashcards SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE flashcards SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("UPDATE flashcards SET deckId = :deckId, updatedAt = :updatedAt WHERE deckId IS NULL AND deletedAt IS NULL")
    suspend fun assignOrphansToDeck(deckId: String, updatedAt: Long)

    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL AND question LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards")
    suspend fun getAllForSync(): List<FlashcardEntity>
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quizzes WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :quizId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(quizId: String): QuizEntity?

    @Query(
        "SELECT * FROM quizzes WHERE deletedAt IS NULL AND sourceNoteId = :deckSource ORDER BY createdAt DESC"
    )
    fun observeByDeckSource(deckSource: String): Flow<List<QuizEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quiz: QuizEntity)
}

@Dao
interface QuizQuestionDao {
    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId")
    suspend fun getByQuiz(quizId: String): List<QuizQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuizQuestionEntity)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity)
}

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plans WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StudyPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: StudyPlanEntity)
}

@Dao
interface StudyPlanItemDao {
    @Query("SELECT * FROM study_plan_items WHERE planId = :planId ORDER BY dayOfWeek, startTime")
    suspend fun getByPlan(planId: String): List<StudyPlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StudyPlanItemEntity)

    @Query("DELETE FROM study_plan_items WHERE planId = :planId")
    suspend fun deleteByPlan(planId: String)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL AND startAt >= :from AND startAt <= :to ORDER BY startAt")
    fun observeInRange(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL AND startAt >= :from AND startAt <= :to ORDER BY startAt")
    suspend fun getInRange(from: Long, to: Long): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity)
}

@Dao
interface GradeEntryDao {
    @Query("SELECT * FROM grade_entries WHERE deletedAt IS NULL AND subjectId = :subjectId ORDER BY createdAt DESC")
    fun observeBySubject(subjectId: String): Flow<List<GradeEntryEntity>>

    @Query("SELECT * FROM grade_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GradeEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GradeEntryEntity)

    @Query("UPDATE grade_entries SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE grade_entries SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM grade_entries")
    suspend fun getAllForSync(): List<GradeEntryEntity>
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata")
    fun observeAll(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_metadata WHERE entityType = :entityType LIMIT 1")
    fun observeByType(entityType: String): Flow<SyncMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)
}

@Dao
interface AiConversationDao {
    @Query(
        "SELECT * FROM conversations WHERE conversationType IN (:types) ORDER BY updatedAt DESC"
    )
    fun observeByTypes(types: List<String>): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchUpdatedAt(id: String, updatedAt: Long)

    @Query(
        "UPDATE conversations SET backendConversationId = :backendId, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateBackendConversationId(id: String, backendId: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
}

@Dao
interface CachedHolidayDao {
    @Query("SELECT * FROM ph_holidays_cache WHERE date >= :fromDate AND date < :toDate ORDER BY date")
    suspend fun getByDateRange(fromDate: String, toDate: String): List<CachedHolidayEntity>

    @Query("SELECT * FROM ph_holidays_cache WHERE year = :year ORDER BY date")
    suspend fun getByYear(year: Int): List<CachedHolidayEntity>

    @Query("SELECT MAX(fetchedAt) FROM ph_holidays_cache WHERE year = :year")
    suspend fun getLatestFetchedAtForYear(year: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(holidays: List<CachedHolidayEntity>)

    @Query("DELETE FROM ph_holidays_cache WHERE year = :year")
    suspend fun deleteByYear(year: Int)
}

@Dao
interface LectureFileDao {
    @Query("SELECT * FROM lecture_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LectureFileEntity>>

    @Query("SELECT * FROM lecture_files WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun observeBySubject(subjectId: String): Flow<List<LectureFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: LectureFileEntity)

    @Query("DELETE FROM lecture_files WHERE id = :id")
    suspend fun deleteById(id: String)
}
