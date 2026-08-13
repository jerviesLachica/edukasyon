package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

enum class GeneratingLoaderStyle {
    Full,
    Compact,
}

private const val LETTER_CYCLE_MS = 4_000
private const val LETTER_STAGGER_MS = 105
private const val SWEEP_CYCLE_MS = 2_000
private const val OVERLAY_OPACITY_CYCLE_MS = 4_000

private val SweepEasing = CubicBezierEasing(0.6f, 0.8f, 0.5f, 1f)

private data class LetterAnimState(
    val opacity: Float,
    val scale: Float,
    val translateYPx: Float,
)

private fun computeLetterState(cycleProgress: Float, letterIndex: Int): LetterAnimState {
    val staggerFraction = LETTER_STAGGER_MS.toFloat() / LETTER_CYCLE_MS
    val t = ((cycleProgress - letterIndex * staggerFraction) % 1f + 1f) % 1f

    val opacity = when {
        t < 0.15f -> t / 0.15f
        t < 0.65f -> 1f - (t - 0.15f) / 0.5f
        else -> 0f
    }.coerceIn(0f, 1f)

    return LetterAnimState(
        opacity = opacity,
        scale = 1f + 0.1f * opacity,
        translateYPx = -2f * opacity,
    )
}

private fun computeOverlayOpacity(cycleProgress: Float): Float {
    return when {
        cycleProgress < 0.15f -> cycleProgress / 0.15f
        cycleProgress < 0.65f -> 1f - (cycleProgress - 0.15f) / 0.5f
        else -> 0f
    }.coerceIn(0f, 1f)
}

@Composable
fun GeneratingLoader(
    modifier: Modifier = Modifier,
    label: String = "Generating",
    style: GeneratingLoaderStyle = GeneratingLoaderStyle.Full,
    showOverlay: Boolean = true,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.primary
    }

    val displayLabel = remember(label) { label.trim() }
    val letters = remember(displayLabel) { displayLabel.toList() }

    val height = when (style) {
        GeneratingLoaderStyle.Full -> 120.dp
        GeneratingLoaderStyle.Compact -> 40.dp
    }
    val horizontalPadding = when (style) {
        GeneratingLoaderStyle.Full -> 24.dp
        GeneratingLoaderStyle.Compact -> 8.dp
    }
    val textStyle = when (style) {
        GeneratingLoaderStyle.Full -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        GeneratingLoaderStyle.Compact -> MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "generatingLoader")

    val letterCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LETTER_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "letterCycle",
    )

    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = -0.55f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_CYCLE_MS, easing = SweepEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweepProgress",
    )

    val overlayCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = OVERLAY_OPACITY_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "overlayCycle",
    )

    val overlayOpacity = if (reducedMotion) 0.35f else computeOverlayOpacity(overlayCycle)
    val sweepOffset = if (reducedMotion) 0f else sweepProgress

    Box(
        modifier = modifier
            .height(height)
            .clipToBounds()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (showOverlay && overlayOpacity > 0.01f) {
            GradientSweepOverlay(
                sweepOffset = sweepOffset,
                overlayOpacity = overlayOpacity,
                style = style,
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            letters.forEachIndexed { index, char ->
                val state = if (reducedMotion) {
                    LetterAnimState(opacity = 1f, scale = 1f, translateYPx = 0f)
                } else {
                    computeLetterState(letterCycle, index)
                }
                AnimatedLetter(
                    char = char,
                    textStyle = textStyle,
                    textColor = textColor,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun AnimatedLetter(
    char: Char,
    textStyle: TextStyle,
    textColor: Color,
    state: LetterAnimState,
) {
    val shadowAlpha = state.opacity.coerceIn(0f, 1f)
    val letterStyle = textStyle.copy(
        color = textColor.copy(alpha = 0.25f + 0.75f * state.opacity),
        shadow = if (shadowAlpha > 0.05f) {
            Shadow(
                color = textColor.copy(alpha = 0.45f * shadowAlpha),
                offset = Offset(0f, 2f),
                blurRadius = 10f * shadowAlpha,
            )
        } else {
            null
        },
    )

    Text(
        text = char.toString(),
        style = letterStyle,
        modifier = Modifier.graphicsLayer {
            alpha = state.opacity.coerceAtLeast(0.12f)
            scaleX = state.scale
            scaleY = state.scale
            translationY = state.translateYPx
        },
    )
}

@Composable
private fun GradientSweepOverlay(
    sweepOffset: Float,
    overlayOpacity: Float,
    style: GeneratingLoaderStyle,
) {
    val gradientColors = remember {
        listOf(
            Color(0xFFFFEB3B),
            Color(0xFFFF5252),
            Color(0xFF18FFFF),
            Color(0xFF69F0AE),
            Color(0xFF448AFF),
        )
    }
    val stripeWidth = when (style) {
        GeneratingLoaderStyle.Full -> 6f
        GeneratingLoaderStyle.Compact -> 4f
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2f
        val sweepCenterX = size.width / 2f + sweepOffset * size.width

        gradientColors.forEachIndexed { index, color ->
            val xOffset = (index - 2) * size.width * 0.18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.55f * overlayOpacity),
                        color.copy(alpha = 0.08f * overlayOpacity),
                        Color.Transparent,
                    ),
                    center = Offset(sweepCenterX + xOffset, centerY),
                    radius = size.height * 0.85f,
                ),
                radius = size.height * 0.85f,
                center = Offset(sweepCenterX + xOffset, centerY),
            )
        }

        val stripeCount = floor(size.width / stripeWidth).toInt() + 2
        for (i in 0 until stripeCount) {
            if (i % 2 == 0) continue
            drawRect(
                color = Color.Black.copy(alpha = 0.18f * overlayOpacity),
                topLeft = Offset(i * stripeWidth, 0f),
                size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                blendMode = BlendMode.Softlight,
            )
        }
    }
}
