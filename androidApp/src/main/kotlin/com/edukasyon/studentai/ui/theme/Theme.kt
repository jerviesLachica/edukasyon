package com.edukasyon.studentai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.ThemeMode

object StudentAiSpacing {
    val xs = 4
    val sm = 8
    val md = 16
    val lg = 24
    val xl = 32
}

object StudentAiShapes {
    val card = RoundedCornerShape(16.dp)
    val dashboard = RoundedCornerShape(22.dp)
    val snackbar = RoundedCornerShape(24.dp)
    val chip = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val hero = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
}

object StudentAiGradients {
    // BRAND COLORS vs FUNCTIONAL COLORS audit (Phase E1 — blue vs orange root cause):
    //
    // ROOT CAUSE: Two different "default primary" values exist:
    //   UserPreferences.DEFAULT_PRIMARY_COLOR = "#3949AB" (indigo blue)
    //   ThemePresets.DEFAULT_PRIMARY          = "#F97316" (orange)
    //   UserPreferences.primaryColorHex returns DEFAULT_PRIMARY_COLOR when no pref is set,
    //   which overrides ThemePresets.DEFAULT_PRIMARY passed to StudentAiTheme().
    //   => Any user who never chose a custom color sees indigo (#3949AB) instead of orange.
    //
    // Hardcoded blues/purples (brand — risk of overriding the orange default):
    //   #3949AB  indigo     — UserPreferences.DEFAULT_PRIMARY_COLOR (DEFAULT), ColorPicker placeholder, GradesScreen fallback
    //   #1A237E  deep indigo — ScheduleScreen fallback, CalendarEvent default, WidgetDataProvider
    //   #4F46E5  indigo-600  — ExamEditDialog mock data, JeviDeck mock data
    //   #185EE0  bright blue — PillTabBar selected tab (light mode), ThemedSnackbar Info/Success icon
    //   #1976D2  blue-600    — HomeScreen priority accent fallback
    //   #1565C0  blue-700    — HomeScreen schedule card
    //   #448AFF  light blue  — GeneratingLoader spinner
    //   #424242  graphite    — ScanningOverlay (functional, not brand)
    //   #3A2418  dark brown  — JeviLoadingSpinner dark mode (functional)
    //   #2A3140  dark slate  — FocusScreen dark bg (functional)
    //   #1A2940  navy        — HomeScreen dark card bg (functional)
    //   #6366F1  indigo-500  — JeviViewModels deck colors
    //   #8B5CF6  violet-500  — JeviViewModels deck colors
    //   #3B82F6  blue-500    — JeviViewModels deck colors
    //   #0284C7  sky-500     — ThemePresets "Sky" option (brand, selectable)
    //   #00897B  teal-500    — ThemePresets "Teal" + WidgetDataProvider task events
    //   #7C3AED  violet-600  — ThemePresets "Purple" (brand)
    //   #8B5CF6  violet-500  — ThemePresets "Violet" (brand)
    //   #C026D3  fuchsia     — ThemePresets "Fuchsia" (brand)
    //
    // Functional / non-brand colors (safe):
    //   #F97316  orange      — ThemePresets.DEFAULT_PRIMARY (the intended brand default), coral preset
    //   #FFFFFF  white       — backgrounds, cards (functional)
    //   #000000  black       — text, dark mode surfaces (functional)
    //   #FF5252  red-400     — GeneratingLoader error pulse (functional)
    //   #43A047  green-700   — ExamReadinessCard passed (functional)
    //   #FB8C00  orange-700  — ExamReadinessCard borderline (functional)
    //   #10B981  emerald-500 — AnimatedTaskCheckbox done (functional)
    //   #059669  emerald-600 — ExamReadinessCard passed (functional)
    //   #E11D48  rose-600    — ThemePresets "Rose" (brand, selectable)
    //   #D97706  amber-600   — ThemePresets "Amber" (brand)
    //   #059669  emerald-600 — ThemePresets "Emerald" (brand)
    //   #14B8A6  teal-400    — ThemePresets "Mint" (brand)
    //   #475569  slate-500   — ThemePresets "Slate" (brand)
    //
    // NOTE: PillTabBar light-mode glider/selectedContent (#E6EEF9/#185EE0) and
    // ThemedSnackbar Info/Success (#E6EEF9/#185EE0) hardcode indigo tones that will
    // visually clash if the user picks a non-indigo theme color.
    //
    // FIX (not applied): Align UserPreferences.DEFAULT_PRIMARY_COLOR with
    // ThemePresets.DEFAULT_PRIMARY ("#F97316"), or remove the override in primaryColorHex
    // flow so it falls back to ThemePresets.DEFAULT_PRIMARY in StudentAiTheme().

    @Composable
    fun headerBrush(): Brush {
        val colors = MaterialTheme.colorScheme
        return Brush.linearGradient(
            colors = listOf(
                colors.primary,
                colors.secondary,
                colors.tertiary.copy(alpha = 0.92f),
            )
        )
    }

    @Composable
    fun subtleSurfaceBrush(): Brush {
        val colors = MaterialTheme.colorScheme
        return Brush.verticalGradient(
            colors = listOf(
                colors.surfaceContainerLow,
                colors.background
            )
        )
    }

    @Composable
    fun meshBackgroundBrush(): Brush {
        val colors = MaterialTheme.colorScheme
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to colors.primary.copy(alpha = 0.06f),
                0.35f to colors.background,
                0.7f to colors.secondary.copy(alpha = 0.04f),
                1f to colors.background,
            )
        )
    }

    @Composable
    fun accentChipBrush(index: Int = 0): Brush {
        val colors = MaterialTheme.colorScheme
        val pairs = listOf(
            listOf(colors.primary, colors.secondary),
            listOf(colors.tertiary, colors.primary),
            listOf(colors.secondary, colors.tertiary),
        )
        val pair = pairs[index % pairs.size]
        return Brush.linearGradient(colors = pair)
    }
}

@Composable
fun StudentAiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    primaryColorHex: String = ThemePresets.DEFAULT_PRIMARY,
    secondaryColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primarySeed = remember(primaryColorHex) {
        parseHexColor(primaryColorHex) ?: parseHexColor(ThemePresets.DEFAULT_PRIMARY)!!
    }
    val secondarySeed = remember(secondaryColorHex) {
        secondaryColorHex?.let { parseHexColor(it) }
    }
    val colorScheme = remember(primarySeed, secondarySeed, darkTheme) {
        buildColorScheme(primarySeed, secondarySeed, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudentAiTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
