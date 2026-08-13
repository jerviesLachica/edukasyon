package com.edukasyon.studentai.data.repository

import com.edukasyon.studentai.core.gamification.GizmoGamificationManager
import com.edukasyon.studentai.data.local.dao.FlashcardDao
import com.edukasyon.studentai.data.local.dao.JeviDeckDao
import com.edukasyon.studentai.data.local.dao.JeviReviewRecordDao
import com.edukasyon.studentai.data.local.dao.QuizDao
import com.edukasyon.studentai.data.local.entity.FlashcardEntity
import com.edukasyon.studentai.data.mapper.toDomain
import com.edukasyon.studentai.data.mapper.toEntity
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviConstants
import com.edukasyon.studentai.domain.model.JeviDashboard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.JeviReviewRecord
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.repository.JeviRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JeviRepositoryImpl @Inject constructor(
    private val deckDao: JeviDeckDao,
    private val flashcardDao: FlashcardDao,
    private val reviewRecordDao: JeviReviewRecordDao,
    private val quizDao: QuizDao,
    private val gizmoManager: GizmoGamificationManager,
) : JeviRepository {

    override suspend fun ensureDefaultDeck() {
        val existing = deckDao.getById(JeviConstants.DEFAULT_DECK_ID)
        if (existing == null) {
            val now = System.currentTimeMillis()
            deckDao.insert(
                JeviDeck(
                    id = JeviConstants.DEFAULT_DECK_ID,
                    title = JeviConstants.DEFAULT_DECK_TITLE,
                    description = "Default deck for flashcards",
                    subjectId = null,
                    sourceNoteId = null,
                    colorHex = JeviConstants.DEFAULT_DECK_COLOR,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(now)
            )
        }
        flashcardDao.assignOrphansToDeck(JeviConstants.DEFAULT_DECK_ID, System.currentTimeMillis())
    }

    override fun observeDashboard(): Flow<JeviDashboard> {
        val now = System.currentTimeMillis()
        return combine(
            flashcardDao.observeDueCount(now),
            flashcardDao.observeTotalCount(),
            deckDao.observeAll(),
            quizDao.observeAll().map { it.size },
            gizmoManager.state,
        ) { dueCount, totalCards, deckEntities, quizCount, gizmo ->
            JeviDashboard(
                dueCount = dueCount,
                totalCards = totalCards,
                deckCount = deckEntities.size,
                quizCount = quizCount,
                streakDays = gizmo.streakDays,
                xp = gizmo.xp,
                level = gizmo.level,
                xpProgress = gizmo.xpProgress,
                decks = deckEntities.map { it.toDomain() },
            )
        }
    }

    override fun observeDecks(): Flow<List<JeviDeck>> {
        val now = System.currentTimeMillis()
        return deckDao.observeAll().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val deckFlows = entities.map { entity ->
                    combine(
                        deckDao.observeCardCount(entity.id),
                        deckDao.observeDueCount(entity.id, now),
                        deckDao.observeMasteredCount(entity.id),
                    ) { cardCount, dueCount, masteredCount ->
                        entity.toDomain(cardCount, dueCount, masteredCount)
                    }
                }
                combine(deckFlows) { decks -> decks.toList() }
            }
        }
    }

    override fun observeDeck(deckId: String): Flow<JeviDeck?> {
        val now = System.currentTimeMillis()
        return combine(
            deckDao.observeById(deckId),
            deckDao.observeCardCount(deckId),
            deckDao.observeDueCount(deckId, now),
            deckDao.observeMasteredCount(deckId),
        ) { entity, cardCount, dueCount, masteredCount ->
            entity?.toDomain(cardCount, dueCount, masteredCount)
        }
    }

    override suspend fun createDeck(deck: JeviDeck): String {
        val now = System.currentTimeMillis()
        deckDao.insert(deck.toEntity(now))
        return deck.id
    }

    override suspend fun updateDeck(deck: JeviDeck) {
        deckDao.insert(deck.toEntity())
    }

    override suspend fun deleteDeck(deckId: String) {
        if (deckId == JeviConstants.DEFAULT_DECK_ID) return
        val now = System.currentTimeMillis()
        deckDao.softDelete(deckId, now, now)
    }

    override fun observeDeckFlashcards(deckId: String): Flow<List<Flashcard>> =
        flashcardDao.observeByDeck(deckId).map { list -> list.map { it.toDomain() } }

    override fun observeDueFlashcards(deckId: String?): Flow<List<Flashcard>> {
        val now = System.currentTimeMillis()
        return if (deckId == null) {
            flashcardDao.observeDue(now).map { list -> list.map { it.toDomain() } }
        } else {
            flashcardDao.observeDueByDeck(deckId, now).map { list -> list.map { it.toDomain() } }
        }
    }

    override suspend fun saveFlashcardsToDeck(deckId: String, cards: List<Flashcard>) {
        ensureDefaultDeck()
        val now = System.currentTimeMillis()
        cards.forEach { card ->
            flashcardDao.insert(
                FlashcardEntity(
                    id = card.id,
                    question = card.question,
                    answer = card.answer,
                    subjectId = card.subjectId,
                    deckId = deckId,
                    topic = card.topic,
                    difficulty = card.difficulty,
                    reviewCount = card.reviewCount,
                    correctCount = card.correctCount,
                    incorrectCount = card.incorrectCount,
                    lastReviewedAt = card.lastReviewedAt,
                    nextReviewAt = card.nextReviewAt ?: now,
                    easeFactor = card.easeFactor,
                    intervalDays = card.intervalDays,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    syncState = SyncState.LOCAL_ONLY.name,
                )
            )
        }
        deckDao.getById(deckId)?.let { existing ->
            deckDao.insert(existing.copy(updatedAt = now))
        }
    }

    override suspend fun recordReview(record: JeviReviewRecord) {
        reviewRecordDao.insert(record.toEntity())
    }
}
