package com.edukasyon.studentai.core.audio

/**
 * Parses a podcast script into speaker-tagged lines.
 *
 * Accepted per line (case-insensitive; leading "- ", "* ", "> " bullets and
 * "**" markdown-bold markers tolerated; blank and unparsable lines skipped):
 *  - `A: text` / `B: text` — explicit speaker tags.
 *  - `Host: text` / `Prof: text` / `Chen: text` — named speakers: the FIRST
 *    new label maps to slot A, the SECOND new label to slot B; further new
 *    labels also fold into B (two voices max in v1).
 *
 * Returns emptyList() when nothing parses — callers fall back to
 * single-voice monologue (today's behavior) instead of dead-ending the user.
 */
object DialogueParser {

    data class Line(val speaker: Char, val text: String)

    // label = up to 30 chars starting/ending with a letter (dots/apostrophes/hyphens/space
    // allowed inside, e.g. "Prof. Chen" or "Co-host"), followed by ':' or '—'/'–'.
    private val markerRegex =
        Regex("""^\s*[*\-»>]*\s*\**\s*([A-Za-z][A-Za-z .''-]{0,29})\s*[:：]\s*(.*)$""")

    fun parse(script: String): List<Line> {
        if (script.isBlank()) return emptyList()
        val assigned = HashMap<String, Char>() // label -> slot, first-occurrence order
        val out = mutableListOf<Line>()
        for (raw in script.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = markerRegex.find(line) ?: continue
            val label = m.groupValues[1].trim().trimEnd('.').uppercase()
            val text = clean(m.groupValues[2])
            if (label.isEmpty() || text.isEmpty()) continue
            val speaker = if (label == "A" || label == "B") {
                label[0]
            } else {
                assigned.getOrPut(label) {
                    val used = assigned.values.toSet()
                    when {
                        'A' !in used -> 'A'
                        else -> 'B'
                    }
                }
            }
            out += Line(speaker, text)
        }
        return out
    }

    private fun clean(text: String): String =
        text.replace("**", "")
            .replace("__", "")
            .trim()
            .trimStart('-', '»', '>')
            .trim()
}
