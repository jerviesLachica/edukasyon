package com.edukasyon.studentai.core.audio

import android.content.Context
import com.edukasyon.studentai.core.ai.AiChatRequest
import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.TtsRequest
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDeck
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates and caches MP3 "Audio Overviews" of a JEVI deck: the tutor writes a
 * revision script from the deck's cards (one LLM call), synthesized through the
 * backend TTS endpoint into a single playable file, cached per deck content hash
 * so replays work offline.
 */
@Singleton
class AudioOverviewManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiService: AiService,
    private val api: AiApiService,
) {
    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun overviewFor(deck: JeviDeck, cards: List<Flashcard>): Result {
        if (cards.isEmpty()) return Result.Failed("This deck has no cards to narrate yet.")
        cachedFile(deck, cards)?.let { return Result.Ready(it) }

        val script = runCatching {
            val cardLines = cards.joinToString("\n") { "- ${it.question} — ${it.answer}" }
            aiService.chat(
                AiChatRequest(
                    message = "Write a short audio revision script that teaches this deck out loud, " +
                        "covering every card. Plain sentences, no markdown, no headings.",
                    subject = deck.title,
                    contextSummary = "Deck cards:\n$cardLines",
                )
            ).reply.trim()
        }.getOrElse { return Result.Failed("Could not generate the script: ${it.message ?: "offline?"}") }
        if (script.isBlank()) return Result.Failed("The tutor returned an empty script.")

        return runCatching {
            val out = withContext(Dispatchers.IO) {
                val file = fileFor(deck.id, cacheKey(deck, cards))
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".part")
                FileOutputStream(tmp).use { fos ->
                    for (chunk in splitForSpeech(script)) {
                        api.synthesizeSpeech(TtsRequest(text = chunk)).byteStream().use { input ->
                            input.copyTo(fos)
                        }
                    }
                }
                if (!tmp.renameTo(file)) tmp.delete()
                file
            }
            if (out.exists() && out.length() > 0) Result.Ready(out)
            else Result.Failed("Speech synthesis produced no audio.")
        }.getOrElse {
            Result.Failed("Speech synthesis failed: ${it.message ?: "check connection"}")
        }
    }

    /** Cached overview for the current deck contents, or null if not generated yet. */
    fun cachedFile(deck: JeviDeck, cards: List<Flashcard>): File? {
        if (cards.isEmpty()) return null
        val f = fileFor(deck.id, cacheKey(deck, cards))
        return if (f.exists() && f.length() > 0) f else null
    }

    private fun fileFor(deckId: String, hash: String): File {
        val dir = File(appContext.filesDir, OVERVIEW_DIR)
        return File(dir, "deck_${deckId.sanitize()}_$hash.mp3")
    }

    private fun cacheKey(deck: JeviDeck, cards: List<Flashcard>): String {
        val src = buildString {
            append(deck.title)
            cards.forEach { append('|').append(it.id).append(':').append(it.question).append(':').append(it.answer) }
        }
        return MessageDigest.getInstance("SHA-256").digest(src.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        private const val OVERVIEW_DIR = "audio_overviews"
        private const val MAX_CHUNK_CHARS = 2800

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
