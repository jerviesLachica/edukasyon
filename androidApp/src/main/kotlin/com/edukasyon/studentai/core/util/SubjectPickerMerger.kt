package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Subject
import java.util.UUID

object SubjectPickerMerger {
    private const val DEFAULT_SUBJECT_COLOR = "#3949AB"

    fun stableSubjectIdForScheduleName(name: String): String {
        val normalized = name.trim().lowercase()
        return UUID.nameUUIDFromBytes("schedule-subject:$normalized".toByteArray()).toString()
    }

    /**
     * Returns all [Subject] entities plus schedule-only subject names that are not
     * already represented in the subjects table (matched case-insensitively by name).
     */
    fun mergeSubjectsForPicker(
        subjects: List<Subject>,
        scheduleItems: List<ScheduleItem>,
    ): List<Subject> {
        val merged = subjects.toMutableList()
        val knownNames = subjects.map { it.name.trim().lowercase() }.toMutableSet()

        scheduleItems
            .asSequence()
            .map { it.subjectName.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { name ->
                val key = name.lowercase()
                if (key in knownNames) return@forEach

                val sample = scheduleItems.first { it.subjectName.trim().equals(name, ignoreCase = true) }
                merged.add(
                    Subject(
                        id = stableSubjectIdForScheduleName(name),
                        name = name,
                        code = null,
                        teacher = sample.teacher,
                        colorHex = sample.colorHex.ifBlank { DEFAULT_SUBJECT_COLOR },
                        semester = sample.semester,
                        schoolYear = sample.schoolYear,
                    ),
                )
                knownNames.add(key)
            }

        return merged.sortedBy { it.name.lowercase() }
    }
}
