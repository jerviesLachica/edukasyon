package com.edukasyon.studentai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PillShape = RoundedCornerShape(99.dp)
private val GliderAnimationSpec = tween<Dp>(durationMillis = 320, easing = FastOutSlowInEasing)
private val ColorAnimationSpec = tween<Color>(durationMillis = 200)

data class PillTabSpec(
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null,
    val badgeCount: Int? = null,
)

enum class PillTabOrientation {
    Horizontal,
    Vertical,
}

@Immutable
private data class PillTabColors(
    val container: Color,
    val glider: Color,
    val selectedContent: Color,
    val unselectedContent: Color,
    val badgeDefault: Color,
    val badgeSelected: Color,
    val badgeText: Color,
)

@Composable
private fun rememberPillTabColors(): PillTabColors {
    val isDark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    return remember(isDark, scheme) {
        if (isDark) {
            PillTabColors(
                container = scheme.surfaceContainerHigh,
                glider = scheme.primaryContainer.copy(alpha = 0.55f),
                selectedContent = scheme.primary,
                unselectedContent = scheme.onSurfaceVariant,
                badgeDefault = scheme.primaryContainer,
                badgeSelected = scheme.primary,
                badgeText = scheme.onPrimary,
            )
        } else {
            PillTabColors(
                container = Color.White,
                glider = Color(0xFFE6EEF9),
                selectedContent = Color(0xFF185EE0),
                unselectedContent = Color(0xFF1A1A1A),
                badgeDefault = Color(0xFFE6EEF9),
                badgeSelected = Color(0xFF185EE0),
                badgeText = Color.White,
            )
        }
    }
}

/**
 * Pill-style tab bar with an animated glider behind the selected tab.
 *
 * @param tabs Tab definitions (label, optional icons, optional badge count).
 * @param selectedIndex Zero-based index of the active tab.
 * @param onTabSelected Callback when a tab is tapped.
 * @param orientation Horizontal for bottom bar; vertical for navigation rail.
 * @param showLabels When false, only icons are shown (compact rail).
 */
@Composable
fun PillTabBar(
    tabs: List<PillTabSpec>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    orientation: PillTabOrientation = PillTabOrientation.Horizontal,
    showLabels: Boolean = true,
) {
    if (tabs.isEmpty()) return

    val colors = rememberPillTabColors()
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    when (orientation) {
        PillTabOrientation.Horizontal -> {
            HorizontalPillTabBar(
                tabs = tabs,
                selectedIndex = safeIndex,
                onTabSelected = onTabSelected,
                colors = colors,
                showLabels = showLabels,
                modifier = modifier,
            )
        }

        PillTabOrientation.Vertical -> {
            VerticalPillTabBar(
                tabs = tabs,
                selectedIndex = safeIndex,
                onTabSelected = onTabSelected,
                colors = colors,
                showLabels = showLabels,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun HorizontalPillTabBar(
    tabs: List<PillTabSpec>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    colors: PillTabColors,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp, shape = PillShape, clip = false)
                    .clip(PillShape)
                    .background(colors.container)
                    .padding(12.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val tabCount = tabs.size
                    val segmentWidth = maxWidth / tabCount
                    val tabHeight = if (showLabels) 52.dp else 44.dp

                    val gliderOffset by animateDpAsState(
                        targetValue = segmentWidth * selectedIndex,
                        animationSpec = GliderAnimationSpec,
                        label = "pillGliderOffsetX",
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = gliderOffset)
                            .width(segmentWidth)
                            .height(tabHeight)
                            .clip(PillShape)
                            .background(colors.glider),
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { index, tab ->
                            PillTabCell(
                                tab = tab,
                                selected = index == selectedIndex,
                                colors = colors,
                                showLabel = showLabels,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(tabHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalPillTabBar(
    tabs: List<PillTabSpec>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    colors: PillTabColors,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(if (showLabels) 88.dp else 64.dp)
                .shadow(elevation = 6.dp, shape = PillShape, clip = false)
                .clip(PillShape)
                .background(colors.container)
                .padding(12.dp),
        ) {
            BoxWithConstraints {
                val tabCount = tabs.size
                val segmentHeight = maxHeight / tabCount
                val tabWidth = if (showLabels) 64.dp else 40.dp

                val gliderOffset by animateDpAsState(
                    targetValue = segmentHeight * selectedIndex,
                    animationSpec = GliderAnimationSpec,
                    label = "pillGliderOffsetY",
                )

                Box(
                    modifier = Modifier
                        .offset(y = gliderOffset)
                        .width(tabWidth)
                        .height(segmentHeight)
                        .clip(PillShape)
                        .background(colors.glider),
                )

                Column(modifier = Modifier.fillMaxHeight()) {
                    tabs.forEachIndexed { index, tab ->
                        PillTabCell(
                            tab = tab,
                            selected = index == selectedIndex,
                            colors = colors,
                            showLabel = showLabels,
                            onClick = { onTabSelected(index) },
                            modifier = Modifier
                                .width(tabWidth)
                                .height(segmentHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillTabCell(
    tab: PillTabSpec,
    selected: Boolean,
    colors: PillTabColors,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.selectedContent else colors.unselectedContent,
        animationSpec = ColorAnimationSpec,
        label = "pillTabContentColor",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pillTabPressScale",
    )
    val selectedIconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillTabIconScale",
    )
    val icon = when {
        selected && tab.selectedIcon != null -> tab.selectedIcon
        tab.icon != null -> tab.icon
        else -> tab.selectedIcon
    }

    Box(
        modifier = modifier
            .clip(PillShape)
            .animatedClickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                role = Role.Tab,
                haptic = false,
                pressedScale = 1f,
                interactionSource = interactionSource,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = selectedIconScale
                                scaleY = selectedIconScale
                            },
                    )
                    tab.badgeCount?.takeIf { it > 0 }?.let { count ->
                        PillTabBadge(
                            count = count,
                            selected = selected,
                            colors = colors,
                            modifier = Modifier.offset(x = 8.dp, y = (-4).dp),
                        )
                    }
                }
            } else {
                tab.badgeCount?.takeIf { it > 0 }?.let { count ->
                    PillTabBadge(
                        count = count,
                        selected = selected,
                        colors = colors,
                    )
                }
            }
            if (showLabel) {
                if (icon != null) Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PillTabBadge(
    count: Int,
    selected: Boolean,
    colors: PillTabColors,
    modifier: Modifier = Modifier,
) {
    val badgeBackground by animateColorAsState(
        targetValue = if (selected) colors.badgeSelected else colors.badgeDefault,
        animationSpec = ColorAnimationSpec,
        label = "pillBadgeBackground",
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (selected) colors.badgeText else colors.selectedContent,
        animationSpec = ColorAnimationSpec,
        label = "pillBadgeTextColor",
    )
    val label = if (count > 99) "99+" else count.toString()
    val badgeSize = if (label.length > 1) 18.dp else 16.dp

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(badgeBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = badgeTextColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
