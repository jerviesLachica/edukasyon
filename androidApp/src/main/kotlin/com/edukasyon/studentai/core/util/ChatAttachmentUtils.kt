package com.edukasyon.studentai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Max raw bytes read from content URI before compression. */
const val MAX_CHAT_ATTACHMENT_BYTES = 4 * 1024 * 1024

/** Target max payload after JPEG compression (~3 MB base64 stays under typical API limits). */
private const val TARGET_IMAGE_BYTES = 900_000
private const val JPEG_QUALITY_START = 85
private const val JPEG_QUALITY_MIN = 55
private const val MAX_IMAGE_DIMENSION = 1600

object ChatAttachmentUtils {

    fun detectImageMime(bytes: ByteArray, fileName: String, reportedMime: String?): String? {
        if (reportedMime?.startsWith("image/") == true) return reportedMime
        val magic = detectImageMimeFromBytes(bytes)
        if (magic != null) return magic
        return when (fileName.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> null
        }
    }

    fun isImageAttachment(bytes: ByteArray, fileName: String, reportedMime: String?): Boolean =
        detectImageMime(bytes, fileName, reportedMime) != null

    fun isPdf(reportedMime: String?, fileName: String): Boolean =
        reportedMime == "application/pdf" || fileName.substringAfterLast('.').equals("pdf", ignoreCase = true)

    fun readTextContent(bytes: ByteArray, mime: String?): String? {
        if (mime?.startsWith("text/") != true && mime != "application/json") return null
        return runCatching { String(bytes, Charsets.UTF_8).take(8000) }.getOrNull()
    }

    /**
     * Renders the first PDF page as a JPEG for vision models when text extraction is unavailable.
     */
    fun renderPdfFirstPageAsJpeg(context: Context, uri: Uri): ByteArray? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { fd ->
            runCatching {
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val scale = minOf(
                            MAX_IMAGE_DIMENSION.toFloat() / page.width,
                            MAX_IMAGE_DIMENSION.toFloat() / page.height,
                            2f,
                        ).coerceAtLeast(1f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        compressImageBytes(bitmap, "image/jpeg").first
                    }
                }
            }.getOrNull()
        }
    }

    fun compressImageBytes(bytes: ByteArray, mime: String?): Pair<ByteArray, String> {
        val resolvedMime = detectImageMime(bytes, "attachment", mime) ?: "image/jpeg"
        if (bytes.size <= TARGET_IMAGE_BYTES && resolvedMime == "image/jpeg") {
            return bytes to resolvedMime
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes to resolvedMime
        return compressImageBytes(bitmap, resolvedMime)
    }

    fun compressImageBytes(bitmap: Bitmap, mime: String): Pair<ByteArray, String> {
        val scaled = scaleDown(bitmap)
        var quality = JPEG_QUALITY_START
        var output = encodeJpeg(scaled, quality)
        while (output.size > TARGET_IMAGE_BYTES && quality > JPEG_QUALITY_MIN) {
            quality -= 10
            output = encodeJpeg(scaled, quality)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return output to "image/jpeg"
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }

    private fun detectImageMimeFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "image/gif"
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"
            else -> null
        }
    }
}
