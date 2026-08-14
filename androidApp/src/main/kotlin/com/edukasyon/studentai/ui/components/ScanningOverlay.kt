package com.edukasyon.studentai.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.viewmodel.ScheduleScanStatus
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private val ScanProgressSteps = listOf(
    "Detecting text…",
    "Extracting classes…",
    "Reading your schedule…",
)

@Composable
fun ScanningOverlay(
    modifier: Modifier = Modifier,
    imageBytes: ByteArray? = null,
    extractedText: String? = null,
    primaryMessage: String = "Scanning schedule…",
    subMessage: String = "Jevi is reading your schedule",
    isImageOnlyRetry: Boolean = false,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "scanOverlay")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanLineProgress",
    )
    val bracketPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bracketPulse",
    )

    var stepIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            stepIndex = (stepIndex + 1) % ScanProgressSteps.size
        }
    }

    val previewBitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "Captured schedule",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 20.dp.toPx()
            val cornerLength = 36.dp.toPx()
            val strokeWidth = 3.dp.toPx()
            val bracketColor = primaryColor.copy(alpha = bracketPulse)

            fun drawCorner(
                startX: Float,
                startY: Float,
                endX1: Float,
                endY1: Float,
                endX2: Float,
                endY2: Float,
            ) {
                drawLine(
                    color = bracketColor,
                    start = Offset(startX, startY),
                    end = Offset(endX1, endY1),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = bracketColor,
                    start = Offset(startX, startY),
                    end = Offset(endX2, endY2),
                    strokeWidth = strokeWidth,
                )
            }

            drawCorner(
                inset, inset,
                inset + cornerLength, inset,
                inset, inset + cornerLength,
            )
            drawCorner(
                size.width - inset, inset,
                size.width - inset - cornerLength, inset,
                size.width - inset, inset + cornerLength,
            )
            drawCorner(
                inset, size.height - inset,
                inset + cornerLength, size.height - inset,
                inset, size.height - inset - cornerLength,
            )
            drawCorner(
                size.width - inset, size.height - inset,
                size.width - inset - cornerLength, size.height - inset,
                size.width - inset, size.height - inset - cornerLength,
            )

            val scanY = scanProgress * size.height
            val glowHeight = 48.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        primaryColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    startY = scanY,
                    endY = scanY + glowHeight,
                ),
                topLeft = Offset(0f, scanY),
                size = androidx.compose.ui.geometry.Size(size.width, glowHeight),
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.95f),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 2.5.dp.toPx(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StarPreloader(containerSize = 56.dp, showGlow = true)
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isImageOnlyRetry) "Retrying from scratch…" else primaryMessage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isImageOnlyRetry) "Reading the image directly, no text preview" else subMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = ScanProgressSteps[stepIndex],
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor.copy(alpha = 0.95f),
                textAlign = TextAlign.Center,
            )
            extractedText?.takeIf { it.isNotBlank() }?.let { text ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Detected text preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text.take(280).let { preview ->
                        if (text.length > 280) "$preview…" else preview
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ScheduleScanFailureOverlay(
    modifier: Modifier = Modifier,
    imageBytes: ByteArray? = null,
    status: ScheduleScanStatus,
    retryCount: Int,
    retryAfterMillis: Long? = null,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onEnterManually: (() -> Unit)? = null,
) {
    val previewBitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    var remainingMs by remember(retryAfterMillis) {
        mutableLongStateOf(
            retryAfterMillis?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) } ?: 0L,
        )
    }
    val retryAvailable = status != ScheduleScanStatus.RETRY_LATER || remainingMs <= 0L

    LaunchedEffect(status, retryAfterMillis) {
        if (status != ScheduleScanStatus.RETRY_LATER || retryAfterMillis == null) return@LaunchedEffect
        while (true) {
            remainingMs = (retryAfterMillis - System.currentTimeMillis()).coerceAtLeast(0)
            if (remainingMs <= 0L) break
            delay(1_000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "Captured schedule",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (status) {
                    ScheduleScanStatus.UNREADABLE -> "Schedule unreadable"
                    ScheduleScanStatus.RETRY_LATER -> "Try again later"
                    else -> "Scan failed"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (status) {
                    ScheduleScanStatus.UNREADABLE ->
                        "Couldn't read this schedule. Tap retry to try a different approach."
                    ScheduleScanStatus.RETRY_LATER ->
                        if (retryAvailable) {
                            "Scanning didn't work after several tries. You can try again or enter manually."
                        } else {
                            "Scanning didn't work after several tries. Try again in ${formatRetryWait(remainingMs)} or enter manually."
                        }
                    else -> "Something went wrong while scanning."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )
            if (status == ScheduleScanStatus.UNREADABLE && retryCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Attempt $retryCount of 5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (retryAvailable) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.72f),
                ) {
                    Text(if (status == ScheduleScanStatus.RETRY_LATER) "Try again" else "Retry")
                }
            }
            if (onEnterManually != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onEnterManually,
                    modifier = Modifier.fillMaxWidth(0.72f),
                ) {
                    Text("Enter classes manually")
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(0.72f),
            ) {
                Text("Back to camera")
            }
        }
    }
}

private fun formatRetryWait(remainingMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs).coerceAtLeast(1)
    return "$minutes minute${if (minutes == 1L) "" else "s"}"
}
