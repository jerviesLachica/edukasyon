package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDashboard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.JeviReviewRecord
import kotlinx.coroutines.flow.Flow

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
