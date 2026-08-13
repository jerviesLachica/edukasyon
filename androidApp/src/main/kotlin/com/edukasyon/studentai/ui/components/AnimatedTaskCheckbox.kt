package com.edukasyon.studentai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private object AnimatedTaskCheckboxDefaults {
    val boxSize = 22.dp
    val cornerRadius = 6.dp
    val borderWidth = 2.dp
    val greenStart = Color(0xFF10B981)
    val greenEnd = Color(0xFF059669)
    val completedTextLight = Color(0xFF6B7280)

    @Composable
    fun borderDefault(): Color {
        return if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.outline
        } else {
            Color(0xFFD1D5DB)
        }
    }

    @Composable
    fun boxBackground(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun completedTextColor(): Color {
        return if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            completedTextLight
        }
    }
}

/**
 * Checkbox + label row for planner tasks, subtasks, and assignments.
 */
@Composable
fun AnimatedTaskCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedTaskCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(Modifier.width(12.dp))
        AnimatedTaskCheckboxLabel(
            text = label,
            checked = checked,
            enabled = enabled,
            onLabelClick = { onCheckedChange(!checked) },
            style = textStyle,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun AnimatedTaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = enabled && (isPressed || isHovered)

    val borderDefault = AnimatedTaskCheckboxDefaults.borderDefault()
    val borderColor by animateColorAsState(
        targetValue = when {
            checked || isActive -> AnimatedTaskCheckboxDefaults.greenStart
            else -> borderDefault
        },
        animationSpec = tween(200),
        label = "checkboxBorder"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pressScale"
    )

    val fillScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fillScale"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "checkPop"
    )

    val checkRotation by animateFloatAsState(
        targetValue = if (checked) 0f else -45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkRotation"
    )

    val rippleProgress = remember { Animatable(0f) }
    var previousChecked by remember { mutableStateOf(checked) }

    LaunchedEffect(checked) {
        if (checked && !previousChecked) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, tween(durationMillis = 600))
        }
        previousChecked = checked
    }

    val shape = RoundedCornerShape(AnimatedTaskCheckboxDefaults.cornerRadius)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive && !checked) 0.35f else if (checked) 0.25f else 0f,
        animationSpec = tween(200),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .size(AnimatedTaskCheckboxDefaults.boxSize)
            .scale(pressScale)
            .semantics { role = Role.Checkbox }
            .then(
                if (checked) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        spotColor = AnimatedTaskCheckboxDefaults.greenStart,
                        ambientColor = AnimatedTaskCheckboxDefaults.greenStart.copy(alpha = 0.35f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawCircle(
                        color = AnimatedTaskCheckboxDefaults.greenStart.copy(alpha = glowAlpha),
                        radius = size.minDimension * 0.85f,
                        center = center
                    )
                }
                if (rippleProgress.value > 0f && rippleProgress.value < 1f) {
                    val rippleRadius = size.minDimension * (0.5f + rippleProgress.value)
                    drawCircle(
                        color = AnimatedTaskCheckboxDefaults.greenStart.copy(
                            alpha = (1f - rippleProgress.value) * 0.4f
                        ),
                        radius = rippleRadius,
                        center = center
                    )
                }
            }
            .background(AnimatedTaskCheckboxDefaults.boxBackground())
            .border(
                width = AnimatedTaskCheckboxDefaults.borderWidth,
                color = borderColor,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(fillScale)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AnimatedTaskCheckboxDefaults.greenStart,
                            AnimatedTaskCheckboxDefaults.greenEnd
                        )
                    ),
                    shape = shape
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                    rotationZ = checkRotation
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.74f)
                    lineTo(size.width * 0.82f, size.height * 0.28f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun AnimatedTaskCheckboxLabel(
    text: String,
    checked: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLabelClick: (() -> Unit)? = null,
) {
    val textColor by animateColorAsState(
        targetValue = if (checked) {
            AnimatedTaskCheckboxDefaults.completedTextColor()
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(250),
        label = "labelColor"
    )

    val strikeProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "strikethrough"
    )
    val strikeColor = AnimatedTaskCheckboxDefaults.completedTextColor()

    Text(
        text = text,
        style = style,
        color = textColor,
        modifier = modifier
            .then(
                if (onLabelClick != null) {
                    Modifier.clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onLabelClick()
                    }
                } else {
                    Modifier
                }
            )
            .drawBehind {
            if (strikeProgress > 0f) {
                val strokeWidth = 1.5.dp.toPx()
                val y = size.height / 2f
                drawLine(
                    color = strikeColor,
                    start = Offset(0f, y),
                    end = Offset(size.width * strikeProgress, y),
                    strokeWidth = strokeWidth
                )
            }
        }
    )
}
