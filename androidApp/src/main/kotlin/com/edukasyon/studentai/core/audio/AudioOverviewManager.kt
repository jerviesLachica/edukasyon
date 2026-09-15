package com.edukasyon.studentai.core.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.VisibleForTesting
import com.edukasyon.studentai.R
import com.edukasyon.studentai.core.ai.AiChatRequest
import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.TtsRequest
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDeck
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Generates and caches MP3 "Audio Overviews" of a JEVI deck. Two modes:
 *
 *  - **Podcast dialogue** (default): one JEVI chat call writes a two-voice
 *    script for a [PodcastTheme] (`A:`/`B:` lines); the script is audited
 *    client-side ([ScriptAuditor]) before any TTS spend — one regeneration is
 *    allowed if cards were skipped. Each line is synthesized serially with the
 *    speaker's voice + prosody and raw-APPENDED to one `.part` MP3 (sequential
 *    concat; players seek fine), cached per deck content + theme/voice mix.
 *  - **Monologue fallback**: if zero dialogue lines parse (model ignored the
 *    format), the whole script goes through the old chunked single-voice path
 *    with the theme's A voice — the user is never dead-ended.
 *
 * Episodes can be copied to the device Downloads folder ([exportToDevice]) or
 * saved anywhere via SAF ([exportIntent] + [exportToUri]).
 */
@Singleton
class AudioOverviewManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiService: AiService,
    private val api: AiApiService,
) {
    sealed interface Result {
        /** partialCoverage: 1-based card numbers the script skipped (show a snackbar). */
        data class Ready(val file: File, val partialCoverage: List<Int> = emptyList()) : Result
        data class Failed(val message: String) : Result
    }
    sealed class ExportResult {
        data class Saved(val displayPath: String) : ExportResult()
        data class Failed(val message: String) : ExportResult()
    }

    /**
     * @param onProgress optional stage callback for the Generating UI:
     *   (stage, lineDone, lineTotal). lineDone/lineTotal only tick during
     *   [PodcastStatusEvent.Synthesizing]; other stages report (n, n+?) pairs
     *   of (-1, -1). Default null keeps existing call sites untouched.
     */
    suspend fun overviewFor(
        deck: JeviDeck,
        cards: List<Flashcard>,
        theme: PodcastTheme = PodcastThemes.default.first(),
        onProgress: ((PodcastStatusEvent, Int, Int) -> Unit)? = null,
    ): Result {
        if (cards.isEmpty()) return Result.Failed("This deck has no cards to narrate yet.")
        cachedFile(deck, cards, theme)?.let { return Result.Ready(it) }

        val script = runCatching {
            onProgress?.invoke(PodcastStatusEvent.Scripting, -1, -1)
            aiService.chat(
                AiChatRequest(
                    message = scriptRequestFor(theme),
                    subject = deck.title,
                    contextSummary = contextSummaryFor(cards),
                )
            ).reply.trim()
        }.getOrElse { return Result.Failed("Could not generate the script: ${it.message ?: "offline?"}") }
        if (script.isBlank()) return Result.Failed("The tutor returned an empty script.")

        // Parse + audit; one regeneration only when cards were actually skipped.
        var lines = DialogueParser.parse(script)
        var partial: List<Int> = emptyList()
        if (lines.isNotEmpty()) {
            onProgress?.invoke(PodcastStatusEvent.Auditing, -1, -1)
            var report = ScriptAuditor.audit(lines, cards)
            if (!report.ok && report.missingCards.isNotEmpty()) {
                val regen = runCatching {
                    aiService.chat(
                        AiChatRequest(
                            message = report.feedbackForRegen
                                ?: "Rewrite covering every card.",
                            subject = deck.title,
                            contextSummary = contextSummaryFor(cards),
                        )
                    ).reply.trim()
                }.getOrNull()
                if (!regen.isNullOrBlank()) {
                    val regenLines = DialogueParser.parse(regen)
                    val regenReport = ScriptAuditor.audit(regenLines, cards)
                    // keep whichever script covers more cards
                    if (regenReport.missingCards.size <= report.missingCards.size && regenLines.isNotEmpty()) {
                        lines = regenLines
                        report = regenReport
                    }
                }
                if (report.missingCards.isNotEmpty()) partial = report.missingCards
            }
        }

        return runCatching {
            val out = withContext(Dispatchers.IO) {
                val file = fileFor(deck.id, cacheKey(deck, cards, theme))
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".part")
                if (lines.isNotEmpty()) {
                    synthesizeDialogueOrdered(lines, theme, tmp, onProgress)
                } else {
                    // Monologue fallback (AC3): serial chunk path on the A voice —
                    // no jitter, no pause clips: chunk boundaries already pause.
                    FileOutputStream(tmp).use { fos ->
                        for (chunk in splitForSpeech(script)) {
                            api.synthesizeSpeech(
                                TtsRequest(text = chunk, voice = theme.voiceA, rate = theme.prosodyA.rate, pitch = theme.prosodyA.pitch)
                            ).byteStream().use { input -> input.copyTo(fos) }
                        }
                    }
                }
                if (!tmp.renameTo(file)) tmp.delete()
                file
            }
            if (out.exists() && out.length() > 0) Result.Ready(out, partial)
            else Result.Failed("Speech synthesis produced no audio.")
        }.getOrElse {
            Result.Failed("Speech synthesis failed: " + (it.message ?: "check connection"))
        }
    }

    /**
     * Naturalness v2 dialogue path: synthesis requests run with bounded
     * parallelism (SYNTH_CONCURRENCY workers), each line's bytes landing in its
     * own per-line temp file; a second, strictly ordered pass concatenates the
     * temps into [tmp] in script order (per line: audio bytes, then the pause
     * clip — 650ms after a topic break, else 250ms) and deletes them.
     * Concurrency can therefore never reorder the episode. Progress ticks
     * count LINES WRITTEN in the ordered pass, not synthesis completion.
     */
    private suspend fun synthesizeDialogueOrdered(
        lines: List<DialogueParser.Line>,
        theme: PodcastTheme,
        tmp: File,
        onProgress: ((PodcastStatusEvent, Int, Int) -> Unit)?,
    ) = coroutineScope {
        val silenceShort = silenceClip(R.raw.silence_250ms)
        val silenceLong = silenceClip(R.raw.silence_650ms)
        val gate = Semaphore(SYNTH_CONCURRENCY)
        // Fire every line's synthesis (bounded); each writes its own temp file.
        val temps = lines.mapIndexed { index, line ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val prosody = if (line.speaker == 'A') theme.prosodyA else theme.prosodyB
                    val voice = if (line.speaker == 'A') theme.voiceA else theme.voiceB
                    // Deterministic per-line jitter: same (index, base) => same
                    // values, so regenerating an episode reproduces identical takes.
                    val (rate, pitch) = jitterFor(index, prosody.rate, prosody.pitch)
                    val part = File(tmp.parentFile, tmp.name + "." + index + ".piece")
                    runCatching {
                        FileOutputStream(part).use { fos ->
                            // splitForSpeech is a no-op under the cap; guards runaway model lines.
                            for (piece in splitForSpeech(line.text)) {
                                api.synthesizeSpeech(
                                    TtsRequest(text = piece, voice = voice, rate = rate, pitch = pitch)
                                ).byteStream().use { input -> input.copyTo(fos) }
                            }
                        }
                        part
                    }.getOrElse {
                        part.delete()
                        throw it
                    }
                }
            }
        }
        FileOutputStream(tmp).use { fos ->
            var written = 0
            // A failed line rethrows and cancels the scope — the episode aborts
            // exactly like the serial v1 loop did, only faster.
            temps.forEachIndexed { index, deferred ->
                val part = deferred.await()
                try {
                    part.inputStream().use { it.copyTo(fos) }
                    // Long beat BEFORE a topic-start line: write it after the previous
                    // line so the gap lands at the boundary, not after the new topic's
                    // first line.
                    val nextStartsTopic = lines.getOrNull(index + 1)?.topicBreak == true
                    fos.write(if (nextStartsTopic) silenceLong else silenceShort)
                } finally {
                    part.delete()
                }
                written += 1
                onProgress?.invoke(PodcastStatusEvent.Synthesizing, written, lines.size)
            }
        }
    }

    /** Raw MP3 pause clip from res/raw, appended between lines like a tiny chunk. */
    private fun silenceClip(resId: Int): ByteArray =
        runCatching { appContext.resources.openRawResource(resId).use { it.readBytes() } }
            .getOrDefault(ByteArray(0))

    /** Cached overview for the current deck contents + theme/voice mix, or null. */
    fun cachedFile(deck: JeviDeck, cards: List<Flashcard>, theme: PodcastTheme = PodcastThemes.default.first()): File? {
        if (cards.isEmpty()) return null
        val f = fileFor(deck.id, cacheKey(deck, cards, theme))
        return if (f.exists() && f.length() > 0) f else null
    }

    // --- audition previews ----------------------------------------------------

    /**
     * Synthesize [requests] serially into one small preview MP3 under the cache
     * dir, keyed by [key] (repeat taps are instant). Returns null on failure —
     * previews must never surface a hard error.
     */
    suspend fun preview(key: String, requests: List<TtsRequest>): File? = withContext(Dispatchers.IO) {
        val safeKey = key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(64)
        if (safeKey.isEmpty()) return@withContext null
        val dir = File(appContext.cacheDir, PREVIEW_DIR)
        dir.mkdirs()
        val f = File(dir, "$safeKey.mp3")
        if (f.exists() && f.length() > 0) return@withContext f
        val tmp = File(dir, "$safeKey.part")
        runCatching {
            FileOutputStream(tmp).use { fos ->
                for (req in requests) {
                    api.synthesizeSpeech(req).byteStream().use { input -> input.copyTo(fos) }
                }
            }
            if (!tmp.renameTo(f)) {
                tmp.delete()
                null
            } else if (f.length() > 0) f else null
        }.getOrElse {
            tmp.delete()
            null
        }
    }

    // --- export to Downloads / SAF ------------------------------------------

    /**
     * Copies a cached episode into the public Downloads folder.
     * API 29+: MediaStore (scoped storage, no permission). API 24-28: direct
     * public-directory copy; storage-permission failures surface as
     * [ExportResult.Failed] — the in-app cache stays playable regardless.
     */
    suspend fun exportToDevice(file: File, deckTitle: String, themeId: String): ExportResult =
        withContext(Dispatchers.IO) {
            val name = exportedDisplayName(deckTitle, themeId)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = appContext.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Could not create the download file.")
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw IOException("Could not open the download for writing.")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    ExportResult.Saved("$name (Downloads)")
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dest = File(dir, name)
                    file.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                    ExportResult.Saved(dest.absolutePath)
                }
            }.getOrElse {
                if (it is SecurityException) {
                    ExportResult.Failed("Enable storage permission in system settings")
                } else {
                    ExportResult.Failed("Could not save the episode: ${it.message ?: "storage error"}")
                }
            }
        }

    /** SAF "Save as…" launcher; pair the result with [exportToUri]. */
    fun exportIntent(displayName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_TITLE, displayName)
        }

    /** Writes the episode to a user-picked SAF document uri. */
    suspend fun exportToUri(file: File, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Could not open the picked location.")
            ExportResult.Saved(uri.lastPathSegment ?: "saved episode")
        }.getOrElse { ExportResult.Failed("Could not save the episode: ${it.message ?: "write error"}") }
    }

    // --- pure helpers (companion: unit-testable without Android) ------------

    private fun fileFor(deckId: String, hash: String): File {
        val dir = File(appContext.filesDir, OVERVIEW_DIR)
        return File(dir, "deck_${deckId.sanitize()}_$hash.mp3")
    }

    private fun cacheKey(deck: JeviDeck, cards: List<Flashcard>, theme: PodcastTheme): String =
        cacheKeyFor(deck, cards, theme)

    companion object {
        private const val OVERVIEW_DIR = "audio_overviews"
        private const val PREVIEW_DIR = "audio_previews"
        private const val MAX_CHUNK_CHARS = 2800

        // Canned audition lines so a preview sounds like the real show, not a generic "hello world".
        const val THEME_SAMPLE_A = "Hey, want to run through this deck together?"
        const val THEME_SAMPLE_B = "Sure thing. One card at a time, and stop me if I get it wrong."
        const val VOICE_SAMPLE = "Hi, this is how I'll sound in your episode."

        /** Two-line exchange in the theme's exact voice pair + prosody. */
        fun previewRequestsFor(theme: PodcastTheme): List<TtsRequest> = listOf(
            TtsRequest(text = THEME_SAMPLE_A, voice = theme.voiceA, rate = theme.prosodyA.rate, pitch = theme.prosodyA.pitch),
            TtsRequest(text = THEME_SAMPLE_B, voice = theme.voiceB, rate = theme.prosodyB.rate, pitch = theme.prosodyB.pitch),
        )

        /** Single-line audition for one voice, neutral prosody (isolates the timbre). */
        fun previewRequestsForVoice(voiceId: String): List<TtsRequest> =
            listOf(TtsRequest(text = VOICE_SAMPLE, voice = voiceId))

        /** Shared dialogue contract footer appended after every theme prompt. */
        internal const val DIALOGUE_CONTRACT =
            "Return ONLY lines prefixed A: or B: alternating speakers. Every numbered card " +
            "gets a full exchange: one speaker sets it up, the other answers, then a short " +
            "reaction or follow-up (~4-6 lines, ~90-140 words per card). Plain sentences, " +
            "contractions welcome, no markdown, each line <=280 chars. Start immediately " +
            "with the first line, no intro."

        internal fun scriptRequestFor(theme: PodcastTheme): String =
            theme.scriptPrompt + " " + DIALOGUE_CONTRACT

        internal fun contextSummaryFor(cards: List<Flashcard>): String =
            "Deck cards:\n" + cards.mapIndexed { i, c -> "${i + 1}. ${c.question} — ${c.answer}" }.joinToString("\n")

        /** deck + card contents + theme id + both voices + both prosodies (AC2). */
        internal fun cacheKeyFor(deck: JeviDeck, cards: List<Flashcard>, theme: PodcastTheme): String {
            val src = buildString {
                append(deck.title)
                cards.forEach { append('|').append(it.id).append(':').append(it.question).append(':').append(it.answer) }
                append("|th=").append(theme.id)
                append("|va=").append(theme.voiceA)
                append("|vb=").append(theme.voiceB)
                append("|pa=").append(theme.prosodyA.rate).append('/').append(theme.prosodyA.pitch)
                append("|pb=").append(theme.prosodyB.rate).append('/').append(theme.prosodyB.pitch)
                // '|nat2': naturalness v2 (per-line jitter + pause clips) changes the
                // audio for identical inputs — stale v1 episode files must not be served.
                append("|nat2")
            }
            return MessageDigest.getInstance("SHA-256").digest(src.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16)
        }

        /**
         * Naturalness v2 per-line prosody jitter: uniform ±4% rate / ±15Hz pitch
         * around the theme's base (null base = 0), seeded by [lineIndex] so a
         * regenerated episode reproduces identical takes. Clamped to the
         * backend's validation bounds (rate [-50,100], pitch [-100,100] — see
         * validateTtsRequest in backend/ai/TtsService.js).
         */
        @VisibleForTesting
        internal fun jitterFor(
            lineIndex: Int,
            baseRate: String?,
            basePitch: String?,
        ): Pair<String, String> {
            val rnd = Random(lineIndex.toLong() * -0x61C8864680B583B7L)
            val rate = (parseSignedProsody(baseRate) + rnd.nextInt(-RATE_JITTER, RATE_JITTER + 1))
                .coerceIn(RATE_MIN, RATE_MAX)
            val pitch = (parseSignedProsody(basePitch) + rnd.nextInt(-PITCH_JITTER, PITCH_JITTER + 1))
                .coerceIn(PITCH_MIN, PITCH_MAX)
            return ("%+d%%".format(rate)) to ("%+dHz".format(pitch))
        }

        /** "+4%" / "-2Hz" / "4" -> Int; null or unparseable -> 0 (library default). */
        private fun parseSignedProsody(value: String?): Int =
            value?.trim()?.removeSuffix("%")?.removeSuffix("Hz")?.removePrefix("+")?.toIntOrNull() ?: 0

        // --- naturalness v2 tunables (backend bounds mirror TtsService.js) ---
        private const val SYNTH_CONCURRENCY = 6
        private const val RATE_JITTER = 4 // ±%
        private const val PITCH_JITTER = 15 // ±Hz
        private const val RATE_MIN = -50
        private const val RATE_MAX = 100
        private const val PITCH_MIN = -100
        private const val PITCH_MAX = 100

        // 'JEVI <deck> – <Theme>.mp3'
        internal fun exportedDisplayName(deckTitle: String, themeId: String): String {
            val theme = PodcastThemes.byId(themeId)
            val safeTitle = deckTitle
                .replace(Regex("[^A-Za-z0-9 \\-]"), " ")
                .replace(Regex("\\s+"), " ").trim().ifBlank { "Deck" }
            return "JEVI $safeTitle \u2013 ${theme.title}.mp3"
        }

        /** Groups sentences so no single request exceeds the backend text cap. */
        internal fun splitForSpeech(text: String): List<String> {
            val chunks = mutableListOf<String>()
            val sb = StringBuilder()
            var start = 0
            while (start < text.length) {
                val end = minOf(start + MAX_CHUNK_CHARS, text.length)
                val cut = if (end == text.length) end else {
                    lastSentenceBoundary(text, start, end) ?: end
                }
                val piece = text.substring(start, cut).trim()
                if (piece.isNotEmpty()) {
                    if (sb.length + piece.length > MAX_CHUNK_CHARS) {
                        if (sb.isNotBlank()) chunks += sb.toString().trim()
                        sb.setLength(0)
                    }
                    sb.append(piece).append(' ')
                }
                start = cut
            }
            if (sb.isNotBlank()) chunks += sb.toString().trim()
            return chunks.filter { it.isNotBlank() }
        }

        private fun lastSentenceBoundary(text: String, from: Int, to: Int): Int? {
            var i = to - 1
            while (i > from) {
                if (text[i] == '.' || text[i] == '!' || text[i] == '?' || text[i] == '\n') return i + 1
                i--
            }
            return null
        }

        private fun String.sanitize(): String = map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }
}
