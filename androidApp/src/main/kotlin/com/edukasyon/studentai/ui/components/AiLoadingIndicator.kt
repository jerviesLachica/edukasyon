package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AiLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    message: String? = "AI is thinking…",
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StarPreloader(
            containerSize = size,
            showGlow = true,
            starColor = MaterialTheme.colorScheme.primary,
        )
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                ThinkingDots()
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingDots")
    Row {
        repeat(3) { index ->
            val dotScale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 150),
                ),
                label = "dotScale$index",
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    },
            )
        }
    }
}
