package com.edukasyon.studentai.core.audio

import com.edukasyon.studentai.domain.model.Flashcard

/**
 * Client-side quality gate for generated podcast scripts, run BEFORE any TTS
 * spend. Flat list of issues + skipped card numbers; `feedbackForRegen` is the
 * complete one-shot regeneration ask (never an unbounded retry loop).
 *
 * Checks:
 *  1. Coverage — per card, a distinctive token sequence (>=6 consecutive words
 *     of its question/answer, or one distinctive word >=8 chars, e.g.
 *     "mitochondria") must appear somewhere in the script.
 *  2. Length gate — at least max(10, cards x 3) lines (plan AC6).
 *  3. Slop fingerprints — banned filler phrases (case-insensitive) and any
 *     line-opening word used on more than 2 lines.
 *  4. Structure — no two adjacent lines from the same speaker (flat-listed
 *     with the rest; simplest contract for lane D to display).
 */
object ScriptAuditor {

    data class Report(
        val coveredCards: List<Int>,
        val missingCards: List<Int>,
        val slopHits: List<String>,
        val lineCount: Int,
        val ok: Boolean,
        val feedbackForRegen: String?,
    )

    internal val bannedPhrases = listOf(
        "in this episode",
        "dive into",
        "great question",
        "let's break down",
        "absolutely!",
        "fantastic",
        "now let's move on",
        "welcome back",
    )

    private const val MIN_TOKEN_SEQ = 6
    private const val DISTINCTIVE_WORD_LEN = 8
    private const val MAX_SAME_OPENER = 2

    fun audit(scriptLines: List<DialogueParser.Line>, cards: List<Flashcard>): Report {
        val haystack = normalize(scriptLines.joinToString(" \n ") { it.text })
        val covered = mutableListOf<Int>()
        val missing = mutableListOf<Int>()
        cards.forEachIndexed { i, card ->
            if (covers(haystack, card)) covered += i + 1 else missing += i + 1
        }

        val hits = mutableListOf<String>()
        for (phrase in bannedPhrases) {
            val n = countOccurrences(haystack, normalize(phrase))
            if (n > 0) hits += "banned phrase \"$phrase\" (${n}x)"
        }
        val openerCounts = scriptLines.mapNotNull { line ->
            firstWord(line.text)?.lowercase()
        }.groupingBy { it }.eachCount()
        for ((word, count) in openerCounts) {
            if (count > MAX_SAME_OPENER) hits += "\"$word\" opens $count lines (max $MAX_SAME_OPENER)"
        }
        val minLines = minLineGate(cards.size)
        if (scriptLines.size < minLines) {
            hits += "only ${scriptLines.size} lines — need at least $minLines for ${cards.size} cards"
        }
        for (j in 1 until scriptLines.size) {
            if (scriptLines[j].speaker == scriptLines[j - 1].speaker) {
                hits += "line ${j + 1} repeats speaker ${scriptLines[j].speaker} (speakers must alternate)"
            }
        }

        val ok = missing.isEmpty() && hits.isEmpty() && scriptLines.isNotEmpty()
        val feedback = if (ok) null else buildFeedback(missing, hits)
        return Report(
            coveredCards = covered,
            missingCards = missing,
            slopHits = hits,
            lineCount = scriptLines.size,
            ok = ok,
            feedbackForRegen = feedback,
        )
    }

    /** AC6 length gate, exposed for the manager and tests. */
    internal fun minLineGate(cardCount: Int): Int = maxOf(10, cardCount * 3)

    private fun buildFeedback(missing: List<Int>, hits: List<String>): String = buildString {
        append("Rewrite the dialogue script. Fix every one of these problems, then return ONLY the corrected script:")
        if (missing.isNotEmpty()) {
            append("\n- You skipped card(s) ${missing.joinToString(" and ")} — cover EVERY card with a full exchange.")
        }
        hits.forEach { append("\n- ").append(it).append('.') }
        append("\n- Keep the A:/B: line format, alternating speakers, plain sentences, no markdown.")
    }

    // --- coverage -----------------------------------------------------------

    internal fun covers(haystackNormalized: String, card: Flashcard): Boolean {
        for (field in listOf(card.question, card.answer)) {
            val tokens = words(field)
            if (tokens.isEmpty()) continue
            if (tokens.size >= MIN_TOKEN_SEQ) {
                for (i in 0..tokens.size - MIN_TOKEN_SEQ) {
                    val seq = tokens.subList(i, i + MIN_TOKEN_SEQ).joinToString(" ")
                    if (haystackNormalized.contains(seq)) return true
                }
            }
            tokens.forEach { w ->
                if (w.length >= DISTINCTIVE_WORD_LEN && haystackNormalized.contains(w)) return true
            }
        }
        return false
    }

    // --- text helpers -------------------------------------------------------

    internal fun normalize(s: String): String =
        s.lowercase()
            .replace('’', '\'')
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '\'') it else ' ' }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun words(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() }

    private fun firstWord(text: String): String? =
        Regex("[\\p{L}']+").find(normalize(text))?.value

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
    }
}
