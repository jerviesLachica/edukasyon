package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.theme.StudentAiShapes

/** Up to three tappable follow-up chips for `suggest_followups`; renders horizontally scrollable chips. */
@Composable
fun SuggestFollowupsRow(
    items: List<String>?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = items?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4).orEmpty()
    if (chips.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            AssistChip(
                onClick = { onPick(chip) },
                label = {
                    Text(
                        chip,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                shape = StudentAiShapes.chip,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/** Instant contextual follow-up study actions when no explicit follow-up was generated. */
@Composable
fun ContextualStudySuggestionsRow(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickFollowUps = listOf(
        "💡 Explain step-by-step",
        "🔍 Give real-world example",
        "📝 Quiz me on this",
        "🗂️ Create flashcards",
        "⚡ Key takeaways",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        quickFollowUps.forEach { chip ->
            SuggestionChip(
                onClick = { onPick(chip.substring(3).trim()) },
                label = {
                    Text(
                        chip,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                shape = StudentAiShapes.chip,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            )
        }
    }
}
