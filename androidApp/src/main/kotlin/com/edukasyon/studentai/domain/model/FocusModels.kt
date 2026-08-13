package com.edukasyon.studentai.domain.model

import kotlinx.serialization.Serializable

enum class FocusPreset(val focusMinutes: Int, val breakMinutes: Int, val label: String) {
    POMODORO_25_5(25, 5, "25 / 5"),
    POMODORO_45_10(45, 10, "45 / 10"),
    CUSTOM(25, 5, "Custom");

    companion object {
        fun resolve(focusMinutes: Int, breakMinutes: Int): FocusPreset = when {
            focusMinutes == 25 && breakMinutes == 5 -> POMODORO_25_5
            focusMinutes == 45 && breakMinutes == 10 -> POMODORO_45_10
            else -> CUSTOM
        }
    }
}

enum class FocusBlockType {
    STUDY,
    BREAK,
    REVIEW;

    companion object {
        fun fromString(raw: String?): FocusBlockType {
            val normalized = raw?.trim()?.uppercase()?.replace('-', '_') ?: return STUDY
            return when {
                normalized.contains("BREAK") -> BREAK
                normalized.contains("REVIEW") -> REVIEW
                else -> STUDY
            }
        }
    }
}

@Serializable
enum class FocusMode {
    MANUAL,
    AI_PLAN,
}

enum class FocusTimerPhase {
    FOCUS,
    BREAK,
    BLOCK,
    COMPLETE,
}

data class FocusBlock(
    val startMinute: Int,
    val endMinute: Int,
    val activity: String,
    val type: FocusBlockType,
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(1)
}

data class FocusPlan(
    val totalMinutes: Int,
    val blocks: List<FocusBlock>,
    val breakMinutesBetween: Int = 5,
)

data class FocusPlanContext(
    val totalMinutes: Int,
    val subjects: List<String>,
    val upcomingExams: List<String> = emptyList(),
    val weakAreas: List<String> = emptyList(),
    val userPrompt: String? = null,
)

@Serializable
data class FocusSessionRecord(
    val id: String,
    val mode: FocusMode,
    val subjectLabel: String?,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val completedCycles: Int,
    val totalFocusMinutes: Int,
    val completedAt: Long,
)
