package com.edukasyon.studentai.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.share.ShareDocument
import com.edukasyon.studentai.core.share.SharePayload
import com.edukasyon.studentai.core.share.ShareRepository
import com.edukasyon.studentai.core.share.SharedCard
import com.edukasyon.studentai.core.share.SharedClass
import com.edukasyon.studentai.domain.repository.JeviRepository
import com.edukasyon.studentai.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the publish sheet is packaging. Kept as plain data so the sheet can re-run it. */
sealed interface ShareTarget {
    data object Schedule : ShareTarget
    data class Deck(val deckId: String) : ShareTarget
}

data class SharePublishState(
    val generating: Boolean = false,
    val code: String? = null,
    val envelopeJson: String? = null,
    val error: String? = null,
    /** "14 classes" / "48 cards" — shown in the sheet once packaged. */
    val contentSummary: String? = null,
)

/**
 * Gathers schedule/deck content, wraps it in a [SharePayload]-encoded envelope and
 * publishes it through [ShareRepository.publish], exposing the 6-char code.
 *
 * What lands in Firestore `shares/{code}.payloadJson` is the *encoded envelope*
 * (SharePayload.encodeEnvelope(envelopeFor(kind, innerJson))) — that is exactly
 * what `SharePayload.parse(doc.payloadJson)` on the redeem side expects, mirroring
 * SharePayloadTest's round-trip usage.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
    private val scheduleRepository: ScheduleRepository,
    private val jeviRepository: JeviRepository,
    private val authManager: FirebaseAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharePublishState())
    val uiState: StateFlow<SharePublishState> = _uiState.asStateFlow()

    private var lastTarget: ShareTarget? = null

    fun publish(target: ShareTarget) {
        // Reopening the sheet for the same target keeps the existing code live
        // instead of minting a second share behind the user's back.
        if (lastTarget == target && _uiState.value.code != null) return
        lastTarget = target
        retry()
    }

    fun retry() {
        val target = lastTarget ?: return
        if (_uiState.value.generating) return
        viewModelScope.launch {
            _uiState.update { SharePublishState(generating = true) }
            try {
                when (target) {
                    ShareTarget.Schedule -> publishSchedule()
                    is ShareTarget.Deck -> publishDeck(target.deckId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(generating = false, error = e.message ?: "Couldn't prepare the share") }
            }
        }
    }

    private suspend fun publishSchedule() {
        val items = scheduleRepository.observeSchedule().first()
        if (items.isEmpty()) {
            _uiState.update { it.copy(generating = false, error = "Add classes to your schedule before sharing it.") }
            return
        }
        if (items.size > SharePayload.MAX_CLASSES) {
            _uiState.update { it.copy(generating = false, error = "Your timetable is too large to share (${items.size} classes).") }
            return
        }
        val shared = items.map { item ->
            SharedClass(
                subject = item.subjectName,
                teacher = item.teacher,
                room = item.room,
                day = item.dayOfWeek.name,
                startTime = normalizeTime(item.startTime),
                endTime = normalizeTime(item.endTime),
            )
        }
        finishPublish(
            kind = SharePayload.KIND_SCHEDULE,
            innerJson = SharePayload.encodeSchedule(shared),
            summary = "${shared.size} ${if (shared.size == 1) "class" else "classes"}",
        )
    }

    private suspend fun publishDeck(deckId: String) {
        val deck = jeviRepository.observeDeck(deckId).first()
        if (deck == null) {
            _uiState.update { it.copy(generating = false, error = "That deck no longer exists.") }
            return
        }
        val cards = jeviRepository.observeDeckFlashcards(deckId).first()
        if (cards.isEmpty()) {
            _uiState.update { it.copy(generating = false, error = "This deck has no cards to share yet.") }
            return
        }
        if (cards.size > SharePayload.MAX_CARDS) {
            _uiState.update { it.copy(generating = false, error = "This deck is too large to share (${cards.size} cards).") }
            return
        }
        // Strip ALL review state — the recipient starts these cards fresh.
        val shared = cards.map { card ->
            SharedCard(question = card.question, answer = card.answer, topic = card.topic)
        }
        finishPublish(
            kind = SharePayload.KIND_DECK,
            innerJson = SharePayload.encodeDeck(deck.title, deck.description, deck.colorHex, shared),
            summary = "${shared.size} ${if (shared.size == 1) "card" else "cards"}",
        )
    }

    private suspend fun finishPublish(kind: String, innerJson: String, summary: String) {
        val envelopeJson = SharePayload.encodeEnvelope(SharePayload.envelopeFor(kind, innerJson))
        val doc = ShareDocument.new(kind, envelopeJson, createdBy = authManager.currentUserId)
        shareRepository.publish(doc)
            .onSuccess { code ->
                _uiState.update {
                    it.copy(generating = false, code = code, envelopeJson = envelopeJson, error = null, contentSummary = summary)
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(generating = false, error = e.message ?: "Couldn't publish the share.") }
            }
    }

    companion object {
        /** Entity times are already "HH:mm" strings; stay defensive about the few legacy "9:00" entries. */
        fun normalizeTime(raw: String): String {
            val trimmed = raw.trim()
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                val hour = parts[0].toIntOrNull()
                val minute = parts[1].toIntOrNull()
                if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                    return String.format(Locale.US, "%02d:%02d", hour, minute)
                }
            }
            return trimmed
        }
    }
}
