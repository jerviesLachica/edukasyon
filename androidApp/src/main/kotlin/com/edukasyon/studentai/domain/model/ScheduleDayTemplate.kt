package com.edukasyon.studentai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDayTemplate(
    val dayOfWeek: String,
    val backgroundColorHex: String,
    val accentColorHex: String? = null
)

@Serializable
data class ScheduleWeekTemplates(
    val templates: Map<String, ScheduleDayTemplate> = emptyMap()
) {
    fun templateFor(day: DayOfWeek): ScheduleDayTemplate =
        templates[day.name] ?: ScheduleWeekTemplates.defaultFor(day)

    fun withTemplate(day: DayOfWeek, template: ScheduleDayTemplate): ScheduleWeekTemplates =
        copy(templates = templates + (day.name to template.copy(dayOfWeek = day.name)))

    companion object {
        val DEFAULT_PALETTE = listOf(
            "#FCE4EC", // Mon – light pink
            "#F8BBD0", // Tue – medium pink
            "#F48FB1", // Wed – darker pink
            "#FFCDD2", // Thu – soft rose
            "#FCE4EC", // Fri – light pink
            "#E1BEE7", // Sat – lavender
            "#BBDEFB"  // Sun – soft blue
        )

        val DEFAULT_ACCENT_PALETTE = listOf(
            "#E91E63",
            "#D81B60",
            "#C2185B",
            "#E57373",
            "#EC407A",
            "#9C27B0",
            "#1976D2"
        )

        fun defaultFor(day: DayOfWeek): ScheduleDayTemplate {
            val index = DayOfWeek.entries.indexOf(day).coerceAtLeast(0)
            return ScheduleDayTemplate(
                dayOfWeek = day.name,
                backgroundColorHex = DEFAULT_PALETTE.getOrElse(index) { DEFAULT_PALETTE.first() },
                accentColorHex = DEFAULT_ACCENT_PALETTE.getOrElse(index) { DEFAULT_ACCENT_PALETTE.first() }
            )
        }

        fun defaults(): ScheduleWeekTemplates = ScheduleWeekTemplates(
            templates = DayOfWeek.entries.associate { day ->
                day.name to defaultFor(day)
            }
        )
    }
}
