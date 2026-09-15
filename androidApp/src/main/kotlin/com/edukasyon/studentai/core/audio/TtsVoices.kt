package com.edukasyon.studentai.core.audio

/**
 * Static, offline-capable voice catalog for the podcast A/B pickers (Lane D).
 * Mirrors the backend `VOICE_CATALOG` in `backend/ai/TtsService.js` (the
 * public, non-alias entries of `GET /api/ai/tts/voices`) so the pickers work
 * without a network call; ids must stay exactly in sync with the server
 * allowlist or synthesis 400s.
 */
data class TtsVoice(
    val id: String,
    val label: String,
    val gender: String,
    val locale: String,
)

object TtsVoices {
    val catalog: List<TtsVoice> = listOf(
        TtsVoice(id = "aria", label = "Aria", gender = "female", locale = "en-US"),
        TtsVoice(id = "guy", label = "Guy", gender = "male", locale = "en-US"),
        TtsVoice(id = "jenny", label = "Jenny", gender = "female", locale = "en-US"),
        TtsVoice(id = "davis", label = "Davis", gender = "male", locale = "en-US"),
        TtsVoice(id = "sonia", label = "Sonia", gender = "female", locale = "en-GB"),
    )

    /** "Aria · F · en-US" for pickers; falls back to the raw id if unknown. */
    fun display(voiceId: String): String {
        val v = catalog.firstOrNull { it.id == voiceId } ?: return voiceId
        val gender = if (v.gender.equals("female", ignoreCase = true)) "F" else "M"
        return "${v.label} · $gender · ${v.locale}"
    }

    fun isKnown(voiceId: String): Boolean = catalog.any { it.id == voiceId }
}
