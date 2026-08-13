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
    val chip = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val hero = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
}

object StudentAiGradients {
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
