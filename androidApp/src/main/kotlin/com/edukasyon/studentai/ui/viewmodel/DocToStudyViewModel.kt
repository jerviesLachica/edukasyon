package com.edukasyon.studentai.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.document.DocumentPipeline
import com.edukasyon.studentai.core.document.PageStatus
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.core.gamification.GizmoGamificationManager
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.domain.model.JeviConstants
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.withDeckId
import com.edukasyon.studentai.domain.usecase.AiGenerateFlashcardsUseCase
import com.edukasyon.studentai.domain.usecase.AiGenerateQuizUseCase
import com.edukasyon.studentai.domain.usecase.EnsureJeviDefaultDeckUseCase
import com.edukasyon.studentai.domain.usecase.GetJeviDecksUseCase
import com.edukasyon.studentai.core.util.QuizValidator
import com.edukasyon.studentai.domain.usecase.SaveFlashcardsToDeckUseCase
import com.edukasyon.studentai.domain.usecase.SaveQuizUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class DocStudyTarget {
    FLASHCARDS,
    QUIZ,
    BOTH,
}

enum class DocQuizDifficulty(val label: String) {
    BALANCED("Balanced"),
    CHALLENGE("Exam Challenge"),
}

data class DocToStudyUiState(
    val selectedUris: List<Uri> = emptyList(),
    val fileNames: List<String> = emptyList(),
    val isExtracting: Boolean = false,
    val extractionProgressText: String? = null,
    val extractedMarkdown: String? = null,
    val extractedPageCount: Int = 0,
    val extractedWordCount: Int = 0,
    val isFastOcr: Boolean = true,
    val isGenerating: Boolean = false,
    val generatingProgressText: String? = null,
    val target: DocStudyTarget = DocStudyTarget.BOTH,
    val quizCount: Int = 5,
    val quizDifficulty: DocQuizDifficulty = DocQuizDifficulty.BALANCED,
    val forceVision: Boolean = false,
    val decks: List<JeviDeck> = emptyList(),
    val selectedDeckId: String = JeviConstants.DEFAULT_DECK_ID,
    val generatedCards: List<Flashcard> = emptyList(),
    val generatedQuiz: Quiz? = null,
    val cardsSaved: Boolean = false,
    val quizSaved: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class DocToStudyViewModel @Inject constructor(
    private val documentPipeline: DocumentPipeline,
    private val aiGenerateFlashcards: AiGenerateFlashcardsUseCase,
    private val aiGenerateQuiz: AiGenerateQuizUseCase,
    private val saveFlashcardsToDeck: SaveFlashcardsToDeckUseCase,
    private val saveQuiz: SaveQuizUseCase,
    private val getDecks: GetJeviDecksUseCase,
    private val ensureDefaultDeck: EnsureJeviDefaultDeckUseCase,
    private val gizmoManager: GizmoGamificationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocToStudyUiState())
    val uiState: StateFlow<DocToStudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ensureDefaultDeck()
            getDecks().collect { decks ->
                _uiState.update { state ->
                    state.copy(
                        decks = decks,
                        selectedDeckId = state.selectedDeckId.takeIf { id ->
                            decks.any { it.id == id }
                        } ?: decks.firstOrNull()?.id ?: JeviConstants.DEFAULT_DECK_ID,
                    )
                }
            }
        }

        documentPipeline.progressFlow.onEach { p ->
            val statusLabel = when (p.status) {
                PageStatus.PENDING -> "Queued"
                PageStatus.RENDERING -> "Rendering page ${p.pageNum} of ${p.totalPages}…"
                PageStatus.READING -> "⚡ Reading page ${p.pageNum} of ${p.totalPages}…"
                PageStatus.DONE -> "Processed page ${p.pageNum} of ${p.totalPages}"
                PageStatus.FAILED -> "Page ${p.pageNum} failed"
            }
            _uiState.update {
                if (it.isExtracting) it.copy(extractionProgressText = statusLabel) else it
            }
        }.launchIn(viewModelScope)
    }

    fun setTarget(target: DocStudyTarget) {
        _uiState.update { it.copy(target = target) }
    }

    fun setQuizCount(count: Int) {
        _uiState.update { it.copy(quizCount = count.coerceIn(3, 15)) }
    }

    fun setDifficulty(difficulty: DocQuizDifficulty) {
        _uiState.update { it.copy(quizDifficulty = difficulty) }
    }

    fun setForceVision(force: Boolean) {
        _uiState.update { it.copy(forceVision = force) }
    }

    fun selectDeck(deckId: String) {
        _uiState.update { it.copy(selectedDeckId = deckId) }
    }

    fun clearDocuments() {
        _uiState.update {
            it.copy(
                selectedUris = emptyList(),
                fileNames = emptyList(),
                extractedMarkdown = null,
                extractedPageCount = 0,
                extractedWordCount = 0,
                generatedCards = emptyList(),
                generatedQuiz = null,
                cardsSaved = false,
                quizSaved = false,
                error = null,
                infoMessage = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun selectDocuments(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val names = uris.map { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            } ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "document")
        }

        _uiState.update {
            it.copy(
                selectedUris = uris,
                fileNames = names,
                isExtracting = true,
                extractionProgressText = "Preparing ${uris.size} document(s)…",
                extractedMarkdown = null,
                generatedCards = emptyList(),
                generatedQuiz = null,
                cardsSaved = false,
                quizSaved = false,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val result = documentPipeline.processDocuments(
                    context = context,
                    uris = uris,
                    fileNames = names,
                    forceVision = _uiState.value.forceVision,
                )
                val durationSec = "%.1f".format((System.currentTimeMillis() - startTime) / 1000f)
                val text = result.mergedMarkdown.trim()
                if (text.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            extractionProgressText = null,
                            error = "Could not extract readable text from the selected document(s). Try another file or clearer photo.",
                        )
                    }
                    return@launch
                }

                val words = ChatAttachmentUtils.usableWordCount(text)
                val isFast = result.visionPageCount == 0
                val speedBadge = if (isFast) "⚡ Instant OCR ($durationSec s)" else "Cloud AI ($durationSec s)"

                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionProgressText = null,
                        extractedMarkdown = text,
                        extractedPageCount = result.pageNotes.size,
                        extractedWordCount = words,
                        isFastOcr = isFast,
                        infoMessage = "$speedBadge · ${result.pageNotes.size} page(s) · $words words ready for study pack",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionProgressText = null,
                        error = e.message ?: "Failed to process documents.",
                    )
                }
            }
        }
    }

    fun generate() {
        val markdown = _uiState.value.extractedMarkdown
        if (markdown.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please upload or snap a document first.") }
            return
        }

        val target = _uiState.value.target
        val count = _uiState.value.quizCount
        val difficulty = _uiState.value.quizDifficulty.name
        val deckId = _uiState.value.selectedDeckId

        _uiState.update {
            it.copy(
                isGenerating = true,
                generatingProgressText = when (target) {
                    DocStudyTarget.FLASHCARDS -> "Crafting smart flashcards…"
                    DocStudyTarget.QUIZ -> "Constructing practice quiz ($count questions)…"
                    DocStudyTarget.BOTH -> "Generating complete study pack (cards + quiz)…"
                },
                error = null,
                cardsSaved = false,
                quizSaved = false,
            )
        }

        viewModelScope.launch {
            try {
                when (target) {
                    DocStudyTarget.FLASHCARDS -> {
                        val cards = aiGenerateFlashcards.execute(markdown)
                        if (cards.isEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    error = "No flashcards could be generated from this content.",
                                )
                            }
                            return@launch
                        }
                        val deckLinkedCards = cards.map { it.copy(deckId = deckId) }
                        gizmoManager.addXp(GizmoConstants.XP_GENERATE_FLASHCARDS)
                        gizmoManager.recordActivity()
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                generatedCards = deckLinkedCards,
                                infoMessage = "Generated ${deckLinkedCards.size} flashcards!",
                            )
                        }
                    }

                    DocStudyTarget.QUIZ -> {
                        val rawQuiz = aiGenerateQuiz.execute(
                            params = markdown,
                            count = count,
                            difficulty = difficulty,
                        )
                        val validated = QuizValidator.validate(rawQuiz).withDeckId(deckId)
                        gizmoManager.addXp(JeviConstants.XP_GENERATE_QUIZ)
                        gizmoManager.recordActivity()
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                generatedQuiz = validated,
                                infoMessage = "Generated quiz with ${validated.questions.size} questions!",
                            )
                        }
                    }

                    DocStudyTarget.BOTH -> {
                        val cardsDeferred = async { aiGenerateFlashcards.execute(markdown) }
                        val quizDeferred = async {
                            aiGenerateQuiz.execute(
                                params = markdown,
                                count = count,
                                difficulty = difficulty,
                            )
                        }

                        val cards = cardsDeferred.await()
                        val rawQuiz = quizDeferred.await()
                        val validatedQuiz = QuizValidator.validate(rawQuiz).withDeckId(deckId)
                        val deckLinkedCards = cards.map { it.copy(deckId = deckId) }

                        gizmoManager.addXp(GizmoConstants.XP_GENERATE_FLASHCARDS + JeviConstants.XP_GENERATE_QUIZ)
                        gizmoManager.recordActivity()

                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                generatedCards = deckLinkedCards,
                                generatedQuiz = validatedQuiz,
                                infoMessage = "Complete Study Pack ready! (${deckLinkedCards.size} flashcards + ${validatedQuiz.questions.size} quiz questions)",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = e.message ?: "Failed to generate study materials.",
                    )
                }
            }
        }
    }

    fun saveFlashcards() {
        val cards = _uiState.value.generatedCards
        val deckId = _uiState.value.selectedDeckId
        if (cards.isEmpty()) return

        viewModelScope.launch {
            try {
                saveFlashcardsToDeck(deckId, cards)
                gizmoManager.addXp(GizmoConstants.XP_SAVE_FLASHCARDS)
                gizmoManager.recordActivity()
                _uiState.update {
                    it.copy(
                        cardsSaved = true,
                        infoMessage = "Saved ${cards.size} flashcards to deck!",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save flashcards.") }
            }
        }
    }

    fun saveQuizDirectly() {
        val quiz = _uiState.value.generatedQuiz ?: return
        viewModelScope.launch {
            try {
                saveQuiz.execute(quiz)
                gizmoManager.addXp(JeviConstants.XP_SAVE_QUIZ)
                gizmoManager.recordActivity()
                _uiState.update {
                    it.copy(
                        quizSaved = true,
                        infoMessage = "Quiz saved to your library!",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save quiz.") }
            }
        }
    }
}
