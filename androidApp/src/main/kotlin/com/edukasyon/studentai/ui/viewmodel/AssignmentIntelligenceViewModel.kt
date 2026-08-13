package com.edukasyon.studentai.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.ai.AiException
import com.edukasyon.studentai.core.ai.AiSafetyErrorParser
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.core.util.ContentDisplayKind
import com.edukasyon.studentai.core.util.MAX_TOOLS_PDF_BYTES
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
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AssignmentIntelligenceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val analyzeAssignment: AiAnalyzeAssignmentUseCase,
    private val saveToPlanner: SaveAssignmentBreakdownToPlannerUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssignmentIntelligenceUiState())
    val uiState: StateFlow<AssignmentIntelligenceUiState> = _uiState.asStateFlow()

    private var pendingImageBase64: String? = null
    private var pendingAttachmentText: String? = null

    fun setInputMode(mode: AssignmentInputMode) {
        _uiState.update {
            it.copy(
                inputMode = mode,
                error = null,
                breakdown = null,
                selectedFileName = null,
            )
        }
        pendingImageBase64 = null
        pendingAttachmentText = null
    }

    fun updateInstructionsText(text: String) {
        _uiState.update { it.copy(instructionsText = text, error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun resetToInput() {
        pendingImageBase64 = null
        pendingAttachmentText = null
        _uiState.value = AssignmentIntelligenceUiState(inputMode = _uiState.value.inputMode)
    }

    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch {
            val name = ChatAttachmentUtils.resolveContentDisplayName(appContext, uri, ContentDisplayKind.PDF)
            _uiState.update {
                it.copy(
                    inputMode = AssignmentInputMode.PDF,
                    selectedFileName = name,
                    error = null,
                    breakdown = null,
                    isAnalyzing = true,
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
                    val jpeg = ChatAttachmentUtils.renderPdfFirstPageAsJpeg(appContext, uri)
                        ?: throw IllegalStateException("Could not render PDF for analysis.")
                    pendingImageBase64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
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
                    breakdown = null,
                    isAnalyzing = true,
                )
            }
            pendingAttachmentText = null

            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read image.")
                if (bytes.isEmpty()) throw IllegalStateException("Could not read image.")
                val reportedMime = appContext.contentResolver.getType(uri)
                val (compressed, mime) = ChatAttachmentUtils.compressImageBytes(bytes, reportedMime)
                if (mime != "image/jpeg" || compressed.isEmpty()) {
                    throw IllegalStateException("Unsupported image format. Use JPG or PNG.")
                }
                pendingImageBase64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
                _uiState.update { it.copy(isAnalyzing = false) }
                analyzePendingInput(fallbackText = _uiState.value.instructionsText.takeIf { it.isNotBlank() })
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
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        successMessage = "Added \"${task.title}\" with ${task.subtasks.size} subtasks to Planner.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Could not save to Planner.")
                }
            }
        }
    }

    private suspend fun analyzePendingInput(fallbackText: String?) {
        _uiState.update { it.copy(isAnalyzing = true, error = null, breakdown = null) }
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
            _uiState.update { it.copy(isAnalyzing = false, breakdown = breakdown) }
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

    private fun mapError(e: Exception): String {
        if (e is AiException && e.cause is retrofit2.HttpException) {
            val http = e.cause as retrofit2.HttpException
            val rawBody = http.response()?.errorBody()?.string()
            return AiSafetyErrorParser.userMessage(http.code(), rawBody)
        }
        return e.message ?: AiSafetyMessages.providerUnavailableMessage()
    }

    companion object {
        private const val TAG = "AssignmentIntelligence"
    }
}
