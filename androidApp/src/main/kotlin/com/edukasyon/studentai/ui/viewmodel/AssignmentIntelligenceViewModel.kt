package com.edukasyon.studentai.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.core.util.ContentDisplayKind
import com.edukasyon.studentai.core.util.MAX_TOOLS_PDF_BYTES
import com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer
import com.edukasyon.studentai.domain.model.AssignmentAnalysisInput
import com.edukasyon.studentai.domain.model.AssignmentBreakdown
import com.edukasyon.studentai.domain.usecase.AiAnalyzeAssignmentUseCase
import com.edukasyon.studentai.domain.usecase.SaveAssignmentBreakdownToPlannerUseCase
import com.edukasyon.studentai.ui.components.AiSafetyMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AssignmentInputMode {
    TEXT,
    PDF,
    IMAGE,
}

data class AssignmentIntelligenceUiState(
    val inputMode: AssignmentInputMode = AssignmentInputMode.TEXT,
    val instructionsText: String = "",
    val selectedFileName: String? = null,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val breakdown: AssignmentBreakdown? = null,
    val showBreakdownReview: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AssignmentIntelligenceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val analyzeAssignment: AiAnalyzeAssignmentUseCase,
    private val saveToPlanner: SaveAssignmentBreakdownToPlannerUseCase,
    private val mlKitTextRecognizer: MlKitTextRecognizer,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(restoreUiState())
    val uiState: StateFlow<AssignmentIntelligenceUiState> = _uiState.asStateFlow()

    private var pendingImageBase64: String? = null
    private var pendingAttachmentText: String? = null

    fun setInputMode(mode: AssignmentInputMode) {
        val previousMode = _uiState.value.inputMode
        _uiState.update {
            it.copy(
                inputMode = mode,
                error = null,
                selectedFileName = if (mode == previousMode) it.selectedFileName else null,
            )
        }
        if (mode != previousMode) {
            pendingImageBase64 = null
            pendingAttachmentText = null
        }
        persistUiState(_uiState.value)
    }

    fun updateInstructionsText(text: String) {
        _uiState.update { it.copy(instructionsText = text, error = null) }
        persistUiState(_uiState.value)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /** Returns to the input form without discarding the analyzed breakdown. */
    fun backToInput() {
        _uiState.update { it.copy(showBreakdownReview = false, error = null) }
        persistUiState(_uiState.value)
    }

    fun resumeBreakdownReview() {
        if (_uiState.value.breakdown != null) {
            _uiState.update { it.copy(showBreakdownReview = true, error = null) }
            persistUiState(_uiState.value)
        }
    }

    fun discardBreakdown() {
        pendingImageBase64 = null
        pendingAttachmentText = null
        _uiState.update {
            AssignmentIntelligenceUiState(inputMode = it.inputMode)
        }
        clearPersistedBreakdown()
        persistUiState(_uiState.value)
    }

    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch {
            val name = ChatAttachmentUtils.resolveContentDisplayName(appContext, uri, ContentDisplayKind.PDF)
            _uiState.update {
                it.copy(
                    inputMode = AssignmentInputMode.PDF,
                    selectedFileName = name,
                    error = null,
                    isAnalyzing = true,
                    showBreakdownReview = false,
                )
            }
            pendingImageBase64 = null
            pendingAttachmentText = null

            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read PDF file.")
                if (bytes.size > MAX_TOOLS_PDF_BYTES) {
                    throw IllegalStateException("PDF is too large (max ${MAX_TOOLS_PDF_BYTES / (1024 * 1024)} MB).")
                }

                val embedded = ChatAttachmentUtils.extractEmbeddedPdfText(bytes)
                if (embedded != null) {
                    pendingAttachmentText = embedded.take(8000)
                } else {
                    val pages = listOfNotNull(
                        ChatAttachmentUtils.renderPdfFirstPageAsJpeg(appContext, uri),
                    )
                    if (pages.isEmpty()) {
                        throw IllegalStateException("Could not render PDF for analysis.")
                    }
                    val ocr = mlKitTextRecognizer.recognizeFromPageImages(pages)
                    if (ocr.hasUsableText) {
                        pendingAttachmentText = ocr.text.take(8000)
                    } else {
                        pendingImageBase64 = android.util.Base64.encodeToString(
                            pages.first(),
                            android.util.Base64.NO_WRAP,
                        )
                    }
                }

                _uiState.update { it.copy(isAnalyzing = false) }
                analyzePendingInput(fallbackText = _uiState.value.instructionsText.takeIf { it.isNotBlank() })
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isAnalyzing = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF preparation failed", e)
                _uiState.update {
                    it.copy(isAnalyzing = false, error = e.message ?: "Could not process PDF.")
                }
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            val name = ChatAttachmentUtils.resolveContentDisplayName(appContext, uri, ContentDisplayKind.IMAGE)
            _uiState.update {
                it.copy(
                    inputMode = AssignmentInputMode.IMAGE,
                    selectedFileName = name,
                    error = null,
                    isAnalyzing = true,
                    showBreakdownReview = false,
                )
            }
            pendingAttachmentText = null

            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read image.")
                if (bytes.isEmpty()) throw IllegalStateException("Could not read image.")
                processAssignmentImageBytes(bytes, name)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isAnalyzing = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image preparation failed", e)
                _uiState.update {
                    it.copy(isAnalyzing = false, error = e.message ?: "Could not process image.")
                }
            }
        }
    }

    fun onScannedImageBytes(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    inputMode = AssignmentInputMode.IMAGE,
                    selectedFileName = "Scanned document",
                    error = null,
                    isAnalyzing = true,
                    showBreakdownReview = false,
                )
            }
            pendingAttachmentText = null
            try {
                if (bytes.isEmpty()) throw IllegalStateException("Scanned image was empty.")
                processAssignmentImageBytes(bytes, "Scanned document")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isAnalyzing = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document scan preparation failed", e)
                _uiState.update {
                    it.copy(isAnalyzing = false, error = e.message ?: "Could not process scanned document.")
                }
            }
        }
    }

    private suspend fun processAssignmentImageBytes(bytes: ByteArray, displayName: String) {
        val reportedMime = "image/jpeg"
        val (compressed, mime) = ChatAttachmentUtils.compressImageBytes(bytes, reportedMime)
        if (mime != "image/jpeg" || compressed.isEmpty()) {
            throw IllegalStateException("Unsupported image format. Use JPG or PNG.")
        }
        val ocr = mlKitTextRecognizer.recognizeFromBytes(compressed)
        if (ocr.hasUsableText) {
            pendingAttachmentText = ocr.text.take(8000)
        }
        pendingImageBase64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
        _uiState.update { it.copy(isAnalyzing = false, selectedFileName = displayName) }
        analyzePendingInput(fallbackText = _uiState.value.instructionsText.takeIf { it.isNotBlank() })
    }

    fun analyzeFromText() {
        val text = _uiState.value.instructionsText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Paste assignment instructions to analyze.") }
            return
        }
        pendingImageBase64 = null
        pendingAttachmentText = null
        viewModelScope.launch {
            analyzePendingInput(fallbackText = text)
        }
    }

    fun addToPlanner() {
        val breakdown = _uiState.value.breakdown ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val task = saveToPlanner.execute(breakdown)
                pendingImageBase64 = null
                pendingAttachmentText = null
                _uiState.update {
                    AssignmentIntelligenceUiState(
                        inputMode = it.inputMode,
                        successMessage = "Added \"${task.title}\" with ${task.subtasks.size} subtasks to Planner.",
                    )
                }
                clearPersistedBreakdown()
                persistUiState(_uiState.value)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Could not save to Planner.")
                }
            }
        }
    }

    private suspend fun analyzePendingInput(fallbackText: String?) {
        _uiState.update { it.copy(isAnalyzing = true, error = null, showBreakdownReview = false) }
        try {
            val input = AssignmentAnalysisInput(
                text = fallbackText,
                attachmentText = pendingAttachmentText,
                imageBase64 = pendingImageBase64,
            )
            if (input.text.isNullOrBlank() && input.attachmentText.isNullOrBlank() && input.imageBase64.isNullOrBlank()) {
                throw IllegalStateException("Provide instructions, PDF, or image to analyze.")
            }
            val breakdown = analyzeAssignment.execute(input)
            _uiState.update {
                it.copy(isAnalyzing = false, breakdown = breakdown, showBreakdownReview = true)
            }
            persistUiState(_uiState.value)
        } catch (e: CancellationException) {
            _uiState.update { it.copy(isAnalyzing = false) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Assignment analysis failed", e)
            _uiState.update {
                it.copy(isAnalyzing = false, error = mapError(e))
            }
        }
    }

    private fun restoreUiState(): AssignmentIntelligenceUiState {
        val breakdown = savedStateHandle.get<String>(KEY_BREAKDOWN_JSON)?.let { encoded ->
            runCatching { json.decodeFromString<AssignmentBreakdown>(encoded) }.getOrNull()
        }
        val inputMode = savedStateHandle.get<String>(KEY_INPUT_MODE)?.let { name ->
            runCatching { AssignmentInputMode.valueOf(name) }.getOrNull()
        } ?: AssignmentInputMode.TEXT
        val showReview = savedStateHandle.get<Boolean>(KEY_SHOW_BREAKDOWN_REVIEW)
            ?: (breakdown != null)
        return AssignmentIntelligenceUiState(
            inputMode = inputMode,
            instructionsText = savedStateHandle.get<String>(KEY_INSTRUCTIONS_TEXT).orEmpty(),
            selectedFileName = savedStateHandle.get<String>(KEY_SELECTED_FILE_NAME),
            breakdown = breakdown,
            showBreakdownReview = showReview && breakdown != null,
        )
    }

    private fun persistUiState(state: AssignmentIntelligenceUiState) {
        savedStateHandle[KEY_INPUT_MODE] = state.inputMode.name
        savedStateHandle[KEY_INSTRUCTIONS_TEXT] = state.instructionsText
        if (state.selectedFileName != null) {
            savedStateHandle[KEY_SELECTED_FILE_NAME] = state.selectedFileName
        } else {
            savedStateHandle.remove<String>(KEY_SELECTED_FILE_NAME)
        }
        savedStateHandle[KEY_SHOW_BREAKDOWN_REVIEW] = state.showBreakdownReview
        val breakdown = state.breakdown
        if (breakdown != null) {
            savedStateHandle[KEY_BREAKDOWN_JSON] = json.encodeToString(breakdown)
        } else {
            savedStateHandle.remove<String>(KEY_BREAKDOWN_JSON)
        }
    }

    private fun clearPersistedBreakdown() {
        savedStateHandle.remove<String>(KEY_BREAKDOWN_JSON)
        savedStateHandle[KEY_SHOW_BREAKDOWN_REVIEW] = false
        savedStateHandle.remove<String>(KEY_SELECTED_FILE_NAME)
        savedStateHandle[KEY_INSTRUCTIONS_TEXT] = ""
    }

    private fun mapError(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: AiSafetyMessages.providerUnavailableMessage()

    companion object {
        private const val TAG = "AssignmentIntelligence"
        private const val KEY_BREAKDOWN_JSON = "assignment_breakdown_json"
        private const val KEY_INPUT_MODE = "assignment_input_mode"
        private const val KEY_INSTRUCTIONS_TEXT = "assignment_instructions_text"
        private const val KEY_SELECTED_FILE_NAME = "assignment_selected_file_name"
        private const val KEY_SHOW_BREAKDOWN_REVIEW = "assignment_show_breakdown_review"
    }
}
