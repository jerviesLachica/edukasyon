package com.edukasyon.studentai.core.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.PageNotesRequest
import com.edukasyon.studentai.core.network.PageNotesResponse
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
)

data class PageNote(
    val pageNum: Int,
    val markdown: String,
    val sha256: String,
)

/**
 * Parallel document pipeline: renders PDF pages → fan-out vision API calls
 * (Semaphore(3) concurrency, jittered backoff on 429/5xx) → per-page SHA256 cache in Room.
 *
 * Progress is emitted via [progressFlow] as each page transitions through states.
 */
@Singleton
class DocumentPipeline @Inject constructor(
    private val aiApiService: AiApiService,
    private val pageNoteCacheDao: PageNoteCacheDao,
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
    }

    private val semaphore = Semaphore(CONCURRENCY)

    private val _progressFlow = MutableSharedFlow<PageProgress>(extraBufferCapacity = 32)
    val progressFlow: SharedFlow<PageProgress> = _progressFlow.asSharedFlow()

    /**
     * Process a document (PDF or image) through the vision-first pipeline.
     *
     * @param context Android context for content resolver access
     * @param uri URI of the PDF or image file
     * @param fileName Original file name for logging
     * @return DocumentResult with page notes, merged markdown, and cache status
     */
    suspend fun processDocument(
        context: Context,
        uri: Uri,
        fileName: String,
    ): DocumentResult = withContext(Dispatchers.IO) {
        val pages = renderPages(context, uri, fileName)
        if (pages.isEmpty()) {
            return@withContext DocumentResult(
                pageNotes = emptyList(),
                mergedMarkdown = "",
                totalChars = 0,
                wasFullyCached = false,
            )
        }

        val pageLimit = minOf(pages.size, MAX_VISION_PAGES)
        if (pages.size > MAX_VISION_PAGES) {
            Log.w(TAG, "Document has ${pages.size} pages, processing first $MAX_VISION_PAGES (cap)")
        }
        if (pages.size > HARD_MAX_PAGES) {
            Log.w(TAG, "Document has ${pages.size} pages, hard cap is $HARD_MAX_PAGES — some pages will be silently dropped")
        }

        // Check cache for all pages — zero AI calls on cache hit
        val cachedResults = mutableListOf<PageNote?>()
        var allCached = true
        for (i in 0 until pageLimit) {
            val sha256 = DocumentPageCache.sha256(pages[i].jpegBytes)
            val cached = pageNoteCacheDao.getBySha256(sha256)
            if (cached != null) {
                cachedResults.add(
                    PageNote(
                        pageNum = pages[i].num,
                        markdown = cached.markdown,
                        sha256 = sha256,
                    )
                )
                _progressFlow.emit(
                    PageProgress(pages[i].num, pageLimit, PageStatus.DONE, cached.markdown)
                )
            } else {
                cachedResults.add(null)
                allCached = false
            }
        }

        if (allCached) {
            Log.i(TAG, "All $pageLimit pages from cache — zero AI calls")
            val merged = buildMergedMarkdown(cachedResults.filterNotNull())
            return@withContext DocumentResult(
                pageNotes = cachedResults.filterNotNull(),
                mergedMarkdown = merged,
                totalChars = merged.length,
                wasFullyCached = true,
            )
        }

        // Fan-out uncached pages with semaphore-limited concurrency
        val results = arrayOfNulls<PageNote>(pageLimit)
        // Copy cached results into the array
        for (i in 0 until pageLimit) {
            cachedResults[i]?.let { results[i] = it }
        }

        val uncachedIndices = (0 until pageLimit).filter { results[it] == null }

        for (index in uncachedIndices) {
            val pageInfo = pages[index]
            _progressFlow.emit(
                PageProgress(pageInfo.num, pageLimit, PageStatus.RENDERING)
            )
        }

        // Launch all uncached pages concurrently with semaphore
        val jobs = uncachedIndices.map { index ->
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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

        // Warn if exceeding 60k chars
        if (merged.length > MAX_PAYLOAD_CHARS) {
            Log.w(TAG, "Merged markdown is ${merged.length} chars, exceeding $MAX_PAYLOAD_CHARS cap")
        }

        DocumentResult(
            pageNotes = pageNotes,
            mergedMarkdown = merged,
            totalChars = merged.length,
            wasFullyCached = false,
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
     * Render PDF pages to JPEG byte arrays.
     */
    private suspend fun renderPages(
        context: Context,
        uri: Uri,
        fileName: String,
    ): List<RenderedPage> = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext emptyList()

        pfd.use { fd ->
            runCatching {
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
)
