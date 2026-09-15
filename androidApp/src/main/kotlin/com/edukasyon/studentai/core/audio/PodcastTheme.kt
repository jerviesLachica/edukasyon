package com.edukasyon.studentai.core.audio

/** Stages surfaced through AudioOverviewManager's optional onProgress callback. */
enum class PodcastStatusEvent { Scripting, Auditing, Synthesizing }

/**
 * Per-speaker Edge-TTS prosody. Values are signed strings ("+4%", "-2Hz") as
 * accepted by the backend `/api/ai/tts` contract; null = library default.
 */
data class Prosody(
    val rate: String?,
    val pitch: String?,
)

/**
 * A podcast "show format": a script-writing prompt injected into the JEVI chat
 * call plus a default two-voice pairing (with prosody so A and B differ in more
 * than timbre). Themes ship hardcoded in code (v1); lane D renders the picker.
 */
data class PodcastTheme(
    val id: String,
    val title: String,
    val emoji: String,
    val blurb: String,
    val scriptPrompt: String,
    val voiceA: String,
    val voiceB: String,
    val prosodyA: Prosody,
    val prosodyB: Prosody,
)

object PodcastThemes {
    val default: List<PodcastTheme> = listOf(
        PodcastTheme(
            id = "study_duo",
            title = "Study Duo",
            emoji = "🎧",
            blurb = "A curious student asks, the teacher answers warmly — one card at a time.",
            scriptPrompt = "You are Study Duo: Jevi the student asks naive, honest questions and " +
                "the teacher answers with warmth and concrete examples. Stay in character every line, " +
                "sounding like two friends revising together, never like a lecture.",
            voiceA = "aria",
            voiceB = "guy",
            prosodyA = Prosody(rate = "+4%", pitch = null),
            prosodyB = Prosody(rate = "-2%", pitch = "-2Hz"),
        ),
        PodcastTheme(
            id = "hot_seats",
            title = "Hot Seats",
            emoji = "🔥",
            blurb = "Rapid-fire quiz banter: host fires questions, guest answers and corrects.",
            scriptPrompt = "You are Hot Seats: the host fires rapid quiz questions with playful " +
                "teasing, the guest answers and self-corrects. Snappy banter, short turns, " +
                "keep the energy up without ever sounding scripted.",
            voiceA = "jenny",
            voiceB = "davis",
            prosodyA = Prosody(rate = "+8%", pitch = "+2Hz"),
            prosodyB = Prosody(rate = "-2%", pitch = "-2Hz"),
        ),
        PodcastTheme(
            id = "story_time",
            title = "Story Time",
            emoji = "📖",
            blurb = "Each card becomes a mini-story; a narrator frames it, a character voices it.",
            scriptPrompt = "You are Story Time: the narrator frames each card as a tiny story with " +
                "a setting and a stake, and the character living inside it voices the fact. " +
                "Gentle pacing, vivid but plain language, no purple prose.",
            voiceA = "sonia",
            voiceB = "guy",
            prosodyA = Prosody(rate = "-4%", pitch = null),
            prosodyB = Prosody(rate = "+4%", pitch = "+1Hz"),
        ),
        PodcastTheme(
            id = "debate_club",
            title = "For & Against",
            emoji = "⚖️",
            blurb = "Two hosts argue why each fact matters vs. why students get it wrong.",
            scriptPrompt = "You are For & Against: one host argues why the fact matters, the other " +
                "argues why students commonly get it wrong, and they close each exchange with the " +
                "correct takeaway. Respectful sparring, quick rebuttals, no running gag.",
            voiceA = "aria",
            voiceB = "davis",
            prosodyA = Prosody(rate = "+4%", pitch = null),
            prosodyB = Prosody(rate = "-2%", pitch = "-2Hz"),
        ),
    )

    /** Unknown or null ids fall back to the first theme (study_duo). */
    fun byId(id: String?): PodcastTheme = default.firstOrNull { it.id == id } ?: default.first()
}
