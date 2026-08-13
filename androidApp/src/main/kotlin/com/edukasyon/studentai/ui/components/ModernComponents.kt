package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.routeToSelectedTab
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.theme.StudentAiShapes

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = StudentAiShapes.card
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .animatedPressScale(interactionSource, pressedScale = 0.96f),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            interactionSource = interactionSource,
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(StudentAiShapes.hero)
            .background(StudentAiGradients.headerBrush())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 32.dp, y = (-16).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-24).dp, y = 24.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                    )
                }
                trailing?.invoke()
            }
            bottomContent?.let {
                Spacer(Modifier.height(20.dp))
                it()
            }
        }
    }
}

@Composable
fun OnlineStatusBadge(isOnline: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = if (isOnline) 0.18f else 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.25f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
                    )
            )
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
fun GlassStatCard(
    label: String,
    value: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentIndex: Int = 0,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 700),
        label = "statValue",
    )
    val shape = StudentAiShapes.dashboard
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudentAiGradients.accentChipBrush(accentIndex)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
            }
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = StudentAiShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GradientQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentIndex: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(StudentAiGradients.accentChipBrush(accentIndex))
            .animatedPressScale(interactionSource, pressedScale = 0.94f)
            .animatedClickable(
                onClick = onClick,
                interactionSource = interactionSource,
                pressedScale = 1f,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModernCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AiSuggestionCard(
    suggestion: String,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = StudentAiShapes.dashboard
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape,
            )
            .animatedPressScale(interactionSource, pressedScale = 0.98f)
            .animatedClickable(onClick = onAskAi, interactionSource = interactionSource, pressedScale = 1f)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Gizmo suggests",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun DashboardSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
        )
        if (actionLabel != null && onAction != null) {
            BouncyTextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun AccentPreviewCard(
    title: String,
    subtitle: String,
    meta: String? = null,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ModernCard(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 5.dp),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meta?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardEmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Inbox,
    emoji: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = StudentAiShapes.dashboard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (emoji != null) {
                Text(text = emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                BouncyButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun NextClassHeroCard(
    subjectName: String,
    startTime: String,
    room: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(StudentAiShapes.dashboard)
            .background(StudentAiGradients.headerBrush())
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Text(
                text = subjectName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            room?.let {
                Text(
                    text = "Room $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = startTime,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun ModernEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            BouncyButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ModernLoadingState(
    message: String = "Loading…",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StarPreloader(containerSize = 80.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StudentAiBottomBar(
    tabs: List<MainTab>,
    currentRoute: String?,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    tabBadges: Map<MainTab, Int> = emptyMap(),
) {
    val selectedTab = routeToSelectedTab(currentRoute)
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    PillTabBar(
        tabs = tabs.map { tab ->
            PillTabSpec(
                label = tab.label,
                icon = tab.unselectedIcon,
                selectedIcon = tab.selectedIcon,
                badgeCount = tabBadges[tab],
            )
        },
        selectedIndex = selectedIndex,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        modifier = modifier,
        orientation = PillTabOrientation.Horizontal,
        showLabels = true,
    )
}

@Composable
fun AdaptiveNavigationRail(
    tabs: List<MainTab>,
    currentRoute: String?,
    onTabSelected: (MainTab) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tabBadges: Map<MainTab, Int> = emptyMap(),
) {
    val selectedTab = routeToSelectedTab(currentRoute)
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        PillTabBar(
            tabs = tabs.map { tab ->
                PillTabSpec(
                    label = tab.label,
                    icon = tab.unselectedIcon,
                    selectedIcon = tab.selectedIcon,
                    badgeCount = tabBadges[tab],
                )
            },
            selectedIndex = selectedIndex,
            onTabSelected = { index -> onTabSelected(tabs[index]) },
            orientation = PillTabOrientation.Vertical,
            showLabels = expanded,
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        ModernCard(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}
