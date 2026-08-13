package com.edukasyon.studentai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.AcademicOverview
import kotlinx.coroutines.delay

@Composable
fun AcademicOverviewCard(
    overview: AcademicOverview,
    modifier: Modifier = Modifier,
) {
    var sectionVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        sectionVisible = true
    }

    AnimatedVisibility(
        visible = sectionVisible,
        enter = fadeIn(animationSpec = tween(450)) +
            slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = StudentAiShapes.dashboard,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AcademicOverviewHeader()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AcademicOverviewStatTile(
                        label = "Subjects",
                        value = overview.subjectsCount,
                        icon = Icons.Default.MenuBook,
                        staggerIndex = 0,
                        modifier = Modifier.weight(1f),
                    )
                    AcademicOverviewStatTile(
                        label = "GPA",
                        valueText = overview.currentGpa?.let { "%.1f".format(it) } ?: "—",
                        icon = Icons.Outlined.Grade,
                        staggerIndex = 1,
                        highlight = overview.currentGpa != null,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AcademicOverviewStatTile(
                        label = "Tasks",
                        value = overview.tasksCount,
                        icon = Icons.Default.Assignment,
                        staggerIndex = 2,
                        modifier = Modifier.weight(1f),
                    )
                    AcademicOverviewStatTile(
                        label = "Exams",
                        value = overview.upcomingExamsCount,
                        icon = Icons.Outlined.Event,
                        staggerIndex = 3,
                        modifier = Modifier.weight(1f),
                    )
                }

                AcademicOverviewWeekProgress(percent = overview.weekProgressPercent)

                AnimatedVisibility(
                    visible = overview.strongestSubject != null || overview.needsAttentionSubject != null,
                    enter = fadeIn(tween(400, delayMillis = 320)) +
                        slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        overview.strongestSubject?.let { subject ->
                            AcademicOverviewInsightChip(
                                label = "Strongest",
                                subject = subject,
                                icon = Icons.Default.TrendingUp,
                                containerAlpha = 0.22f,
                                contentColor = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        overview.needsAttentionSubject?.let { subject ->
                            AcademicOverviewInsightChip(
                                label = "Needs attention",
                                subject = subject,
                                icon = Icons.Default.TrendingDown,
                                containerAlpha = 0.18f,
                                contentColor = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicOverviewHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudentAiGradients.accentChipBrush(0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Academic Overview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Your progress at a glance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AcademicOverviewStatTile(
    label: String,
    value: Int,
    icon: ImageVector,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    AcademicOverviewStatTileInternal(
        label = label,
        valueText = null,
        intValue = value,
        icon = icon,
        staggerIndex = staggerIndex,
        highlight = highlight,
        modifier = modifier,
    )
}

@Composable
private fun AcademicOverviewStatTile(
    label: String,
    valueText: String,
    icon: ImageVector,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    AcademicOverviewStatTileInternal(
        label = label,
        valueText = valueText,
        intValue = null,
        icon = icon,
        staggerIndex = staggerIndex,
        highlight = highlight,
        modifier = modifier,
    )
}

@Composable
private fun AcademicOverviewStatTileInternal(
    label: String,
    valueText: String?,
    intValue: Int?,
    icon: ImageVector,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L + staggerIndex * 70L)
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "statScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(280),
        label = "statAlpha",
    )

    val animatedInt by animateIntAsState(
        targetValue = intValue ?: 0,
        animationSpec = tween(durationMillis = 650, delayMillis = staggerIndex * 70),
        label = "statInt",
    )
    val displayValue = valueText ?: animatedInt.toString()

    val highlightScale by animateFloatAsState(
        targetValue = if (highlight && visible) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gpaHighlight",
    )

    Surface(
        modifier = modifier
            .scale(scale * highlightScale),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha * 0.92f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * alpha),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * alpha),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                )
            }
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AcademicOverviewWeekProgress(percent: Int) {
    val animatedPercent by animateIntAsState(
        targetValue = percent.coerceIn(0, 100),
        animationSpec = tween(durationMillis = 900, delayMillis = 280),
        label = "weekPercent",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = animatedPercent / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "weekProgress",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "This week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$animatedPercent%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.3.sp,
            )
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun AcademicOverviewInsightChip(
    label: String,
    subject: String,
    icon: ImageVector,
    containerAlpha: Float,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = contentColor.copy(alpha = containerAlpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subject,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
