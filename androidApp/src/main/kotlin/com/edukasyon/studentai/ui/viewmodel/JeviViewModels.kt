package com.edukasyon.studentai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.gamification.GizmoGamificationManager
import com.edukasyon.studentai.core.document.DocumentPipeline
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
import kotlinx.coroutines.CancellationException
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
    val audioState: DeckAudioState = DeckAudioState.Idle,
    // Podcast settings (lane D): theme id null = first theme; voice overrides are
    // per deck+theme and null = the theme's default voice for that speaker.
    val podcastThemeId: String? = null,
    val voiceAOverride: String? = null,
    val voiceBOverride: String? = null,
    // Transient snackbar text (partial coverage warning, export result); UI clears via clearAudioMessage().
    val audioMessage: String? = null,
    // Which audition preview ("th_<themeId>" / "v_<voiceId>") is currently playing; null = none.
    val previewPlayingKey: String? = null,
)

sealed interface DeckAudioState {
    data object Idle : DeckAudioState
    // was `data object` — now carries an optional progress line ("Writing the script…",
    // "Synthesizing line 4 of 23…"); default null keeps old `Generating()` use sites valid.
    data class Generating(val progressNote: String? = null) : DeckAudioState
    data class Ready(val playable: Boolean, val positionMs: Int = 0, val durationMs: Int = 0) : DeckAudioState
    data class Failed(val message: String) : DeckAudioState
}

@HiltViewModel
class JeviDeckDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDeck: GetJeviDeckUseCase,
    private val getDeckFlashcards: GetDeckFlashcardsUseCase,
    private val deleteDeck: DeleteJeviDeckUseCase,
    private val audioOverviewManager: com.edukasyon.studentai.core.audio.AudioOverviewManager,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
) : ViewModel() {
    private val deckId: String = savedStateHandle.get<String>("deckId")
        ?: JeviConstants.DEFAULT_DECK_ID

    private val _uiState = MutableStateFlow(JeviDeckDetailUiState())
    val uiState: StateFlow<JeviDeckDetailUiState> = _uiState.asStateFlow()

    private val _deckDeleted = MutableStateFlow(false)
    val deckDeleted: StateFlow<Boolean> = _deckDeleted.asStateFlow()

    private var player: android.media.MediaPlayer? = null
    private var progressTicker: kotlinx.coroutines.Job? = null

    // Audition previews (theme/voice try-before-select). Separate player so an
    // episode that's playing keeps its position; only one preview at a time.
    private var previewPlayer: android.media.MediaPlayer? = null
    private var previewJob: kotlinx.coroutines.Job? = null

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
                    podcastThemeId = _uiState.value.podcastThemeId,
                    voiceAOverride = _uiState.value.voiceAOverride,
                    voiceBOverride = _uiState.value.voiceBOverride,
                    audioState = _uiState.value.audioState,
                    audioMessage = _uiState.value.audioMessage,
                    previewPlayingKey = _uiState.value.previewPlayingKey,
                )
            }.collect { state ->
                _uiState.value = state
                syncAudioCache()
            }
        }
        // Podcast settings (theme + per deck+theme voice overrides) load once and
        // live-update when the pickers change them; switching settings re-checks
        // the cache so an already-generated episode is instantly playable (AC2).
        viewModelScope.launch {
            combine(
                preferences.podcastThemeId,
                preferences.podcastVoiceOverridesA,
                preferences.podcastVoiceOverridesB,
            ) { themeId, overridesA, overridesB ->
                val effectiveThemeId = com.edukasyon.studentai.core.audio.PodcastThemes.byId(themeId).id
                Triple(
                    themeId,
                    overridesA[voiceOverrideKey(effectiveThemeId)],
                    overridesB[voiceOverrideKey(effectiveThemeId)],
                )
            }.collect { (themeId, voiceA, voiceB) ->
                _uiState.update {
                    it.copy(podcastThemeId = themeId, voiceAOverride = voiceA, voiceBOverride = voiceB)
                }
                stopPlaybackAndSyncCache()
            }
        }
    }

    /** Effective theme for generation/playback: selected theme + per-deck voice overrides. */
    fun activePodcastTheme(): com.edukasyon.studentai.core.audio.PodcastTheme {
        val base = com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId)
        return base.copy(
            voiceA = _uiState.value.voiceAOverride ?: base.voiceA,
            voiceB = _uiState.value.voiceBOverride ?: base.voiceB,
        )
    }

    private fun voiceOverrideKey(themeId: String) = "$deckId|$themeId"

    fun selectPodcastTheme(themeId: String) {
        viewModelScope.launch { preferences.setPodcastThemeId(themeId) }
    }

    fun setPodcastVoiceA(voice: String?) {
        viewModelScope.launch {
            preferences.setPodcastVoiceA(
                deckId,
                com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId).id,
                voice,
            )
        }
    }

    fun setPodcastVoiceB(voice: String?) {
        viewModelScope.launch {
            preferences.setPodcastVoiceB(
                deckId,
                com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId).id,
                voice,
            )
        }
    }

    /** Show the cache state that matches the current theme/voice mix. */
    private fun syncAudioCache() {
        val deck = _uiState.value.deck ?: return
        val cards = _uiState.value.cards
        if (cards.isEmpty()) return
        when (_uiState.value.audioState) {
            is DeckAudioState.Generating -> return
            else -> {
                val cached = audioOverviewManager.cachedFile(deck, cards, activePodcastTheme())
                _uiState.update {
                    if (cached != null && it.audioState !is DeckAudioState.Ready) {
                        it.copy(audioState = DeckAudioState.Ready(playable = false))
                    } else if (cached == null && it.audioState is DeckAudioState.Ready) {
                        it.copy(audioState = DeckAudioState.Idle)
                    } else {
                        it
                    }
                }
            }
        }
    }

    /** Theme/voice change: tear the current player down, then re-sync with the new cache. */
    private fun stopPlaybackAndSyncCache() {
        progressTicker?.cancel()
        progressTicker = null
        runCatching { player?.release() }
        player = null
        syncAudioCache()
    }

    fun clearAudioMessage() {
        _uiState.update { it.copy(audioMessage = null) }
    }

    /**
     * Toggle an audition preview: [key] identifies the option ("th_<themeId>" or
     * "v_<voiceId>"); tapping the playing one stops it. Synthesis runs off the
     * cached preview file (instant on repeat); failures stop silently.
     */
    fun audition(key: String, requests: () -> List<com.edukasyon.studentai.core.network.TtsRequest>) {
        previewJob?.cancel()
        runCatching { previewPlayer?.stop() }
        runCatching { previewPlayer?.release() }
        previewPlayer = null
        if (_uiState.value.previewPlayingKey == key) {
            _uiState.update { it.copy(previewPlayingKey = null) }
            return
        }
        _uiState.update { it.copy(previewPlayingKey = key) }
        previewJob = viewModelScope.launch {
            val file = audioOverviewManager.preview(key, requests())
            if (file == null || _uiState.value.previewPlayingKey != key) {
                _uiState.update {
                    if (it.previewPlayingKey == key) {
                        it.copy(
                            previewPlayingKey = null,
                            audioMessage = if (file == null) "Preview unavailable — check your connection." else it.audioMessage,
                        )
                    } else it
                }
                return@launch
            }
            runCatching {
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setOnCompletionListener {
                        if (_uiState.value.previewPlayingKey == key) {
                            _uiState.update { it.copy(previewPlayingKey = null) }
                        }
                        runCatching { release() }
                        if (previewPlayer === this) previewPlayer = null
                    }
                    prepare()
                    start()
                }
                previewPlayer = mp
            }.onFailure {
                _uiState.update { if (it.previewPlayingKey == key) it.copy(previewPlayingKey = null) else it }
            }
        }
    }

    fun auditionTheme(theme: com.edukasyon.studentai.core.audio.PodcastTheme) {
        // Voice overrides are stored per deck+selected-theme; only apply them
        // when auditioning the currently selected theme, otherwise defaults.
        val selectedId = com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId).id
        val effective = if (theme.id == selectedId) {
            theme.copy(
                voiceA = _uiState.value.voiceAOverride ?: theme.voiceA,
                voiceB = _uiState.value.voiceBOverride ?: theme.voiceB,
            )
        } else theme
        // Key includes the effective pair so a voice change re-synthesizes instead of replaying a stale cache.
        audition("th_${theme.id}_${effective.voiceA}_${effective.voiceB}") {
            com.edukasyon.studentai.core.audio.AudioOverviewManager.previewRequestsFor(effective)
        }
    }

    fun auditionVoice(voiceId: String) {
        audition("v_$voiceId") {
            com.edukasyon.studentai.core.audio.AudioOverviewManager.previewRequestsForVoice(voiceId)
        }
    }

    fun deleteCurrentDeck() {
        if (deckId == JeviConstants.DEFAULT_DECK_ID) return
        viewModelScope.launch {
            deleteDeck(deckId)
            _deckDeleted.value = true
        }
    }

    /** Generates (or reuses cached) Audio Overview script + MP3 for the current deck + selected theme/voices. */
    fun generateAudioOverview() {
        val deck = _uiState.value.deck ?: return
        val cards = _uiState.value.cards
        if (cards.isEmpty() || _uiState.value.audioState is DeckAudioState.Generating) return
        viewModelScope.launch {
            _uiState.update { it.copy(audioState = DeckAudioState.Generating()) }
            val theme = activePodcastTheme()
            when (
                val result = audioOverviewManager.overviewFor(
                    deck = deck,
                    cards = cards,
                    theme = theme,
                    onProgress = { stage, lineDone, lineTotal ->
                        val note = when (stage) {
                            com.edukasyon.studentai.core.audio.PodcastStatusEvent.Scripting -> "Writing the script…"
                            com.edukasyon.studentai.core.audio.PodcastStatusEvent.Auditing -> "Checking card coverage…"
                            com.edukasyon.studentai.core.audio.PodcastStatusEvent.Synthesizing ->
                                if (lineTotal > 0) "Generating line $lineDone of $lineTotal…"
                                else "Voicing the episode…"
                        }
                        _uiState.update { s ->
                            if (s.audioState is DeckAudioState.Generating) {
                                s.copy(audioState = DeckAudioState.Generating(progressNote = note))
                            } else s
                        }
                    },
                )
            ) {
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.Result.Ready ->
                    _uiState.update {
                        it.copy(
                            audioState = DeckAudioState.Ready(playable = false),
                            audioMessage = result.partialCoverage.takeIf { missing -> missing.isNotEmpty() }?.let { missing ->
                                "Covered ${cards.size - missing.size} of ${cards.size} cards (missed ${missing.joinToString(", ")}) — regenerate for full coverage."
                            },
                        )
                    }
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.Result.Failed ->
                    _uiState.update { it.copy(audioState = DeckAudioState.Failed(result.message)) }
            }
        }
    }

    /** Ready-state: copy the cached episode into the public Downloads folder. */
    fun saveEpisodeToDownloads() {
        val deck = _uiState.value.deck ?: return
        val file = audioOverviewManager.cachedFile(deck, _uiState.value.cards, activePodcastTheme()) ?: return
        viewModelScope.launch {
            val result = audioOverviewManager.exportToDevice(
                file = file,
                deckTitle = deck.title,
                themeId = com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId).id,
            )
            val message = when (result) {
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.ExportResult.Saved ->
                    "Saved ${result.displayPath}"
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.ExportResult.Failed -> result.message
            }
            _uiState.update { it.copy(audioMessage = message) }
        }
    }

    /** Suggested file name for the SAF "Save as…" launcher. */
    fun episodeExportName(): String {
        val deck = _uiState.value.deck ?: return "JEVI episode.mp3"
        return com.edukasyon.studentai.core.audio.AudioOverviewManager.exportedDisplayName(
            deckTitle = deck.title,
            themeId = com.edukasyon.studentai.core.audio.PodcastThemes.byId(_uiState.value.podcastThemeId).id,
        )
    }

    /** ACTION_CREATE_DOCUMENT intent to launch from the UI for "Save as…". */
    fun episodeExportIntent(): android.content.Intent =
        audioOverviewManager.exportIntent(episodeExportName())

    /** Handles the uri returned by the ACTION_CREATE_DOCUMENT launcher ([AudioOverviewManager.exportIntent]). */
    fun exportEpisodeToUri(uri: android.net.Uri) {
        val deck = _uiState.value.deck ?: return
        val file = audioOverviewManager.cachedFile(deck, _uiState.value.cards, activePodcastTheme()) ?: return
        viewModelScope.launch {
            val result = audioOverviewManager.exportToUri(file = file, uri = uri)
            val message = when (result) {
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.ExportResult.Saved ->
                    "Saved ${result.displayPath}"
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.ExportResult.Failed -> result.message
            }
            _uiState.update { it.copy(audioMessage = message) }
        }
    }

    fun playOrPauseAudio() {
        val deck = _uiState.value.deck ?: return
        val file = audioOverviewManager.cachedFile(deck, _uiState.value.cards, activePodcastTheme()) ?: return
        val current = player
        val state = _uiState.value.audioState
        if (current != null && state is DeckAudioState.Ready && state.playable) {
            current.pause()
            _uiState.update { it.copy(audioState = state.copy(playable = false)) }
            return
        }
        if (current != null && current.isPlaying) return
        runCatching {
            val mp = current ?: android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _uiState.update { s ->
                        (s.audioState as? DeckAudioState.Ready)?.let { s.copy(audioState = it.copy(playable = false, positionMs = 0)) } ?: s
                    }
                }
            }
            mp.seekTo((_uiState.value.audioState as? DeckAudioState.Ready)?.positionMs ?: 0)
            mp.start()
            player = mp
            _uiState.update { s ->
                val ready = (s.audioState as? DeckAudioState.Ready)
                    ?.copy(playable = true, durationMs = mp.duration)
                    ?: DeckAudioState.Ready(playable = true, durationMs = mp.duration)
                s.copy(audioState = ready)
            }
            startProgressTicker()
        }.onFailure { err ->
            _uiState.update { it.copy(audioState = DeckAudioState.Failed("Could not play the audio: ${err.message}")) }
        }
    }

    fun seekAudio(fraction: Float) {
        val mp = player ?: return
        runCatching {
            mp.seekTo((mp.duration * fraction.coerceIn(0f, 1f)).toInt())
            _uiState.update { s ->
                (s.audioState as? DeckAudioState.Ready)?.let { s.copy(audioState = it.copy(positionMs = mp.currentPosition)) } ?: s
            }
        }
    }

    private fun startProgressTicker() {
        progressTicker?.cancel()
        progressTicker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition }.getOrNull() ?: break
                _uiState.update { s ->
                    (s.audioState as? DeckAudioState.Ready)?.let { s.copy(audioState = it.copy(positionMs = pos)) } ?: s
                }
            }
        }
    }

    override fun onCleared() {
        progressTicker?.cancel()
        runCatching { player?.release() }
        player = null
        previewJob?.cancel()
        runCatching { previewPlayer?.release() }
        previewPlayer = null
    }
}

data class JeviCreateUiState(
    val topic: String = "",
    val isGenerating: Boolean = false,
    val isExtracting: Boolean = false,
    // Scanner-style live status while a document is being read ("Page 2 of 5 · reading").
    val extractionNote: String? = null,
    // Non-blocking coverage note from document reads (shown as snackbar, not error).
    val infoNote: String? = null,
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
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
                throw e
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
        // Cap at 60k chars (DocumentPipeline.MAX_PAYLOAD_CHARS) with explicit warning
        val capped = if (text.length > com.edukasyon.studentai.core.document.DocumentPipeline.MAX_PAYLOAD_CHARS) {
            _uiState.update { it.copy(
                error = "Document is ${text.length} chars. Only the first 60,000 characters will be used for generation."
            ) }
            text.take(com.edukasyon.studentai.core.document.DocumentPipeline.MAX_PAYLOAD_CHARS)
        } else text
        _uiState.update { it.copy(topic = capped, error = null) }
        generate()
    }

    /** Start of a scanner-style document read: block inputs, show progress. */
    fun beginExtraction() {
        _uiState.update { it.copy(isExtracting = true, extractionNote = null, error = null) }
    }

    fun updateExtractionNote(note: String) {
        _uiState.update { if (it.isExtracting) it.copy(extractionNote = note) else it }
    }

    /** End of document read; hand the clean markdown to generation, or surface failure. */
    fun finishExtraction(markdown: String?, info: String? = null) {
        _uiState.update { it.copy(isExtracting = false, extractionNote = null) }
        if (markdown == null) {
            _uiState.update { it.copy(error = "Couldn't read this document. Try another file or check your connection.") }
            return
        }
        generateFromDocument(markdown)
        if (info != null) _uiState.update { it.copy(infoNote = info) }
    }

    fun clearInfoNote() {
        _uiState.update { it.copy(infoNote = null) }
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
    val extractionNote: String? = null,
    val infoNote: String? = null,
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
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
                throw e
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
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
                throw e
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
        // Cap at 60k chars (DocumentPipeline.MAX_PAYLOAD_CHARS) with explicit warning
        val capped = if (text.length > com.edukasyon.studentai.core.document.DocumentPipeline.MAX_PAYLOAD_CHARS) {
            _uiState.update { it.copy(
                error = "Document is ${text.length} chars. Only the first 60,000 characters will be used for generation."
            ) }
            text.take(com.edukasyon.studentai.core.document.DocumentPipeline.MAX_PAYLOAD_CHARS)
        } else text
        _uiState.update { it.copy(topic = capped, source = JeviQuizSource.TOPIC, error = null) }
        generate()
    }

    /** Start of a scanner-style document read: block inputs, show progress. */
    fun beginExtraction() {
        _uiState.update { it.copy(isExtracting = true, extractionNote = null, error = null) }
    }

    fun updateExtractionNote(note: String) {
        _uiState.update { if (it.isExtracting) it.copy(extractionNote = note) else it }
    }

    /** End of document read; hand the clean markdown to generation, or surface failure. */
    fun finishExtraction(markdown: String?, info: String? = null) {
        _uiState.update { it.copy(isExtracting = false, extractionNote = null) }
        if (markdown == null) {
            _uiState.update { it.copy(error = "Couldn't read this document. Try another file or check your connection.") }
            return
        }
        generateFromDocument(markdown)
        if (info != null) _uiState.update { it.copy(infoNote = info) }
    }

    fun clearInfoNote() {
        _uiState.update { it.copy(infoNote = null) }
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
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
                throw e
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
        val wordCount = content.split("\\s+".toRegex()).size
        val count = (wordCount / 50).coerceIn(5, 25)
        val rawQuiz = aiGenerateQuiz.execute(params = content, count = count)
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
