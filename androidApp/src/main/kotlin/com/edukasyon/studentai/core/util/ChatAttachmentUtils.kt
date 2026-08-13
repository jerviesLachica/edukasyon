package com.edukasyon.studentai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

enum class ContentDisplayKind {
    IMAGE,
    PDF,
    FILE,
}

/** Max raw bytes read from content URI before compression. */
const val MAX_CHAT_ATTACHMENT_BYTES = 4 * 1024 * 1024

/** Max PDF size for Tools tab upload and text extraction. */
const val MAX_TOOLS_PDF_BYTES = 8 * 1024 * 1024

/** Max PDF pages sent to vision models for OCR-style extraction. */
const val MAX_PDF_VISION_PAGES = 5

/** Target max payload after JPEG compression (~3 MB base64 stays under typical API limits). */
private const val TARGET_IMAGE_BYTES = 900_000
private const val JPEG_QUALITY_START = 85
private const val JPEG_QUALITY_MIN = 55
private const val MAX_IMAGE_DIMENSION = 1600

object ChatAttachmentUtils {

    private val internalDocumentIdPattern =
        Regex("^(image|document|raw|msf|com\\.android\\.providers\\.media\\.documents/document):\\d+$", RegexOption.IGNORE_CASE)

    /**
     * Resolves a human-readable label for a content [Uri].
     * Document-provider URIs often expose internal ids like `image:51277` — those are replaced
     * with friendly fallbacks such as "Image selected".
     */
    fun resolveContentDisplayName(context: Context, uri: Uri, kind: ContentDisplayKind): String {
        val fromProvider = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) return@use null
            cursor.getString(index)?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (fromProvider != null && !isInternalDocumentId(fromProvider)) return fromProvider

        val segment = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (segment != null && !isInternalDocumentId(segment)) return segment

        return when (kind) {
            ContentDisplayKind.IMAGE -> "Image selected"
            ContentDisplayKind.PDF -> "PDF selected"
            ContentDisplayKind.FILE -> "File selected"
        }
    }

    private fun isInternalDocumentId(name: String): Boolean = internalDocumentIdPattern.matches(name)

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
     * Best-effort extraction of embedded text from text-based PDFs (no OCR).
     * Returns null when the PDF appears scanned or has insufficient extractable text.
     */
    fun extractEmbeddedPdfText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val chunks = mutableListOf<String>()
        var index = 0
        while (index < raw.length) {
            when (raw[index]) {
                '(' -> {
                    val parsed = parsePdfLiteralString(raw, index)
                    if (parsed != null) {
                        val (text, nextIndex) = parsed
                        if (text.length >= 2 && text.any { it.isLetterOrDigit() }) {
                            chunks.add(text)
                        }
                        index = nextIndex
                    } else {
                        index++
                    }
                }
                '<' -> {
                    val parsed = parsePdfHexString(raw, index)
                    if (parsed != null) {
                        val (text, nextIndex) = parsed
                        if (text.length >= 2 && text.any { it.isLetterOrDigit() }) {
                            chunks.add(text)
                        }
                        index = nextIndex
                    } else {
                        index++
                    }
                }
                else -> index++
            }
        }
        val joined = chunks
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return joined.takeIf { it.length >= 150 }
    }

    /**
     * Renders the first PDF page as a JPEG for vision models when text extraction is unavailable.
     */
    fun renderPdfFirstPageAsJpeg(context: Context, uri: Uri): ByteArray? =
        renderPdfPagesAsJpeg(context, uri, maxPages = 1).firstOrNull()

    /** Renders up to [maxPages] PDF pages as JPEG images for vision-based text extraction. */
    fun renderPdfPagesAsJpeg(context: Context, uri: Uri, maxPages: Int = MAX_PDF_VISION_PAGES): List<ByteArray> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return pfd.use { fd ->
            runCatching {
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching emptyList<ByteArray>()
                    val pageLimit = minOf(renderer.pageCount, maxPages.coerceAtLeast(1))
                    buildList {
                        for (pageIndex in 0 until pageLimit) {
                            renderer.openPage(pageIndex).use { page ->
                                val scale = minOf(
                                    MAX_IMAGE_DIMENSION.toFloat() / page.width,
                                    MAX_IMAGE_DIMENSION.toFloat() / page.height,
                                    2f,
                                ).coerceAtLeast(1f)
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                add(compressImageBytes(bitmap, "image/jpeg").first)
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun parsePdfLiteralString(raw: String, start: Int): Pair<String, Int>? {
        if (raw.getOrNull(start) != '(') return null
        val builder = StringBuilder()
        var depth = 1
        var index = start + 1
        while (index < raw.length && depth > 0) {
            when (val char = raw[index]) {
                '\\' -> {
                    if (index + 1 >= raw.length) return null
                    builder.append(
                        when (val escaped = raw[index + 1]) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> escaped
                        },
                    )
                    index += 2
                }
                '(' -> {
                    depth++
                    builder.append('(')
                    index++
                }
                ')' -> {
                    depth--
                    if (depth > 0) builder.append(')')
                    index++
                }
                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return builder.toString() to index
    }

    private fun parsePdfHexString(raw: String, start: Int): Pair<String, Int>? {
        if (raw.getOrNull(start) != '<') return null
        val end = raw.indexOf('>', start + 1)
        if (end < 0) return null
        val hex = raw.substring(start + 1, end).replace(Regex("\\s+"), "")
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val byte = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            bytes[i] = byte.toByte()
        }
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        return text to end + 1
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
