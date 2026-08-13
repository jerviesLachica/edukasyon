package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDashboard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.repository.JeviRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetJeviDashboardUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    operator fun invoke(): Flow<JeviDashboard> = jeviRepository.observeDashboard()
}

class GetJeviDecksUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    operator fun invoke(): Flow<List<JeviDeck>> = jeviRepository.observeDecks()
}

class CreateJeviDeckUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    suspend operator fun invoke(deck: JeviDeck): String {
        jeviRepository.ensureDefaultDeck()
        return jeviRepository.createDeck(deck)
    }
}

class SaveFlashcardsToDeckUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    suspend operator fun invoke(deckId: String, cards: List<Flashcard>) {
        jeviRepository.ensureDefaultDeck()
        jeviRepository.saveFlashcardsToDeck(deckId, cards)
    }
}

class GetDueFlashcardsUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    operator fun invoke(deckId: String? = null): Flow<List<Flashcard>> =
        jeviRepository.observeDueFlashcards(deckId)
}

class EnsureJeviDefaultDeckUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    suspend operator fun invoke() = jeviRepository.ensureDefaultDeck()
}

class GetJeviDeckUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    operator fun invoke(deckId: String): Flow<JeviDeck?> = jeviRepository.observeDeck(deckId)
}

class GetDeckFlashcardsUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    operator fun invoke(deckId: String): Flow<List<Flashcard>> =
        jeviRepository.observeDeckFlashcards(deckId)
}

class DeleteJeviDeckUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    suspend operator fun invoke(deckId: String) = jeviRepository.deleteDeck(deckId)
}

class RecordJeviReviewUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
) {
    suspend operator fun invoke(record: com.edukasyon.studentai.domain.model.JeviReviewRecord) =
        jeviRepository.recordReview(record)
}
