package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.BattlePlan
import com.edukasyon.studentai.domain.model.SuggestedAction
import com.edukasyon.studentai.ui.theme.StudentAiShapes

/**
 * P1: Today's Battle Plan card. Theme-safe, offline data only.
 */
@Composable
fun BattlePlanCard(
    plan: BattlePlan,
    onOpenSchedule: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StudentAiShapes.dashboard,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Today's Battle Plan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = plan.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BattlePlanStat(
                    icon = Icons.Default.CalendarMonth,
                    value = "${plan.classesToday}",
                    label = "Classes",
                )
                BattlePlanStat(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    value = "${plan.overdueTasks.size + plan.dueTodayTasks.size}",
                    label = "Due",
                )
                BattlePlanStat(
                    icon = Icons.Default.School,
                    value = "${plan.dueFlashcardsCount}",
                    label = "Cards",
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (plan.suggestedAction) {
                    SuggestedAction.ATTEND_NEXT_CLASS -> Button(onClick = onOpenSchedule) {
                        Text("View schedule")
                    }
                    SuggestedAction.CLEAR_OVERDUE -> Button(onClick = onOpenPlanner) {
                        Text("Clear tasks")
                    }
                    SuggestedAction.REVIEW_DUE_CARDS -> Button(onClick = onOpenReview) {
                        Text("Review now")
                    }
                    SuggestedAction.START_FOCUS_WEAK_SUBJECT -> Button(onClick = onOpenFocus) {
                        Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Focus now")
                    }
                    SuggestedAction.PREPARE_NEXT_EXAM -> Button(onClick = onOpenPlanner) {
                        Text("Prep exam")
                    }
                    SuggestedAction.PLAN_DAY -> OutlinedButton(onClick = onOpenPlanner) {
                        Text("Plan day")
                    }
                }
            }
        }
    }
}

@Composable
private fun BattlePlanStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$value $label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
