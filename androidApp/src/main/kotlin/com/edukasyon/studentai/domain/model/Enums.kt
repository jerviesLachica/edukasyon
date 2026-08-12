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
        fun fromString(value: String): DayOfWeek? =
            entries.find { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
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
