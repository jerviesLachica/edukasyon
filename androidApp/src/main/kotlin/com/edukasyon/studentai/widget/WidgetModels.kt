package com.edukasyon.studentai.widget

import androidx.compose.ui.graphics.Color
import com.edukasyon.studentai.ui.theme.parseHexColor

enum class WidgetSize {
    SMALL_2X2,
    TALL_2X3
}

enum class WidgetDisplayType {
    TASKS,
    SCHEDULE,
    COMBINED
}

enum class WidgetDesignPreset {
    MINIMAL,
    CORAL_CHEVRON,
    HEX_DARK,
    DOT_GRID,
    LINE_GRID;

    val displayName: String
        get() = when (this) {
            MINIMAL -> "Minimal"
            CORAL_CHEVRON -> "Coral Chevron"
            HEX_DARK -> "Hex Dark"
            DOT_GRID -> "Dot Grid"
            LINE_GRID -> "Line Grid"
        }

    val description: String
        get() = when (this) {
            MINIMAL -> "Clean light card with date and tasks"
            CORAL_CHEVRON -> "Radial gradient with diamond chevrons"
            HEX_DARK -> "Dark hexagonal tile pattern"
            DOT_GRID -> "Dark background with dot grid"
            LINE_GRID -> "Dark background with grid lines"
        }

    fun defaultColors(): WidgetDesignColors = when (this) {
        MINIMAL -> WidgetDesignColors(
            color1 = "#F3F4F6",
            color2 = "#FFFFFF",
            color3 = null
        )
        CORAL_CHEVRON -> WidgetDesignColors(
            color1 = "#F8B195",
            color2 = "#355C7D",
            color3 = null
        )
        HEX_DARK -> WidgetDesignColors(
            color1 = "#1D1D1D",
            color2 = "#4E4F51",
            color3 = "#3C3C3C"
        )
        DOT_GRID -> WidgetDesignColors(
            color1 = "#313131",
            color2 = "#FFFFFF",
            color3 = null
        )
        LINE_GRID -> WidgetDesignColors(
            color1 = "#191A1A",
            color2 = "#808080",
            color3 = null
        )
    }

    val usesLightText: Boolean
        get() = this != MINIMAL
}

data class WidgetDesignColors(
    val color1: String,
    val color2: String,
    val color3: String? = null
) {
    fun resolved(primaryOverride: String?, secondaryOverride: String?, tertiaryOverride: String?): WidgetDesignColors =
        copy(
            color1 = primaryOverride?.takeIf { it.isNotBlank() } ?: color1,
            color2 = secondaryOverride?.takeIf { it.isNotBlank() } ?: color2,
            color3 = tertiaryOverride?.takeIf { it.isNotBlank() } ?: color3
        )

    fun cacheKey(): String = listOfNotNull(color1, color2, color3).joinToString("|")
}

data class WidgetThemeColors(
    val onSurface: Color,
    val muted: Color,
    val card: Color,
    val isLightBackground: Boolean
)

data class WidgetTaskItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: String,
    val isHighlighted: Boolean = false
)

data class WidgetScheduleItem(
    val id: String,
    val title: String,
    val timeRange: String,
    val accentHex: String,
    val isCurrent: Boolean = false
)

data class WidgetCalendarDay(
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isCurrentMonth: Boolean,
    val dotColorHex: String?
)

data class WidgetSnapshot(
    val dayName: String,
    val monthName: String,
    val dayOfMonth: Int,
    val tasks: List<WidgetTaskItem>,
    val schedule: List<WidgetScheduleItem>,
    val calendarDays: List<WidgetCalendarDay>,
    val calendarWeekdayLabels: List<String>,
    val moreCount: Int,
    val accentColorHex: String,
    val isDarkTheme: Boolean,
    val displayType: WidgetDisplayType,
    val widgetSize: WidgetSize = WidgetSize.SMALL_2X2,
    val designPreset: WidgetDesignPreset = WidgetDesignPreset.MINIMAL,
    val designColors: WidgetDesignColors = WidgetDesignPreset.MINIMAL.defaultColors(),
    val themeColors: WidgetThemeColors = widgetThemeFor(WidgetDesignPreset.MINIMAL, WidgetDesignPreset.MINIMAL.defaultColors()),
    val currentTaskProgress: Float? = null,
    val currentTaskTimeLeft: String? = null
)

fun widgetThemeFor(design: WidgetDesignPreset, colors: WidgetDesignColors): WidgetThemeColors {
    return when (design) {
        WidgetDesignPreset.MINIMAL -> WidgetThemeColors(
            onSurface = parseHexColor("#1A1A1A") ?: Color(0xFF1A1A1A),
            muted = parseHexColor("#6B7280") ?: Color(0xFF6B7280),
            card = parseHexColor(colors.color2) ?: Color.White,
            isLightBackground = true
        )
        WidgetDesignPreset.CORAL_CHEVRON -> WidgetThemeColors(
            onSurface = Color(0xFFF8FAFC),
            muted = Color(0xFFE2E8F0),
            card = Color(0x33FFFFFF),
            isLightBackground = false
        )
        WidgetDesignPreset.HEX_DARK,
        WidgetDesignPreset.DOT_GRID,
        WidgetDesignPreset.LINE_GRID -> WidgetThemeColors(
            onSurface = Color(0xFFF5F5F5),
            muted = Color(0xFF9CA3AF),
            card = Color(0x33FFFFFF),
            isLightBackground = false
        )
    }
}

object WidgetAccentPresets {
    val presets = listOf(
        "#00BCD4" to "Cyan",
        "#3949AB" to "Blue",
        "#7C3AED" to "Purple",
        "#E11D48" to "Red",
        "#00897B" to "Teal"
    )
}
