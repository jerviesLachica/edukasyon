package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.random.Random

private data class Star(
    val xFraction: Float,
    val yFraction: Float,
    val alpha: Float,
)

private data class PreparedStar(
    val xFraction: Float,
    val yFraction: Float,
    val color: Color,
)

private const val STAR_CYCLE_HEIGHT_DP = 2000

/** Overall layer opacity — keeps the ambient effect subtle behind cards and buttons. */
private const val STARFIELD_OVERLAY_ALPHA_DARK = 0.55f
private const val STARFIELD_OVERLAY_ALPHA_LIGHT = 0.42f

private fun generateStars(seed: Int, count: Int): List<Star> {
    val random = Random(seed)
    return List(count) {
        Star(
            xFraction = random.nextFloat(),
            yFraction = random.nextFloat(),
            alpha = 0.15f + random.nextFloat() * 0.35f,
        )
    }
}

/**
 * Animated parallax starfield inspired by the Uiverse.io night-sky design.
 * Highly optimized with precomputed star colors and lightweight star density.
 */
@Composable
fun StarfieldBackground(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Optimized star counts (100 small, 35 medium, 15 large) for smooth 120fps performance with low battery draw
    val rawSmall = remember { generateStars(0x5A71, 100) }
    val rawMedium = remember { generateStars(0x5A72, 35) }
    val rawLarge = remember { generateStars(0x5A73, 15) }

    val density = LocalDensity.current
    val cycleHeightPx = with(density) { STAR_CYCLE_HEIGHT_DP.dp.toPx() }
    val smallRadiusPx = with(density) { 1.dp.toPx() }
    val mediumRadiusPx = with(density) { 2.dp.toPx() }
    val largeRadiusPx = with(density) { 3.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val offsetSmall by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -cycleHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 50_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starsSmall",
    )
    val offsetMedium by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -cycleHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starsMedium",
    )
    val offsetLarge by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -cycleHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starsLarge",
    )

    val overlayAlpha = if (isDark) STARFIELD_OVERLAY_ALPHA_DARK else STARFIELD_OVERLAY_ALPHA_LIGHT
    val starColor = if (isDark) Color(0xFFDCE6F2) else Color(0xFF4A6F96)
    val layerAlphaScale = if (isDark) {
        floatArrayOf(0.38f, 0.30f, 0.24f)
    } else {
        floatArrayOf(0.16f, 0.20f, 0.24f)
    }

    // Precompute star colors to avoid per-frame Color allocations
    val layerSmall = remember(rawSmall, starColor, layerAlphaScale[0]) {
        rawSmall.map { PreparedStar(it.xFraction, it.yFraction, starColor.copy(alpha = (it.alpha * layerAlphaScale[0]).coerceIn(0f, 1f))) }
    }
    val layerMedium = remember(rawMedium, starColor, layerAlphaScale[1]) {
        rawMedium.map { PreparedStar(it.xFraction, it.yFraction, starColor.copy(alpha = (it.alpha * layerAlphaScale[1]).coerceIn(0f, 1f))) }
    }
    val layerLarge = remember(rawLarge, starColor, layerAlphaScale[2]) {
        rawLarge.map { PreparedStar(it.xFraction, it.yFraction, starColor.copy(alpha = (it.alpha * layerAlphaScale[2]).coerceIn(0f, 1f))) }
    }

    val darkGradient = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF1B2735).copy(alpha = 0.50f),
                Color(0xFF090A0F).copy(alpha = 0.35f),
            ),
        )
    }
    val lightGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FBFF).copy(alpha = 0.32f),
                Color(0xFFE6F1FA).copy(alpha = 0.24f),
                Color(0xFFC9DBEF).copy(alpha = 0.18f),
            ),
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha },
    ) {
        drawRect(brush = if (isDark) darkGradient else lightGradient)

        drawStarLayer(
            stars = layerSmall,
            offsetY = offsetSmall,
            cycleHeight = cycleHeightPx,
            canvasWidth = size.width,
            canvasHeight = size.height,
            radius = smallRadiusPx,
        )
        drawStarLayer(
            stars = layerMedium,
            offsetY = offsetMedium,
            cycleHeight = cycleHeightPx,
            canvasWidth = size.width,
            canvasHeight = size.height,
            radius = mediumRadiusPx,
        )
        drawStarLayer(
            stars = layerLarge,
            offsetY = offsetLarge,
            cycleHeight = cycleHeightPx,
            canvasWidth = size.width,
            canvasHeight = size.height,
            radius = largeRadiusPx,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarLayer(
    stars: List<PreparedStar>,
    offsetY: Float,
    cycleHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    radius: Float,
) {
    stars.forEach { star ->
        val x = star.xFraction * canvasWidth
        var y = star.yFraction * cycleHeight + offsetY
        y %= cycleHeight
        if (y < 0f) y += cycleHeight

        var drawY = y
        while (drawY > -radius) drawY -= cycleHeight
        drawY += cycleHeight

        while (drawY < canvasHeight + radius) {
            drawCircle(
                color = star.color,
                radius = radius,
                center = Offset(x, drawY),
            )
            drawY += cycleHeight
        }
    }
}

@Composable
fun StarfieldScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        StarfieldBackground(Modifier.fillMaxSize())
        content()
    }
}
