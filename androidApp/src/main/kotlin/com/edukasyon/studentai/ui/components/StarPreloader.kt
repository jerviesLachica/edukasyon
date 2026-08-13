package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val StarPolygonPoints = listOf(
    0.50f to 0.00f,
    0.61f to 0.35f,
    0.98f to 0.35f,
    0.68f to 0.57f,
    0.79f to 0.91f,
    0.50f to 0.70f,
    0.21f to 0.91f,
    0.32f to 0.57f,
    0.02f to 0.35f,
    0.39f to 0.35f,
)

private val StarSizeFractions = listOf(0.10f, 0.12f, 0.14f, 0.16f, 0.18f)
private val StarDelayMillis = listOf(0, 400, 800, 1_200, 1_600)
private const val RotationDurationMillis = 2_400

fun createStarPath(size: Size): Path {
    val path = Path()
    val (firstX, firstY) = StarPolygonPoints.first()
    path.moveTo(firstX * size.width, firstY * size.height)
    StarPolygonPoints.drop(1).forEach { (x, y) ->
        path.lineTo(x * size.width, y * size.height)
    }
    path.close()
    return path
}

@Composable
fun starPreloaderColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colorScheme.primaryContainer.copy(alpha = 0.95f)
    } else {
        colorScheme.primary
    }
}

@Composable
fun StarPreloader(
    modifier: Modifier = Modifier,
    containerSize: Dp = 120.dp,
    starColor: Color = starPreloaderColor(),
    showGlow: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "starPreloaderPulse")
    val containerPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "containerPulse",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = modifier
            .size(containerSize)
            .graphicsLayer {
                scaleX = containerPulse
                scaleY = containerPulse
            }
            .then(
                if (showGlow) {
                    Modifier.drawWithContent {
                        drawCircle(
                            color = starColor.copy(alpha = glowAlpha),
                            radius = size.minDimension * 0.62f,
                            center = center,
                        )
                        drawContent()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        StarSizeFractions.forEachIndexed { index, sizeFraction ->
            RotatingStarLayer(
                containerSize = containerSize,
                sizeFraction = sizeFraction,
                delayMillis = StarDelayMillis[index],
                starColor = starColor,
                layerIndex = index,
            )
        }
    }
}

@Composable
private fun RotatingStarLayer(
    containerSize: Dp,
    sizeFraction: Float,
    delayMillis: Int,
    starColor: Color,
    layerIndex: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "starRotation$layerIndex")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = RotationDurationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "rotationZ",
    )

    val layerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f + layerIndex * 0.08f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700 + layerIndex * 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "layerAlpha",
    )

    val starPath = remember { createStarPath(Size(1f, 1f)) }

    Canvas(
        modifier = Modifier
            .size(containerSize)
            .graphicsLayer {
                rotationZ = rotation
                alpha = layerAlpha
            },
    ) {
        val starSize = size.width * sizeFraction
        translate(
            left = (size.width - starSize) / 2f,
            top = (size.height - starSize) / 2f,
        ) {
            scale(scaleX = starSize, scaleY = starSize) {
                drawPath(
                    path = starPath,
                    color = starColor,
                    style = Fill,
                )
            }
        }
    }
}
