package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.*
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUser(): Flow<UserProfile?>
    suspend fun saveUser(user: UserProfile)
}

interface ScheduleRepository {
    fun observeSchedule(): Flow<List<ScheduleItem>>
    fun observeByDay(day: DayOfWeek): Flow<List<ScheduleItem>>
    suspend fun addScheduleItem(item: ScheduleItem)
    suspend fun updateScheduleItem(item: ScheduleItem)
    suspend fun deleteScheduleItem(id: String)
    fun search(query: String): Flow<List<ScheduleItem>>
}

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    fun observeUpcoming(limit: Int = 5): Flow<List<Task>>
    suspend fun createTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun completeTask(id: String)
    suspend fun deleteTask(id: String)
    fun search(query: String): Flow<List<Task>>
}

interface AssignmentRepository {
    fun observeAssignments(): Flow<List<Assignment>>
    suspend fun saveAssignment(assignment: Assignment)
    suspend fun deleteAssignment(id: String)
}

interface ExamRepository {
    fun observeExams(): Flow<List<Exam>>
    fun observeUpcoming(limit: Int = 5): Flow<List<Exam>>
    suspend fun saveExam(exam: Exam)
    suspend fun deleteExam(id: String)
}

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun saveNote(note: Note)
    suspend fun deleteNote(id: String)
    fun search(query: String): Flow<List<Note>>
}

interface GradeRepository {
    fun observeGrades(subjectId: String? = null): Flow<List<GradeEntry>>
    suspend fun saveGrade(entry: GradeEntry)
    suspend fun deleteGrade(id: String)
    fun calculateWeightedGrade(entries: List<GradeEntry>): Double
}

interface SubjectRepository {
    fun observeSubjects(): Flow<List<Subject>>
    suspend fun saveSubject(subject: Subject)
}

interface CalendarRepository {
    fun observeEvents(from: Long, to: Long): Flow<List<CalendarEvent>>
    suspend fun saveEvent(event: CalendarEvent)
}

interface FlashcardRepository {
    fun observeFlashcards(): Flow<List<Flashcard>>
    fun observeDueFlashcards(): Flow<List<Flashcard>>
    suspend fun saveFlashcards(cards: List<Flashcard>)
    suspend fun updateFlashcard(card: Flashcard)
}

interface QuizRepository {
    suspend fun saveQuiz(quiz: Quiz)
}

interface SearchRepository {
    fun globalSearch(query: String): Flow<Map<String, List<String>>>
}
