package com.edukasyon.studentai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.gamification.GizmoGamificationManager
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviConstants
import com.edukasyon.studentai.domain.model.JeviDashboard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.core.util.QuizValidator
import com.edukasyon.studentai.domain.model.GizmoCompanionState
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.domain.model.QuestionType
import com.edukasyon.studentai.domain.model.withDeckId
import com.edukasyon.studentai.domain.repository.QuizRepository
import com.edukasyon.studentai.domain.usecase.AiGenerateFlashcardsUseCase
import com.edukasyon.studentai.domain.usecase.AiGenerateQuizUseCase
import com.edukasyon.studentai.domain.usecase.CreateJeviDeckUseCase
import com.edukasyon.studentai.domain.usecase.DeleteJeviDeckUseCase
import com.edukasyon.studentai.domain.usecase.EnsureJeviDefaultDeckUseCase
import com.edukasyon.studentai.domain.usecase.GetDeckFlashcardsUseCase
import com.edukasyon.studentai.domain.usecase.GetJeviDashboardUseCase
import com.edukasyon.studentai.domain.usecase.GetJeviDeckUseCase
import com.edukasyon.studentai.domain.usecase.GetJeviDecksUseCase
import com.edukasyon.studentai.domain.usecase.SaveFlashcardsToDeckUseCase
import com.edukasyon.studentai.domain.usecase.SaveQuizUseCase
import com.edukasyon.studentai.core.mlkit.PdfOcrHelper
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class JeviHomeUiState(
    val dashboard: JeviDashboard? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class JeviHomeViewModel @Inject constructor(
    private val getDashboard: GetJeviDashboardUseCase,
    private val ensureDefaultDeck: EnsureJeviDefaultDeckUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JeviHomeUiState())
    val uiState: StateFlow<JeviHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ensureDefaultDeck()
            getDashboard().collect { dashboard ->
                _uiState.update { it.copy(dashboard = dashboard, isLoading = false) }
            }
        }
    }
}

data class JeviDecksUiState(
    val decks: List<JeviDeck> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class JeviDecksViewModel @Inject constructor(
    private val getDecks: GetJeviDecksUseCase,
    private val createDeck: CreateJeviDeckUseCase,
    private val ensureDefaultDeck: EnsureJeviDefaultDeckUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JeviDecksUiState())
    val uiState: StateFlow<JeviDecksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ensureDefaultDeck()
            getDecks().collect { decks ->
                _uiState.update { it.copy(decks = decks, isLoading = false) }
            }
        }
    }

    fun createDeck(title: String, subjectId: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val colors = listOf("#6366F1", "#8B5CF6", "#EC4899", "#F59E0B", "#10B981", "#3B82F6")
            createDeck(
                JeviDeck(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = null,
                    subjectId = subjectId,
                    sourceNoteId = null,
                    colorHex = colors.random(),
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }
}

data class JeviDeckDetailUiState(
    val deck: JeviDeck? = null,
    val cards: List<Flashcard> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class JeviDeckDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDeck: GetJeviDeckUseCase,
    private val getDeckFlashcards: GetDeckFlashcardsUseCase,
    private val deleteDeck: DeleteJeviDeckUseCase,
) : ViewModel() {
    private val deckId: String = savedStateHandle.get<String>("deckId")
        ?: JeviConstants.DEFAULT_DECK_ID

    private val _uiState = MutableStateFlow(JeviDeckDetailUiState())
    val uiState: StateFlow<JeviDeckDetailUiState> = _uiState.asStateFlow()

    private val _deckDeleted = MutableStateFlow(false)
    val deckDeleted: StateFlow<Boolean> = _deckDeleted.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getDeck(deckId),
                getDeckFlashcards(deckId),
            ) { deck, cards ->
                JeviDeckDetailUiState(
                    deck = deck,
                    cards = cards,
                    isLoading = false,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteCurrentDeck() {
        if (deckId == JeviConstants.DEFAULT_DECK_ID) return
        viewModelScope.launch {
            deleteDeck(deckId)
            _deckDeleted.value = true
        }
    }
}

data class JeviCreateUiState(
    val topic: String = "",
    val isGenerating: Boolean = false,
    val isExtracting: Boolean = false,
    val generatedCards: List<Flashcard> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null,
    val decks: List<JeviDeck> = emptyList(),
    val selectedDeckId: String = JeviConstants.DEFAULT_DECK_ID,
)

@HiltViewModel
class JeviCreateViewModel @Inject constructor(
    private val aiGenerateFlashcards: AiGenerateFlashcardsUseCase,
    private val saveToDeck: SaveFlashcardsToDeckUseCase,
    private val getDecks: GetJeviDecksUseCase,
    private val gizmoManager: GizmoGamificationManager,
    private val ensureDefaultDeck: EnsureJeviDefaultDeckUseCase,
    private val pdfOcrHelper: PdfOcrHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JeviCreateUiState())
    val uiState: StateFlow<JeviCreateUiState> = _uiState.asStateFlow()

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
    }

    fun updateTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, error = null) }
    }

    fun selectDeck(deckId: String) {
        _uiState.update { it.copy(selectedDeckId = deckId) }
    }

    fun generate() {
        val topic = _uiState.value.topic.trim()
        if (topic.isBlank()) {
            _uiState.update { it.copy(error = "Enter a topic or paste note content.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, generatedCards = emptyList(), saved = false) }
            try {
                val cards = aiGenerateFlashcards.execute(topic)
                if (cards.isEmpty()) {
                    _uiState.update { it.copy(isGenerating = false, error = "No flashcards generated. Try more content.") }
                    return@launch
                }
                gizmoManager.addXp(com.edukasyon.studentai.domain.model.GizmoConstants.XP_GENERATE_FLASHCARDS)
                _uiState.update { it.copy(isGenerating = false, generatedCards = cards) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = e.message ?: "Generation failed") }
            }
        }
    }

    /**
     * Use extracted text (from scan or PDF) as the flashcard source.
     */
    fun generateFromDocument(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Could not read text from this document.") }
            return
        }
        _uiState.update { it.copy(topic = text.take(4000), error = null) }
        generate()
    }

    fun addCard(question: String, answer: String) {
        val q = question.trim()
        val a = answer.trim()
        if (q.isBlank() || a.isBlank()) return
        _uiState.update { state ->
            val card = Flashcard(
                id = UUID.randomUUID().toString(),
                question = q,
                answer = a,
                subjectId = null,
                deckId = state.selectedDeckId,
                topic = null,
                difficulty = "medium",
                reviewCount = 0,
                correctCount = 0,
                incorrectCount = 0,
                lastReviewedAt = null,
                nextReviewAt = null,
            )
            state.copy(generatedCards = state.generatedCards + card)
        }
    }

    fun updateCard(id: String, question: String, answer: String) {
        _uiState.update { state ->
            state.copy(
                generatedCards = state.generatedCards.map { card ->
                    if (card.id == id) {
                        card.copy(question = question.trim(), answer = answer.trim())
                    } else card
                }
            )
        }
    }

    fun removeCard(id: String) {
        _uiState.update { state ->
            state.copy(generatedCards = state.generatedCards.filter { it.id != id })
        }
    }

    fun saveToSelectedDeck() {
        val cards = _uiState.value.generatedCards
        val deckId = _uiState.value.selectedDeckId
        if (cards.isEmpty()) return
        viewModelScope.launch {
            try {
                saveToDeck(deckId, cards)
                gizmoManager.addXp(com.edukasyon.studentai.domain.model.GizmoConstants.XP_SAVE_FLASHCARDS)
                gizmoManager.recordActivity()
                _uiState.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save") }
            }
        }
    }
}

enum class JeviQuizSource { DECK, TOPIC }

enum class JeviQuizPhase { SETUP, REVIEW, PLAYING }

data class JeviQuizUiState(
    val phase: JeviQuizPhase = JeviQuizPhase.SETUP,
    val decks: List<JeviDeck> = emptyList(),
    val savedQuizzes: List<Quiz> = emptyList(),
    val selectedDeckId: String = JeviConstants.DEFAULT_DECK_ID,
    val topic: String = "",
    val source: JeviQuizSource = JeviQuizSource.DECK,
    val isGenerating: Boolean = false,
    val isExtracting: Boolean = false,
    val error: String? = null,
    val gizmo: GizmoCompanionState = GizmoCompanionState(),
    val generatedQuiz: Quiz? = null,
    val quizSession: QuizSessionState? = null,
    val quizSaved: Boolean = false,
)

@HiltViewModel
class JeviQuizViewModel @Inject constructor(
    private val aiGenerateQuiz: AiGenerateQuizUseCase,
    private val saveQuiz: SaveQuizUseCase,
    private val getDecks: GetJeviDecksUseCase,
    private val getDeckFlashcards: GetDeckFlashcardsUseCase,
    private val quizRepository: QuizRepository,
    private val gizmoManager: GizmoGamificationManager,
    private val ensureDefaultDeck: EnsureJeviDefaultDeckUseCase,
    private val pdfOcrHelper: PdfOcrHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JeviQuizUiState())
    val uiState: StateFlow<JeviQuizUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ensureDefaultDeck()
            gizmoManager.state.collect { gizmo ->
                _uiState.update { it.copy(gizmo = gizmo) }
            }
        }
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
        viewModelScope.launch {
            quizRepository.observeAll().collect { quizzes ->
                _uiState.update { it.copy(savedQuizzes = quizzes) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, error = null) }
    }

    fun selectDeck(deckId: String) {
        _uiState.update { it.copy(selectedDeckId = deckId, error = null) }
    }

    fun selectSource(source: JeviQuizSource) {
        _uiState.update { it.copy(source = source, error = null) }
    }

    fun generateFromDeck() {
        val deckId = _uiState.value.selectedDeckId
        val deck = _uiState.value.decks.find { it.id == deckId }
        if (deck == null) {
            _uiState.update { it.copy(error = "Select a deck first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            try {
                val cards = getDeckFlashcards(deckId).first()
                if (cards.isEmpty()) {
                    _uiState.update {
                        it.copy(isGenerating = false, error = "This deck has no flashcards yet.")
                    }
                    return@launch
                }
                val content = cards.joinToString("\n\n") { card ->
                    buildString {
                        append("Q: ${card.question}\nA: ${card.answer}")
                        card.topic?.let { append("\nTopic: $it") }
                    }
                }
                startQuizFromContent(
                    content = content,
                    title = "${deck.title} Quiz",
                    deckId = deckId,
                    subjectId = deck.subjectId,
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = e.message ?: "Quiz generation failed") }
            }
        }
    }

    fun generateFromTopic() {
        val topic = _uiState.value.topic.trim()
        if (topic.isBlank()) {
            _uiState.update { it.copy(error = "Enter a topic or paste study content.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            try {
                val deck = _uiState.value.decks.find { it.id == _uiState.value.selectedDeckId }
                startQuizFromContent(
                    content = topic,
                    title = "Quiz: ${topic.take(40)}",
                    deckId = _uiState.value.selectedDeckId,
                    subjectId = deck?.subjectId,
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = e.message ?: "Quiz generation failed") }
            }
        }
    }

    fun generate() {
        when (_uiState.value.source) {
            JeviQuizSource.DECK -> generateFromDeck()
            JeviQuizSource.TOPIC -> generateFromTopic()
        }
    }

    /**
     * Use extracted text (from scan or PDF) as the quiz source.
     */
    fun generateFromDocument(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Could not read text from this document.") }
            return
        }
        _uiState.update { it.copy(topic = text.take(4000), error = null) }
        generate()
    }

    fun startSavedQuiz(quizId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            try {
                val quiz = quizRepository.getQuiz(quizId)
                    ?: throw IllegalStateException("Quiz not found.")
                if (quiz.questions.isEmpty()) {
                    throw IllegalStateException("This quiz has no questions.")
                }
                beginSession(quiz)
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = e.message ?: "Failed to load quiz") }
            }
        }
    }

    fun backToSetup() {
        _uiState.update {
            it.copy(
                phase = JeviQuizPhase.SETUP,
                quizSession = null,
                generatedQuiz = null,
                quizSaved = false,
            )
        }
    }

    fun selectQuizAnswer(answer: String) {
        val session = _uiState.value.quizSession ?: return
        if (session.revealed || session.finished) return
        _uiState.update { it.copy(quizSession = session.copy(selectedAnswer = answer)) }
    }

    fun revealQuizAnswer() {
        val session = _uiState.value.quizSession ?: return
        val question = session.currentQuestion ?: return
        val selected = session.selectedAnswer ?: return
        if (session.revealed) return
        val isCorrect = question.isAnswerCorrect(selected)
        viewModelScope.launch {
            var gizmo = _uiState.value.gizmo
            if (isCorrect) {
                gizmo = gizmoManager.addXp(GizmoConstants.XP_CORRECT_ANSWER)
                gizmoManager.recordActivity()
            }
            val wrongAnswers = if (!isCorrect) {
                session.wrongAnswers + QuizWrongAnswer(question, selected)
            } else session.wrongAnswers
            _uiState.update {
                it.copy(
                    gizmo = gizmo,
                    quizSession = session.copy(
                        revealed = true,
                        correctCount = session.correctCount + if (isCorrect) 1 else 0,
                        wrongAnswers = wrongAnswers,
                    ),
                )
            }
        }
    }

    fun nextQuizQuestion() {
        val session = _uiState.value.quizSession ?: return
        if (!session.revealed) return
        val nextIndex = session.currentIndex + 1
        if (nextIndex >= session.totalQuestions) {
            viewModelScope.launch {
                val gizmo = gizmoManager.addXp(JeviConstants.XP_COMPLETE_QUIZ)
                gizmoManager.recordActivity()
                _uiState.update {
                    it.copy(
                        gizmo = gizmo,
                        quizSession = session.copy(finished = true),
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    quizSession = session.copy(
                        currentIndex = nextIndex,
                        selectedAnswer = null,
                        revealed = false,
                    ),
                )
            }
        }
    }

    fun restartQuiz() {
        val quiz = _uiState.value.generatedQuiz ?: return
        _uiState.update {
            it.copy(
                quizSession = QuizSessionState(quiz = quiz),
                quizSaved = false,
            )
        }
    }

    fun reviewMistakes() {
        val session = _uiState.value.quizSession ?: return
        val wrongQuestions = session.wrongAnswers.map { it.question }
        if (wrongQuestions.isEmpty()) return
        val reviewQuiz = session.quiz.copy(
            title = "${session.quiz.title} — Review",
            questions = wrongQuestions,
        )
        _uiState.update {
            it.copy(
                generatedQuiz = reviewQuiz,
                quizSession = QuizSessionState(quiz = reviewQuiz),
                quizSaved = false,
                phase = JeviQuizPhase.PLAYING,
            )
        }
    }

    fun updateQuizQuestion(
        questionId: String,
        question: String,
        options: List<String>,
        correctAnswer: String,
    ) {
        val quiz = _uiState.value.generatedQuiz ?: return
        val updatedQuestions = quiz.questions.map { q ->
            if (q.id == questionId) {
                q.copy(
                    question = question.trim(),
                    options = options.map { it.trim() }.filter { it.isNotBlank() },
                    correctAnswer = correctAnswer.trim(),
                )
            } else q
        }
        _uiState.update {
            it.copy(generatedQuiz = quiz.copy(questions = updatedQuestions), quizSaved = false)
        }
    }

    fun addQuizQuestion(question: String, options: List<String>, correctAnswer: String) {
        val quiz = _uiState.value.generatedQuiz ?: return
        if (question.isBlank() || options.none { it.isNotBlank() }) return
        val newQuestion = QuizQuestion(
            id = UUID.randomUUID().toString(),
            quizId = quiz.id,
            type = QuestionType.MULTIPLE_CHOICE,
            question = question.trim(),
            options = options.map { it.trim() }.filter { it.isNotBlank() },
            correctAnswer = correctAnswer.trim(),
        )
        _uiState.update {
            it.copy(
                generatedQuiz = quiz.copy(questions = quiz.questions + newQuestion),
                quizSaved = false,
            )
        }
    }

    fun removeQuizQuestion(questionId: String) {
        val quiz = _uiState.value.generatedQuiz ?: return
        _uiState.update {
            it.copy(
                generatedQuiz = quiz.copy(questions = quiz.questions.filter { q -> q.id != questionId }),
                quizSaved = false,
            )
        }
    }

    fun saveQuizResult() {
        val quiz = _uiState.value.generatedQuiz ?: return
        viewModelScope.launch {
            try {
                saveQuiz.execute(quiz)
                gizmoManager.addXp(JeviConstants.XP_SAVE_QUIZ)
                gizmoManager.recordActivity()
                _uiState.update { it.copy(quizSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save quiz") }
            }
        }
    }

    private suspend fun startQuizFromContent(
        content: String,
        title: String,
        deckId: String,
        subjectId: String?,
    ) {
        val rawQuiz = aiGenerateQuiz.execute(content)
        val validated = QuizValidator.validate(rawQuiz)
        val linkedQuiz = validated.copy(
            title = title,
            subjectId = subjectId,
        ).withDeckId(deckId)
        gizmoManager.addXp(JeviConstants.XP_GENERATE_QUIZ)
        gizmoManager.recordActivity()
        beginSession(linkedQuiz)
    }

    private fun beginSession(quiz: Quiz) {
        _uiState.update {
            it.copy(
                isGenerating = false,
                generatedQuiz = quiz,
                quizSession = QuizSessionState(quiz = quiz),
                quizSaved = false,
                phase = JeviQuizPhase.REVIEW,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                saveQuiz.execute(quiz)
                gizmoManager.addXp(JeviConstants.XP_SAVE_QUIZ)
                _uiState.update { it.copy(quizSaved = true) }
            }
        }
    }

    fun startQuizFromReview() {
        _uiState.update { it.copy(phase = JeviQuizPhase.PLAYING) }
    }
}
