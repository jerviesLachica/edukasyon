package com.edukasyon.studentai.core.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class MlKitTextRecognizer @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeFromBytes(bytes: ByteArray): MlKitTextResult = withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) {
            return@withContext MlKitTextResult(text = "", blockCount = 0, success = false, error = "Empty image")
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@withContext MlKitTextResult(text = "", blockCount = 0, success = false, error = "Could not decode image")
        try {
            recognizeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): MlKitTextResult = withContext(Dispatchers.Default) {
        runCatching {
            // Upscale small images for better OCR accuracy on dense text (e.g., schedules)
            val processedBitmap = if (bitmap.width < MIN_OCR_DIMENSION || bitmap.height < MIN_OCR_DIMENSION) {
                val scale = maxOf(
                    MIN_OCR_DIMENSION.toFloat() / bitmap.width,
                    MIN_OCR_DIMENSION.toFloat() / bitmap.height
                )
                val scaledW = (bitmap.width * scale).toInt()
                val scaledH = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            } else {
                bitmap
            }

            val image = InputImage.fromBitmap(processedBitmap, 0)
            val visionText = recognizer.process(image).await()

            if (processedBitmap !== bitmap) {
                processedBitmap.recycle()
            }

            MlKitTextResult(
                text = visionText.text.trim(),
                blockCount = visionText.textBlocks.size,
                success = true,
            )
        }.getOrElse { error ->
            MlKitTextResult(
                text = "",
                blockCount = 0,
                success = false,
                error = error.message ?: "Text recognition failed",
            )
        }
    }

    suspend fun recognizeFromUri(context: Context, uri: Uri): MlKitTextResult = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(image).await()
            MlKitTextResult(
                text = visionText.text.trim(),
                blockCount = visionText.textBlocks.size,
                success = true,
            )
        }.getOrElse { error ->
            MlKitTextResult(
                text = "",
                blockCount = 0,
                success = false,
                error = error.message ?: "Text recognition failed",
            )
        }
    }

    /** OCR on multiple page images (e.g. scanned PDF pages). */
    suspend fun recognizeFromPageImages(pageImages: List<ByteArray>): MlKitTextResult {
        if (pageImages.isEmpty()) {
            return MlKitTextResult(text = "", blockCount = 0, success = false, error = "No pages")
        }
        val parts = mutableListOf<String>()
        var totalBlocks = 0
        for ((index, pageBytes) in pageImages.withIndex()) {
            val pageResult = recognizeFromBytes(pageBytes)
            totalBlocks += pageResult.blockCount
            if (pageResult.text.isNotBlank()) {
                if (pageImages.size > 1) {
                    parts.add("--- Page ${index + 1} ---\n${pageResult.text}")
                } else {
                    parts.add(pageResult.text)
                }
            }
        }
        val combined = parts.joinToString("\n\n").trim()
        return MlKitTextResult(
            text = combined,
            blockCount = totalBlocks,
            success = combined.isNotEmpty(),
            error = if (combined.isEmpty()) "No text detected on pages" else null,
        )
    }

    private companion object {
        /** ML Kit performs better on larger images for small text recognition. */
        private const val MIN_OCR_DIMENSION = 1200
    }
}
