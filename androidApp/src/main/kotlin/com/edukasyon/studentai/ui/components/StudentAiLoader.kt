package com.edukasyon.studentai.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class StudentAiLoaderStyle {
    Full,
    Compact,
}

@Composable
fun StudentAiLoader(
    modifier: Modifier = Modifier,
    label: String? = null,
    style: StudentAiLoaderStyle = StudentAiLoaderStyle.Full,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onBackground

    when (style) {
        StudentAiLoaderStyle.Full -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BookPageFlipMotif(
                    color = primaryColor,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.size(120.dp),
                )
                if (!label.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = labelColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        StudentAiLoaderStyle.Compact -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                DotOrbitMotif(
                    color = primaryColor,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.size(40.dp),
                )
                if (!label.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = labelColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPageFlipMotif(
    color: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BookPageFlipTransition")
    val rawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pageFlipProgress",
    )
    val flipProgress = if (reducedMotion) 0.5f else rawProgress

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val bookBottom = h * 0.75f
        val bookTop = h * 0.25f
        val bookHeight = bookBottom - bookTop
        val halfWidth = w * 0.35f
        val spineX = centerX

        // Draw left cover/pages base
        val leftPath = Path().apply {
            moveTo(spineX, bookTop)
            lineTo(spineX - halfWidth, bookTop + 8.dp.toPx())
            lineTo(spineX - halfWidth, bookBottom + 8.dp.toPx())
            lineTo(spineX, bookBottom)
            close()
        }
        drawPath(leftPath, color = color.copy(alpha = 0.35f))
        drawPath(leftPath, color = color, style = Stroke(width = 2.dp.toPx()))

        // Draw right cover/pages base
        val rightPath = Path().apply {
            moveTo(spineX, bookTop)
            lineTo(spineX + halfWidth, bookTop + 8.dp.toPx())
            lineTo(spineX + halfWidth, bookBottom + 8.dp.toPx())
            lineTo(spineX, bookBottom)
            close()
        }
        drawPath(rightPath, color = color.copy(alpha = 0.35f))
        drawPath(rightPath, color = color, style = Stroke(width = 2.dp.toPx()))

        // Draw animated flipping page
        // Angle ranges from 0 (right side) to Math.PI (left side)
        val angle = (1f - flipProgress) * Math.PI.toFloat()
        val pageScaleX = cos(angle.toDouble()).toFloat()
        val pageX = spineX + pageScaleX * halfWidth
        val arcLift = (sin(angle.toDouble()) * 16.dp.toPx()).toFloat()

        val flippingPagePath = Path().apply {
            moveTo(spineX, bookTop)
            lineTo(pageX, bookTop + 4.dp.toPx() - arcLift)
            lineTo(pageX, bookBottom + 4.dp.toPx() - arcLift)
            lineTo(spineX, bookBottom)
            close()
        }
        drawPath(flippingPagePath, color = color.copy(alpha = 0.75f))
        drawPath(flippingPagePath, color = color, style = Stroke(width = 2.5.dp.toPx()))

        // Draw spine center line
        drawLine(
            color = color,
            start = Offset(spineX, bookTop - 2.dp.toPx()),
            end = Offset(spineX, bookBottom + 2.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
        )
    }
}

@Composable
private fun DotOrbitMotif(
    color: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotOrbitTransition")
    val rawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitProgress",
    )
    val progress = if (reducedMotion) 0.25f else rawProgress

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val orbitRadius = size.minDimension * 0.35f
        val dotRadius = size.minDimension * 0.1f

        // Draw orbit track
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = orbitRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Draw center dot
        drawCircle(
            color = color,
            radius = dotRadius * 0.8f,
            center = Offset(centerX, centerY),
        )

        // Draw 3 orbiting dots at different angles
        val baseAngle = progress * 2f * Math.PI.toFloat()
        for (i in 0 until 3) {
            val dotAngle = baseAngle + (i * 2f * Math.PI.toFloat() / 3f)
            val dx = (centerX + orbitRadius * cos(dotAngle.toDouble())).toFloat()
            val dy = (centerY + orbitRadius * sin(dotAngle.toDouble())).toFloat()
            val alpha = 0.4f + (i * 0.3f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius * (0.8f + i * 0.2f),
                center = Offset(dx, dy),
            )
        }
    }
}
