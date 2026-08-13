package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.JeviDashboard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.JeviReviewRecord
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.UserProfile
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

interface FlashcardRepository {
    fun observeFlashcards(): Flow<List<Flashcard>>
    fun observeDueFlashcards(): Flow<List<Flashcard>>
    suspend fun saveFlashcards(cards: List<Flashcard>)
    suspend fun updateFlashcard(card: Flashcard)
}

interface QuizRepository {
    fun observeAll(): Flow<List<Quiz>>
    fun observeByDeck(deckId: String): Flow<List<Quiz>>
    suspend fun getQuiz(quizId: String): Quiz?
    suspend fun saveQuiz(quiz: Quiz)
}

interface GradeRepository {
    fun observeGrades(subjectId: String? = null): Flow<List<GradeEntry>>
    suspend fun saveGrade(entry: GradeEntry)
    suspend fun deleteGrade(id: String)
    fun calculateWeightedGrade(entries: List<GradeEntry>): Double
}

interface JeviRepository {
    fun observeDashboard(): Flow<JeviDashboard>
    fun observeDecks(): Flow<List<JeviDeck>>
    fun observeDeck(deckId: String): Flow<JeviDeck?>
    suspend fun createDeck(deck: JeviDeck): String
    suspend fun updateDeck(deck: JeviDeck)
    suspend fun deleteDeck(deckId: String)
    fun observeDeckFlashcards(deckId: String): Flow<List<Flashcard>>
    fun observeDueFlashcards(deckId: String? = null): Flow<List<Flashcard>>
    suspend fun saveFlashcardsToDeck(deckId: String, cards: List<Flashcard>)
    suspend fun recordReview(record: JeviReviewRecord)
    suspend fun ensureDefaultDeck()
}
