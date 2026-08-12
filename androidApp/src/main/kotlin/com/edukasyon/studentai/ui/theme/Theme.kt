package com.edukasyon.studentai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF001454),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF7E57C2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DDFF),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainer = Color(0xFFEEEFF4),
    surfaceContainerLow = Color(0xFFF4F5FA),
    surfaceContainerHigh = Color(0xFFE8E9EE),
    surfaceContainerHighest = Color(0xFFE2E3E8),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF002984),
    primaryContainer = Color(0xFF1A3A9E),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF3A0080),
    tertiaryContainer = Color(0xFF5E35A8),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

object StudentAiSpacing {
    val xs = 4
    val sm = 8
    val md = 16
    val lg = 24
    val xl = 32
}

object StudentAiShapes {
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val hero = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
}

object StudentAiGradients {
    @Composable
    fun headerBrush(): Brush {
        val colors = MaterialTheme.colorScheme
        return Brush.linearGradient(
            colors = listOf(
                colors.primary,
                colors.tertiary.copy(alpha = 0.85f)
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
}

@Composable
fun StudentAiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
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
