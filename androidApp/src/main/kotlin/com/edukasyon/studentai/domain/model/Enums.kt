package com.edukasyon.studentai.domain.model

enum class DayOfWeek(val displayName: String) {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday");

    companion object {
        private val ALIAS_LOOKUP: Map<String, DayOfWeek> = buildMap {
            DayOfWeek.entries.forEach { day ->
                put(day.name, day)
                put(day.displayName.uppercase(), day)
                put(day.name.take(3), day)
                put(day.displayName.take(3).uppercase(), day)
            }
            put("TUES", TUESDAY)
            put("THUR", THURSDAY)
            put("THURS", THURSDAY)
            put("R", THURSDAY)
            put("U", SUNDAY)
        }

        fun fromString(value: String): DayOfWeek? {
            val token = value.trim()
                .substringBefore('/')
                .substringBefore(',')
                .substringBefore('-')
                .trim()
                .trimEnd('.')
            if (token.isEmpty()) return null

            ALIAS_LOOKUP[token.uppercase()]?.let { return it }

            DayOfWeek.entries.find {
                it.name.equals(token, ignoreCase = true) ||
                    it.displayName.equals(token, ignoreCase = true)
            }?.let { return it }

            val upper = token.uppercase()
            if (upper.length >= 3) {
                DayOfWeek.entries.find {
                    it.name.startsWith(upper, ignoreCase = true) ||
                        it.displayName.startsWith(token, ignoreCase = true)
                }?.let { return it }
            }

            token.toIntOrNull()?.let { numeric ->
                return when (numeric) {
                    0 -> SUNDAY
                    in 1..7 -> DayOfWeek.entries[(numeric - 1) % DayOfWeek.entries.size]
                    else -> null
                }
            }

            return null
        }
    }
}

enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    CONFLICT,
    FAILED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER
}

enum class AiModel(val slug: String, val displayName: String, val chatDescription: String) {
    AUTO("auto", "Auto", "Fast general answers"),
    REASONING("nemotron-3.5-lightning-free", "Zen Flash", "Stronger reasoning & vision");

    val isStepModel: Boolean get() = this == REASONING

    companion object {
        fun fromSlug(slug: String): AiModel = when (slug) {
            "mimo-v2.5", "mimo-v2.5-pro" -> AUTO
                        // Legacy slug from before the agnes migration — map to REASONING
                        // so old saved preferences keep working.
                        "agnes-2.5-flash", "step-3.7-flash" -> REASONING
            else -> entries.find { it.slug == slug } ?: AUTO
        }
    }
}

/**
 * Thinking effort for JEVI chat. NONE is non-thinking (fast path);
 * MINIMAL adds minimal reasoning;
 * LOW includes reasoning (default, previous FLASH ≈ fast but now with reasoning);
 * MEDIUM adds step-by-step prompting;
 * HIGH adds longest reasoning with more output tokens.
 */
enum class ThinkingLevel(val slug: String, val displayName: String, val description: String) {
    NONE("none", "None", "Fastest answers, no reasoning"),
    MINIMAL("minimal", "Minimal", "Minimal reasoning before answering"),
    LOW("low", "Low", "Includes reasoning before answering (default)"),
    MEDIUM("medium", "Medium", "Step-by-step reasoning with additional output tokens"),
    HIGH("high", "High", "Longest reasoning with maximum output tokens and detailed step-by-step prompts");

    companion object {
        fun fromSlug(slug: String?): ThinkingLevel =
            entries.find { it.slug == slug } ?: LOW
    }
}
