package com.edukasyon.studentai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class ThemePreset(
    val name: String,
    val primaryHex: String,
    val secondaryHex: String? = null
)

data class ThemePreferences(
    val primaryColorHex: String = ThemePresets.DEFAULT_PRIMARY,
    val secondaryColorHex: String? = null
)

object ThemePresets {
    const val DEFAULT_PRIMARY = "#F97316"

    val presets = listOf(
        ThemePreset("Indigo", "#3949AB"),
        ThemePreset("Teal", "#00897B"),
        ThemePreset("Purple", "#7C3AED"),
        ThemePreset("Rose", "#E11D48"),
        ThemePreset("Amber", "#D97706"),
        ThemePreset("Emerald", "#059669"),
        ThemePreset("Sky", "#0284C7"),
        ThemePreset("Slate", "#475569"),
        ThemePreset("Violet", "#8B5CF6"),
        ThemePreset("Coral", "#F97316"),
        ThemePreset("Mint", "#14B8A6"),
        ThemePreset("Fuchsia", "#C026D3")
    )
}

fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length !in listOf(6, 8)) return null
    return try {
        val value = cleaned.toLong(16)
        if (cleaned.length == 6) Color(0xFF000000L or value) else Color(value)
    } catch (_: Exception) {
        null
    }
}

fun Color.toHexString(): String = "#%06X".format(0xFFFFFF and toArgb())

fun isValidHexColor(hex: String): Boolean = parseHexColor(hex) != null

fun buildColorScheme(
    primarySeed: Color,
    secondarySeed: Color? = null,
    darkTheme: Boolean
): ColorScheme {
    val secondary = secondarySeed ?: primarySeed.copy(alpha = 0.85f)
    return if (darkTheme) {
        darkColorScheme(
            primary = primarySeed,
            onPrimary = Color.White,
            primaryContainer = primarySeed.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = primarySeed.copy(alpha = 0.7f)
        )
    } else {
        lightColorScheme(
            primary = primarySeed,
            onPrimary = Color.White,
            primaryContainer = primarySeed.copy(alpha = 0.15f),
            onPrimaryContainer = primarySeed,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = primarySeed.copy(alpha = 0.8f)
        )
    }
}

