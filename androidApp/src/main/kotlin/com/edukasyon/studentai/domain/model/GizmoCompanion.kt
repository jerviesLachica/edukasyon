package com.edukasyon.studentai.domain.model

enum class GizmoMood(val emoji: String, val greeting: String) {
    HAPPY("😊", "Ready to learn together!"),
    EXCITED("🤩", "You're on fire today!"),
    PROUD("🌟", "Amazing progress!"),
    ENCOURAGING("💪", "Keep going — you've got this!"),
    RESTING("😴", "Take a break, then come back stronger."),
    CHEERFUL("✨", "Let's make studying fun!")
}

data class GizmoChatMessage(
    val sender: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentName: String? = null,
    val attachmentIsImage: Boolean = false,
    val reasoning: String? = null,
)

/** Pending attachment selected in the Tutor tab before send. */
data class ChatAttachmentPayload(
    val fileName: String,
    val mimeType: String?,
    val isImage: Boolean,
    val bytes: ByteArray,
    val textContent: String? = null,
)

data class GizmoCompanionState(
    val xp: Int = 0,
    val level: Int = 1,
    val streakDays: Int = 0,
    val mood: GizmoMood = GizmoMood.HAPPY,
) {
    val xpToNextLevel: Int get() = GizmoConstants.xpForLevel(level + 1) - xp
    val xpProgress: Float get() {
        val currentLevelXp = GizmoConstants.xpForLevel(level)
        val nextLevelXp = GizmoConstants.xpForLevel(level + 1)
        val range = (nextLevelXp - currentLevelXp).coerceAtLeast(1)
        return ((xp - currentLevelXp).toFloat() / range).coerceIn(0f, 1f)
    }
}

object GizmoConstants {
    const val XP_CHAT = 5
    const val XP_CORRECT_ANSWER = 10
    const val XP_GENERATE_FLASHCARDS = 15
    const val XP_GENERATE_QUIZ = 10
    const val XP_SAVE_FLASHCARDS = 20
    const val XP_DAILY_STREAK = 25

    fun xpForLevel(level: Int): Int = (level - 1) * 100

    val QUICK_PROMPTS = listOf(
        "Explain photosynthesis simply",
        "Help me study for my exam",
        "Summarize this topic for me",
        "Give me a practice question",
        "How do I stay motivated?",
        "Break down quadratic equations"
    )
}
