package com.edukasyon.studentai.ui.components.mascot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Curated student jokes and motivational quotes spoken by the SchedMate mascot.
 */
val SchedMateQuotesAndJokes = listOf(
    "Why did the student eat their homework? Because the teacher said it was a piece of cake! 🍰",
    "Small steps, big goals! You're making progress every single day. 🐾",
    "Procrastination is just future you having a panic attack. Let's tackle one task now! ⏰",
    "Need a study break? I'll guard your desk while you stretch and hydrate! ☕",
    "My favorite subject? Recess. But your study schedule is a very close second! 📅",
    "You don't have to be great to start, but you have to start to be great! ✨",
    "Late night grind? Remember: a well-rested brain gets better grades! 🌙",
    "An organized schedule is half the battle won. You've got this! 🎯",
    "Why did the math book look sad? Because it had too many problems! 📐",
    "Remember to breathe! One assignment at a time. 🐾",
    "Why was the teacher wearing sunglasses? Because their students were so bright! 😎",
    "Consistency beats cramming every time. 20 focused minutes really count! ⏱️",
    "Flashcard tip: Explaining a topic out loud to yourself locks it into memory! 🧠",
    "Study tip: The Pomodoro technique + SchedMate timer = unstoppable focus! 🍅",
    "Why did the clock get sent to the principal's office? For tocking too much! ⏰",
    "Done is better than perfect. Write that first draft and polish it next! 📝",
    "You've survived 100% of your hardest school days so far. Keep that streak going! 🌟",
    "Coffee fuels the body, but crossing off tasks fuels the soul! ☕",
    "Click me again for another joke or motivation boost! 😄"
)

enum class BubbleTailPosition {
    Bottom, // Points downward (when bubble is placed above mascot)
    Left,   // Points to the left (when bubble is placed to the right of mascot)
}

/**
 * Speech bubble shape with a tail on the left edge pointing left toward the mascot.
 */
private fun speechBubbleLeftTailShape(
    cornerRadius: Float,
    tailWidth: Float,
    tailHeight: Float,
): GenericShape = GenericShape { size, _ ->
    val tw = tailWidth.coerceAtMost(size.width * 0.25f)
    val r = cornerRadius.coerceAtMost(size.height / 2f).coerceAtMost((size.width - tw) / 2f)
    val maxTh = (size.height - 2 * r).coerceAtLeast(0f)
    val th = tailHeight.coerceAtMost(maxTh)
    val tailTop = ((size.height - th) / 2f).coerceAtLeast(r)
    val tailBottom = tailTop + th
    val tailTipY = (tailTop + tailBottom) / 2f

    // Start at top-left corner of the bubble body
    moveTo(tw + r, 0f)

    // Top edge & top-right corner
    lineTo(size.width - r, 0f)
    arcTo(
        rect = Rect(size.width - 2 * r, 0f, size.width, 2 * r),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Right edge & bottom-right corner
    lineTo(size.width, size.height - r)
    arcTo(
        rect = Rect(size.width - 2 * r, size.height - 2 * r, size.width, size.height),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Bottom edge & bottom-left corner
    lineTo(tw + r, size.height)
    arcTo(
        rect = Rect(tw, size.height - 2 * r, tw + 2 * r, size.height),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    // Left edge up to tail bottom
    lineTo(tw, tailBottom)

    // Tail tip (pointing left towards the mascot at x = 0)
    lineTo(0f, tailTipY)

    // Tail top back to bubble body
    lineTo(tw, tailTop)

    // Left edge up to top-left corner
    lineTo(tw, r)
    arcTo(
        rect = Rect(tw, 0f, tw + 2 * r, 2 * r),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )

    close()
}

/**
 * Speech bubble shape with a tail at the bottom pointing down toward the mascot.
 */
private fun speechBubbleBottomTailShape(
    cornerRadius: Float,
    tailWidth: Float,
    tailHeight: Float,
): GenericShape = GenericShape { size, _ ->
    val th = tailHeight.coerceAtMost(size.height * 0.25f)
    val bubbleHeight = size.height - th
    val r = cornerRadius.coerceAtMost(bubbleHeight / 2f).coerceAtMost(size.width / 2f)
    val maxTw = (size.width - 2 * r).coerceAtLeast(0f)
    val tw = tailWidth.coerceAtMost(maxTw)
    val tailLeft = ((size.width - tw) / 2f).coerceAtLeast(r)
    val tailRight = (tailLeft + tw).coerceAtMost(size.width - r)
    val tailTipX = (tailLeft + tailRight) / 2f

    // Top edge & top-right
    moveTo(r, 0f)
    lineTo(size.width - r, 0f)
    arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), -90f, 90f, false)

    // Right edge & bottom-right of bubble body
    lineTo(size.width, bubbleHeight - r)
    arcTo(Rect(size.width - 2 * r, bubbleHeight - 2 * r, size.width, bubbleHeight), 0f, 90f, false)

    // Bottom edge to tail right
    lineTo(tailRight, bubbleHeight)
    // Down to tail tip pointing down
    lineTo(tailTipX, size.height)
    // Up to tail left
    lineTo(tailLeft, bubbleHeight)

    // Bottom-left corner
    lineTo(r, bubbleHeight)
    arcTo(Rect(0f, bubbleHeight - 2 * r, 2 * r, bubbleHeight), 90f, 90f, false)

    // Left edge up to top-left corner
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)

    close()
}

/**
 * Comic-style animated speech bubble for the SchedMate mascot.
 * Features unified vector shape with integrated tail, border, shadow, and physics-based spring scale.
 */
@Composable
fun MascotSpeechBubble(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    tailPosition: BubbleTailPosition = BubbleTailPosition.Left,
    onBubbleClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val tailWidthPx = with(density) { 9.dp.toPx() }
    val tailHeightPx = with(density) { 14.dp.toPx() }
    val cornerRadiusPx = with(density) { 16.dp.toPx() }

    val shape = remember(tailPosition, tailWidthPx, tailHeightPx, cornerRadiusPx) {
        if (tailPosition == BubbleTailPosition.Left) {
            speechBubbleLeftTailShape(cornerRadiusPx, tailWidthPx, tailHeightPx)
        } else {
            speechBubbleBottomTailShape(cornerRadiusPx, tailWidthPx, tailHeightPx)
        }
    }

    val enterAnim = if (tailPosition == BubbleTailPosition.Left) {
        fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    transformOrigin = TransformOrigin(0f, 0.5f)
                )
    } else {
        fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
    }

    val exitAnim = if (tailPosition == BubbleTailPosition.Left) {
        fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                scaleOut(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    transformOrigin = TransformOrigin(0f, 0.5f)
                )
    } else {
        fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                scaleOut(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (tailPosition == BubbleTailPosition.Left) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(min = 120.dp, max = 320.dp)
                    }
                )
                .shadow(elevation = 3.dp, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = shape
                )
                .clickable(onClick = onBubbleClick)
                .padding(
                    start = if (tailPosition == BubbleTailPosition.Left) 18.dp else 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = if (tailPosition == BubbleTailPosition.Bottom) 18.dp else 10.dp,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
        }
    }
}
