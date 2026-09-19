package com.edukasyon.studentai.core.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.PageNotesRequest
import com.edukasyon.studentai.core.network.PageNotesResponse
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.data.local.dao.PageNoteCacheDao
import com.edukasyon.studentai.data.local.entity.PageNoteCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Status of a single page during the document processing pipeline.
 */
enum class PageStatus {
    PENDING,
    RENDERING,
    READING,
    DONE,
    FAILED,
}

// NOTE: text-layer pages intentionally reuse DONE — a new enum value would
// break exhaustive `when (status)` sites on screens this lane may not edit.

/**
 * Progress event emitted per page as it moves through the pipeline.
 */
data class PageProgress(
    val pageNum: Int,
    val totalPages: Int,
    val status: PageStatus,
    val markdown: String? = null,
    val error: String? = null,
)

/**
 * Result of processing a document: merged page notes with provenance info.
 */
data class DocumentResult(
    val pageNotes: List<PageNote>,
    val mergedMarkdown: String,
    val totalChars: Int,
    val wasFullyCached: Boolean,
    /** Pages read from the PDF text layer for free (zero vision calls). */
    val textLayerPageCount: Int = 0,
    /** Pages read via on-device ML Kit OCR (fast, free, offline). */
    val ocrPageCount: Int = 0,
    /** Pages that needed a rendered image + vision API call. */
    val visionPageCount: Int = 0,
    /** Pages beyond the per-import vision cap that were NOT read (0 = full coverage). */
    val skippedPageCount: Int = 0,
)

data class PageNote(
    val pageNum: Int,
    val markdown: String,
    val sha256: String,
)

/**
 * Parallel document pipeline: renders PDF/images → 3-tier hybrid resolution:
 * 1. Embedded PDF text layer (instant, ~10ms)
 * 2. On-device Google ML Kit OCR (~150-250ms, offline, zero API calls)
 * 3. Cloud Vision API (Gemini Vision with backoff & Room cache)
 *
 * Progress is emitted via [progressFlow] as each page transitions through states.
 */
@Singleton
class DocumentPipeline @Inject constructor(
    private val aiApiService: AiApiService,
    private val pageNoteCacheDao: PageNoteCacheDao,
    private val mlKitTextRecognizer: com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer,
) {
    companion object {
        private const val TAG = "DocumentPipeline"
        const val MAX_VISION_PAGES = 12
        const val HARD_MAX_PAGES = 20
        const val MAX_PAYLOAD_CHARS = 60_000
        const val MAX_IMAGE_DIMENSION = 1600
        private const val JPEG_QUALITY = 80
        private const val CONCURRENCY = 3
        private const val BACKOFF_CONCURRENCY = 2
        private const val MAX_RETRIES = 2

        /** Pages with at least this many decoded text-layer words skip vision entirely. */
        const val MIN_TEXTLAYER_WORDS = 25

        /**
         * Hybrid gate: true when a page's decoded text layer is rich enough to
         * use as-is (free), false when the page must be rendered + read by vision.
         */
        fun pageUsesTextLayer(text: String?): Boolean =
            ChatAttachmentUtils.usableWordCount(text) >= MIN_TEXTLAYER_WORDS
    }

    private val semaphore = Semaphore(CONCURRENCY)

    private val _progressFlow = MutableSharedFlow<PageProgress>(extraBufferCapacity = 32)
    val progressFlow: SharedFlow<PageProgress> = _progressFlow.asSharedFlow()

    /**
     * Process a single document (PDF or image).
     */
    suspend fun processDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        forceVision: Boolean = false,
    ): DocumentResult = processDocuments(
        context = context,
        uris = listOf(uri),
        fileNames = listOf(fileName),
        forceVision = forceVision,
    )

    /**
     * Process one or more documents (multiple images or a PDF) through the hybrid pipeline.
     */
    suspend fun processDocuments(
        context: Context,
        uris: List<Uri>,
        fileNames: List<String> = emptyList(),
        forceVision: Boolean = false,
    ): DocumentResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext DocumentResult(
                pageNotes = emptyList(),
                mergedMarkdown = "",
                totalChars = 0,
                wasFullyCached = false,
            )
        }

        val renderedPages = mutableListOf<RenderedPage>()
        for ((idx, uri) in uris.withIndex()) {
            val name = fileNames.getOrNull(idx) ?: "file_$idx"
            val p = renderPages(context, uri, name)
            renderedPages.addAll(p)
            if (renderedPages.size >= HARD_MAX_PAGES) break
        }

        // Re-number sequentially across all input files
        val pages = renderedPages.take(HARD_MAX_PAGES).mapIndexed { i, p ->
            p.copy(num = i + 1)
        }

        if (pages.isEmpty()) {
            return@withContext DocumentResult(
                pageNotes = emptyList(),
                mergedMarkdown = "",
                totalChars = 0,
                wasFullyCached = false,
            )
        }

        val pageLimit = minOf(pages.size, MAX_VISION_PAGES)
        var textLayerPageCount = 0
        var ocrPageCount = 0

        val cachedResults = mutableListOf<PageNote?>()
        var allResolvedWithoutVision = true

        for (i in 0 until pageLimit) {
            val page = pages[i]

            // Tier 1: Embedded text layer
            val textLayer = page.textLayer?.takeIf { pageUsesTextLayer(it) }
            if (textLayer != null) {
                val sha = "text:" + DocumentPageCache.sha256(page.jpegBytes)
                cachedResults.add(
                    PageNote(
                        pageNum = page.num,
                        markdown = textLayer.trim(),
                        sha256 = sha,
                    )
                )
                textLayerPageCount++
                _progressFlow.emit(
                    PageProgress(page.num, pageLimit, PageStatus.DONE, textLayer.trim())
                )
                continue
            }

            // Tier 2: Check persistent Room cache
            val sha256 = DocumentPageCache.sha256(page.jpegBytes)
            val cached = pageNoteCacheDao.getBySha256(sha256)
            if (cached != null) {
                cachedResults.add(
                    PageNote(
                        pageNum = page.num,
                        markdown = cached.markdown,
                        sha256 = sha256,
                    )
                )
                _progressFlow.emit(
                    PageProgress(page.num, pageLimit, PageStatus.DONE, cached.markdown)
                )
                continue
            }

            // Tier 3: On-device ML Kit OCR (fast, local, offline)
            if (!forceVision) {
                _progressFlow.emit(PageProgress(page.num, pageLimit, PageStatus.READING))
                val ocrResult = mlKitTextRecognizer.recognizeFromBytes(page.jpegBytes)
                if (ocrResult.success && pageUsesTextLayer(ocrResult.text)) {
                    val markdown = ocrResult.text.trim()
                    runCatching {
                        pageNoteCacheDao.upsert(
                            PageNoteCacheEntity(
                                sha256 = sha256,
                                markdown = markdown,
                                pageNum = page.num,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    cachedResults.add(
                        PageNote(pageNum = page.num, markdown = markdown, sha256 = sha256)
                    )
                    ocrPageCount++
                    _progressFlow.emit(
                        PageProgress(page.num, pageLimit, PageStatus.DONE, markdown)
                    )
                    continue
                }
            }

            // Needs cloud vision
            cachedResults.add(null)
            allResolvedWithoutVision = false
        }

        if (allResolvedWithoutVision) {
            Log.i(TAG, "All $pageLimit pages resolved without cloud vision ($textLayerPageCount text layer, $ocrPageCount ML Kit OCR, ${pageLimit - textLayerPageCount - ocrPageCount} cache hits)")
            val merged = buildMergedMarkdown(cachedResults.filterNotNull())
            return@withContext DocumentResult(
                pageNotes = cachedResults.filterNotNull(),
                mergedMarkdown = merged,
                totalChars = merged.length,
                wasFullyCached = textLayerPageCount == 0 && ocrPageCount == 0,
                textLayerPageCount = textLayerPageCount,
                ocrPageCount = ocrPageCount,
                visionPageCount = 0,
                skippedPageCount = (pages.size - pageLimit).coerceAtLeast(0),
            )
        }

        // Fan-out remaining pages that need cloud vision
        val results = arrayOfNulls<PageNote>(pageLimit)
        for (i in 0 until pageLimit) {
            cachedResults[i]?.let { results[i] = it }
        }

        val uncachedIndices = (0 until pageLimit).filter { results[it] == null }
        val visionPageCount = uncachedIndices.size

        for (index in uncachedIndices) {
            val pageInfo = pages[index]
            _progressFlow.emit(
                PageProgress(pageInfo.num, pageLimit, PageStatus.RENDERING)
            )
        }

        // Launch uncached pages concurrently with semaphore
        val jobs = uncachedIndices.map { index ->
            CoroutineScope(Dispatchers.IO).launch {
                semaphore.withPermit {
                    processPageWithRetry(
                        pageInfo = pages[index],
                        pageNum = pages[index].num,
                        total = pageLimit,
                        resultSlot = results,
                        index = index,
                    )
                }
            }
        }
        jobs.forEach { it.join() }

        val pageNotes = results.filterNotNull().sortedBy { it.pageNum }
        val merged = buildMergedMarkdown(pageNotes)
        val skipped = (pages.size - pageLimit).coerceAtLeast(0)
        Log.i(
            TAG,
            "Document read finished: $textLayerPageCount text layer, $ocrPageCount local OCR, " +
                "$visionPageCount cloud vision, skipped $skipped",
        )

        if (merged.length > MAX_PAYLOAD_CHARS) {
            Log.w(TAG, "Merged markdown is ${merged.length} chars, exceeding $MAX_PAYLOAD_CHARS cap")
        }

        DocumentResult(
            pageNotes = pageNotes,
            mergedMarkdown = merged,
            totalChars = merged.length,
            wasFullyCached = false,
            textLayerPageCount = textLayerPageCount,
            ocrPageCount = ocrPageCount,
            visionPageCount = visionPageCount,
            skippedPageCount = skipped,
        )
    }

    private suspend fun processPageWithRetry(
        pageInfo: RenderedPage,
        pageNum: Int,
        total: Int,
        resultSlot: Array<PageNote?>,
        index: Int,
    ) {
        val sha256 = DocumentPageCache.sha256(pageInfo.jpegBytes)
        val base64 = DocumentPageCache.toBase64(pageInfo.jpegBytes)

        for (attempt in 0..MAX_RETRIES) {
            try {
                _progressFlow.emit(PageProgress(pageNum, total, PageStatus.READING))

                val response = aiApiService.pageNotes(PageNotesRequest(imageBase64 = base64))
                val markdown = response.markdown

                // Persist to cache
                pageNoteCacheDao.upsert(
                    PageNoteCacheEntity(
                        sha256 = sha256,
                        markdown = markdown,
                        pageNum = pageNum,
                        createdAt = System.currentTimeMillis(),
                    )
                )

                val note = PageNote(pageNum = pageNum, markdown = markdown, sha256 = sha256)
                resultSlot[index] = note

                _progressFlow.emit(PageProgress(pageNum, total, PageStatus.DONE, markdown))
                Log.i(TAG, "Page $pageNum/$total: read ${markdown.length} chars")
                return
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                if ((code == 429 || code in 500..599) && attempt < MAX_RETRIES) {
                    val backoffMs = calculateBackoff(attempt, code)
                    Log.w(TAG, "Page $pageNum: HTTP $code, retry ${attempt + 1} in ${backoffMs}ms")
                    delay(backoffMs)
                    continue
                }
                Log.e(TAG, "Page $pageNum: HTTP $code after ${attempt + 1} attempts", e)
                _progressFlow.emit(
                    PageProgress(pageNum, total, PageStatus.FAILED, error = "HTTP $code")
                )
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Page $pageNum: failed", e)
                _progressFlow.emit(
                    PageProgress(pageNum, total, PageStatus.FAILED, error = e.message)
                )
                return
            }
        }
    }

    /**
     * Jittered exponential backoff for 429/5xx retries.
     */
    private fun calculateBackoff(attempt: Int, httpCode: Int): Long {
        val baseMs = if (httpCode == 429) 2000L else 1000L
        val exponential = baseMs * (1L shl attempt)
        val jitter = Random.nextLong(0, exponential / 2)
        return exponential + jitter
    }

    private fun buildMergedMarkdown(pageNotes: List<PageNote>): String {
        return pageNotes.joinToString("\n\n---\n\n") { note ->
            "**Page ${note.pageNum}:**\n\n${note.markdown}"
        }
    }

    /**
     * Render PDF pages or images to JPEG byte arrays. Plain images (jpg/png URIs from
     * camera/gallery) decode to a single page, downsampled to MAX_IMAGE_DIMENSION.
     * PDFs also extract their embedded text layer per page if available.
     */
    private suspend fun renderPages(
        context: Context,
        uri: Uri,
        fileName: String,
    ): List<RenderedPage> = withContext(Dispatchers.IO) {
        // Not a PDF? Treat as image: decode + downsample to one rendered page.
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching null
            pfd.use { fd ->
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return@use null
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= MAX_IMAGE_DIMENSION) sample *= 2
                val bmp = android.graphics.BitmapFactory.decodeFileDescriptor(
                    fd.fileDescriptor, null,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return@use null
                val jpeg = encodeJpeg(bmp)
                bmp.recycle()
                listOf(RenderedPage(num = 1, jpegBytes = jpeg, textLayer = null))
            }
        }.getOrNull()?.let { return@withContext it }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext emptyList()

        pfd.use { fd ->
            runCatching {
                val textLayerPages: List<String?> = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size >= 5 && String(bytes, 0, 5, Charsets.ISO_8859_1) == "%PDF-") {
                        ChatAttachmentUtils.extractEmbeddedPdfTextPerPage(bytes)
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())

                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching emptyList()
                    val pageLimit = minOf(renderer.pageCount, HARD_MAX_PAGES)
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
                                val bitmap = Bitmap.createBitmap(
                                    width, height, Bitmap.Config.ARGB_8888
                                )
                                page.render(
                                    bitmap, null, null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                val jpegBytes = encodeJpeg(bitmap)
                                bitmap.recycle()
                                add(
                                    RenderedPage(
                                        num = pageIndex + 1,
                                        jpegBytes = jpegBytes,
                                        textLayer = textLayerPages.getOrNull(pageIndex),
                                    )
                                )
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }
}

data class RenderedPage(
    val num: Int,
    val jpegBytes: ByteArray,
    val textLayer: String? = null,
)
