package com.edukasyon.studentai.core.mlkit

import android.content.Context
import android.net.Uri
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfOcrHelper @Inject constructor(
    private val textRecognizer: MlKitTextRecognizer,
) {

    companion object {
        const val MAX_PDF_PAGES = 5
        const val MIN_USABLE_TEXT_LENGTH = 50
    }

    /**
     * Extract text from a PDF Uri.
     * Strategy:
     * 1. Try embedded text extraction (fast, for text-based PDFs)
     * 2. If insufficient, render pages to images and run ML Kit OCR
     * Returns the extracted text, or null on failure.
     */
    suspend fun extractTextFromPdf(
        context: Context,
        uri: Uri,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            // Step 1: try embedded text extraction
            val embedded = ChatAttachmentUtils.extractEmbeddedPdfText(bytes)
            if (!embedded.isNullOrBlank() && embedded.length >= MIN_USABLE_TEXT_LENGTH) {
                return@withContext embedded
            }

            // Step 2: render pages to images and OCR
            val pageImages = ChatAttachmentUtils.renderPdfPagesAsJpeg(
                context, uri, maxPages = MAX_PDF_PAGES,
            )
            if (pageImages.isEmpty()) return@withContext null

            val ocrResult = textRecognizer.recognizeFromPageImages(pageImages)
            ocrResult.text.takeIf { it.isNotBlank() && it.length >= MIN_USABLE_TEXT_LENGTH }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Recognize text from an image (camera scan or gallery image).
     */
    suspend fun recognizeImage(
        context: Context,
        uri: Uri,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val result = textRecognizer.recognizeFromBytes(bytes)
            result.text.takeIf { it.isNotBlank() && result.success }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}