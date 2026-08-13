package com.edukasyon.studentai.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SPARK_PATH_DATA =
    "M243.7,418.13C198.37,312.3,118.14,268.5,0,294.73,135.19,238.54,203.38,148.99,149.24,0c49.45,103.91,130.68,145.05,243.7,123.4-127.69,63.18-168.91,165.26-149.24,294.73Z"
private const val SPARK_VIEWBOX_WIDTH = 392.94f
private const val SPARK_VIEWBOX_HEIGHT = 418.13f

private const val SPIN_DURATION_MS = 1_100
private const val SPARK_TILT_DURATION_MS = 2_200
private const val DOT_PULSE_DURATION_MS = 1_400

private data class JeviSpinnerColors(
    val pillBackground: Color,
    val pillBorder: Color,
    val label: Color,
    val ringBase: Color,
    val ringTangerine: Color,
    val ringGold: Color,
    val spark: Color,
    val dot: Color,
)

@Composable
private fun jeviSpinnerColors(): JeviSpinnerColors {
    return if (isSystemInDarkTheme()) {
        JeviSpinnerColors(
            pillBackground = Color(0xFF2A2218),
            pillBorder = Color(0xFFFBF6EA).copy(alpha = 0.18f),
            label = Color(0xFFFBF6EA),
            ringBase = Color(0xFFFBF6EA).copy(alpha = 0.14f),
            ringTangerine = Color(0xFFF06A32),
            ringGold = Color(0xFFF5D060),
            spark = Color(0xFFF06A32),
            dot = Color(0xFFF5D060),
        )
    } else {
        JeviSpinnerColors(
            pillBackground = Color(0xFFFBF6EA),
            pillBorder = Color(0xFF3A2418).copy(alpha = 0.15f),
            label = Color(0xFF3A2418),
            ringBase = Color(0xFF3A2418).copy(alpha = 0.12f),
            ringTangerine = Color(0xFFE0521A),
            ringGold = Color(0xFFF2C545),
            spark = Color(0xFFE0521A),
            dot = Color(0xFFE0521A),
        )
    }
}

@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f ||
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1f,
                ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun JeviLoadingSpinner(
    modifier: Modifier = Modifier,
    label: String = "Thinking",
    showLabel: Boolean = true,
    ringSize: Dp = 32.dp,
    compact: Boolean = false,
) {
    val colors = jeviSpinnerColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val displayLabel = remember(label) { label.trimEnd('.', '…', ' ').uppercase() }

    val horizontalPadding = if (compact) 12.dp else 16.dp
    val verticalPadding = if (compact) 6.dp else 8.dp
    val gap = if (compact) 10.dp else 12.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.pillBackground)
            .border(1.dp, colors.pillBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        JeviSpinnerRing(
            ringSize = ringSize,
            colors = colors,
            reducedMotion = reducedMotion,
        )
        if (showLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = displayLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 11.sp else 12.sp,
                    letterSpacing = 0.8.sp,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                JeviSpinnerDots(
                    color = colors.dot,
                    reducedMotion = reducedMotion,
                    dotSize = if (compact) 10.sp else 11.sp,
                )
            }
        }
    }
}

@Composable
private fun JeviSpinnerRing(
    ringSize: Dp,
    colors: JeviSpinnerColors,
    reducedMotion: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jeviSpinnerRing")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinRotation",
    )
    val sparkTilt by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPARK_TILT_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkTilt",
    )

    val sparkPath = remember { createSparkPath() }
    val ringRotation = if (reducedMotion) 0f else spinRotation
    val tiltDegrees = if (reducedMotion) 0f else sparkTilt

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            rotate(ringRotation) {
                drawCircle(
                    color = colors.ringBase,
                    radius = size.minDimension / 2f - strokeWidth / 2f,
                    style = Stroke(width = strokeWidth),
                )
                drawArc(
                    color = colors.ringTangerine,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = colors.ringGold,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            val sparkSize = size.minDimension * 0.42f
            rotate(tiltDegrees, pivot = center) {
                translate(
                    left = center.x - sparkSize / 2f,
                    top = center.y - sparkSize / 2f,
                ) {
                    scale(
                        scaleX = sparkSize / SPARK_VIEWBOX_WIDTH,
                        scaleY = sparkSize / SPARK_VIEWBOX_HEIGHT,
                        pivot = Offset.Zero,
                    ) {
                        drawPath(
                            path = sparkPath,
                            color = colors.spark,
                            style = Fill,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JeviSpinnerDots(
    color: Color,
    reducedMotion: Boolean,
    dotSize: androidx.compose.ui.unit.TextUnit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jeviSpinnerDots")
    Row {
        repeat(3) { index ->
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = if (reducedMotion) 1f else 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = DOT_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset((index * DOT_PULSE_DURATION_MS / 3)),
                ),
                label = "dotAlpha$index",
            )
            Text(
                text = ".",
                fontFamily = FontFamily.Monospace,
                fontSize = dotSize,
                color = color.copy(alpha = dotAlpha),
            )
        }
    }
}

private fun createSparkPath(): Path {
    return PathParser().parsePathString(SPARK_PATH_DATA).toPath()
}
