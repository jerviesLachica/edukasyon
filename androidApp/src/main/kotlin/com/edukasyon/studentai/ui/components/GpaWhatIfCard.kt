package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.util.GpaCalculator
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import java.util.UUID

/**
 * P1 viral hook: "If I get X on my Final (weight Y%), my average goes A -> B".
 * Offline, theme-safe, shareable phrasing.
 */
@Composable
fun GpaWhatIfCard(
    entries: List<GradeEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    var scale by remember { mutableStateOf(GpaCalculator.Scale.PH_1_5) }
    var hypotheticalScore by remember { mutableFloatStateOf(90f) }
    var hypotheticalWeight by remember { mutableFloatStateOf(30f) }

    val current = remember(entries, scale) {
        GpaCalculator.cumulativeGpa(entries, scale)
    }
    val projected = remember(entries, scale, hypotheticalScore, hypotheticalWeight) {
        val first = entries.first()
        GpaCalculator.whatIf(
            entries,
            GradeEntry(
                id = UUID.randomUUID().toString(),
                subjectId = first.subjectId,
                assessment = "Hypothetical Final",
                category = first.category,
                score = hypotheticalScore.toDouble(),
                maxScore = 100.0,
                weight = hypotheticalWeight.toDouble().coerceAtLeast(0.5),
                term = first.term,
            ),
            scale,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StudentAiShapes.dashboard,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "What-if GPA",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scale == GpaCalculator.Scale.PH_1_5,
                    onClick = { scale = GpaCalculator.Scale.PH_1_5 },
                    label = { Text("PH 1.0-5.0") },
                )
                FilterChip(
                    selected = scale == GpaCalculator.Scale.US_4_0,
                    onClick = { scale = GpaCalculator.Scale.US_4_0 },
                    label = { Text("US 4.0") },
                )
                FilterChip(
                    selected = scale == GpaCalculator.Scale.PERCENT,
                    onClick = { scale = GpaCalculator.Scale.PERCENT },
                    label = { Text("%") },
                )
            }
            Text(
                text = "If Final: ${hypotheticalScore.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = hypotheticalScore,
                onValueChange = { hypotheticalScore = it },
                valueRange = 50f..100f,
            )
            Text(
                text = "Final weight: ${hypotheticalWeight.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = hypotheticalWeight,
                onValueChange = { hypotheticalWeight = it },
                valueRange = 10f..60f,
            )
            Text(
                text = "Now: ${formatGpa(current, scale)} → If: ${formatGpa(projected, scale)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatGpa(value: Double?, scale: GpaCalculator.Scale): String {
    if (value == null) return "—"
    return when (scale) {
        GpaCalculator.Scale.PERCENT -> "${"%.1f".format(value)}%"
        GpaCalculator.Scale.US_4_0 -> "%.2f".format(value)
        GpaCalculator.Scale.PH_1_5 -> "%.2f".format(value)
    }
}
