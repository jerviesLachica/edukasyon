package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/** Pressed scale — pronounced enough to read at 0.5× system animator scale (Huawei). */
private const val PRESSED_SCALE = 0.90f
private const val DEFAULT_SCALE = 1f

private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessHigh,
)

private val ReleaseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Scale-down on press with spring release. Chain before [clickable] or use [animatedClickable].
 */
fun Modifier.animatedPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else DEFAULT_SCALE,
        animationSpec = if (isPressed) PressSpring else ReleaseSpring,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Clickable with Material ripple, spring press-scale, and optional haptic on tap.
 */
fun Modifier.animatedClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    haptic: Boolean = true,
    pressedScale: Float = PRESSED_SCALE,
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    animatedPressScale(interactionSource, pressedScale)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            role = role,
            onClick = {
                if (haptic) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            },
        )
}

@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    IconButton(
        onClick = {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        modifier = modifier.animatedPressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        onClick = {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        modifier = modifier.animatedPressScale(interactionSource),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        modifier = modifier.animatedPressScale(interactionSource),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun BouncyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    TextButton(
        onClick = {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        modifier = modifier.animatedPressScale(interactionSource, pressedScale = 0.94f),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}
