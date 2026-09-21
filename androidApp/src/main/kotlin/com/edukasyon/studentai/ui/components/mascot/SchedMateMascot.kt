package com.edukasyon.studentai.ui.components.mascot

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.edukasyon.studentai.R
import kotlinx.coroutines.delay

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width

enum class MascotBubblePosition {
    Top,
    Right,
}

/**
 * Unified SchedMate Red Panda Mascot Composable.
 *
 * Supports:
 * - 8 distinct moods / states (Idle, Planning, Coffee, Submitting, Motivated, Resting, Learning, LateNight)
 * - Transparent background rendering inside customizable container boxes/divs
 * - Interactive tap feedback (bouncy scale & speech bubble trigger)
 * - Animated comic speech bubbles with jokes and motivational quotes
 * - Seamless playback of transparent animated WebP / video assets with fallback to vector illustration
 * - Configurable bubble position (Top or Right with directional tail)
 */
@Composable
fun SchedMateMascot(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Idle,
    size: Dp = 160.dp,
    containerModifier: Modifier = Modifier,
    interactive: Boolean = true,
    showSpeechBubble: Boolean = true,
    startBubbleVisible: Boolean = true,
    bubblePosition: MascotBubblePosition = MascotBubblePosition.Right,
    customSpeechText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var quoteIndex by remember { mutableIntStateOf(0) }
    var isBubbleVisible by remember { mutableStateOf(startBubbleVisible) }
    var isPressed by remember { mutableStateOf(false) }

    // Auto-dismiss bubble after 8 seconds of inactivity
    LaunchedEffect(isBubbleVisible, quoteIndex) {
        if (isBubbleVisible) {
            delay(8000)
            isBubbleVisible = false
        }
    }

    // Bouncy scale animation when tapped
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "MascotBounce"
    )

    // Animated WebP loader with platform hardware ImageDecoder
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // Check for drawable or raw animated assets dynamically
    val animResId = remember(mood) {
        val drawableId = context.resources.getIdentifier(mood.rawResName, "drawable", context.packageName)
        if (drawableId != 0) {
            drawableId
        } else {
            val rawId = context.resources.getIdentifier(mood.rawResName, "raw", context.packageName)
            if (rawId != 0) rawId else null
        }
    }

    // Remember ImageRequest and disable crossfade (crossfade interferes with animated drawables)
    val imageRequest = remember(context, animResId) {
        ImageRequest.Builder(context)
            .data(animResId)
            .crossfade(false)
            .build()
    }

    val mascotBox: @Composable () -> Unit = {
        Box(
            modifier = containerModifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (interactive) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                isPressed = true
                                quoteIndex = (quoteIndex + 1) % SchedMateQuotesAndJokes.size
                                isBubbleVisible = true
                                onClick?.invoke()
                            }
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            LaunchedEffect(isPressed) {
                if (isPressed) {
                    delay(120)
                    isPressed = false
                }
            }

            when {
                // If animated WebP / image is present
                animResId != null -> {
                    AsyncImage(
                        model = imageRequest,
                        imageLoader = imageLoader,
                        contentDescription = mood.description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(size),
                    )
                }

                // Default Fallback: Displays canonical SchedMate Red Panda logo
                else -> {
                    Image(
                        painter = painterResource(R.drawable.wala),
                        contentDescription = mood.description,
                        modifier = Modifier.size(size),
                    )
                }
            }
        }
    }

    if (bubblePosition == MascotBubblePosition.Right) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            mascotBox()

            if (showSpeechBubble) {
                Spacer(Modifier.width(10.dp))
                val speechText = customSpeechText ?: SchedMateQuotesAndJokes[quoteIndex % SchedMateQuotesAndJokes.size]
                MascotSpeechBubble(
                    text = speechText,
                    visible = isBubbleVisible,
                    tailPosition = BubbleTailPosition.Left,
                    onBubbleClick = {
                        quoteIndex = (quoteIndex + 1) % SchedMateQuotesAndJokes.size
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            if (showSpeechBubble) {
                val speechText = customSpeechText ?: SchedMateQuotesAndJokes[quoteIndex % SchedMateQuotesAndJokes.size]
                MascotSpeechBubble(
                    text = speechText,
                    visible = isBubbleVisible,
                    tailPosition = BubbleTailPosition.Bottom,
                    onBubbleClick = {
                        quoteIndex = (quoteIndex + 1) % SchedMateQuotesAndJokes.size
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            mascotBox()
        }
    }
}

/**
 * Lightweight animated mascot icon specifically designed for navigation bars, tabs, and avatar badges.
 * Seamlessly loops the transparent animated WebP for the given mood.
 */
@Composable
fun AnimatedMascotIcon(
    mood: MascotMood = MascotMood.Idle,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    val animResId = remember(mood) {
        val drawableId = context.resources.getIdentifier(mood.rawResName, "drawable", context.packageName)
        if (drawableId != 0) drawableId else {
            val rawId = context.resources.getIdentifier(mood.rawResName, "raw", context.packageName)
            if (rawId != 0) rawId else null
        }
    }
    val imageRequest = remember(context, animResId) {
        ImageRequest.Builder(context)
            .data(animResId ?: R.drawable.wala)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = mood.description,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

