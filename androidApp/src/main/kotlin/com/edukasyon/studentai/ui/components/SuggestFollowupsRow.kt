package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.theme.StudentAiShapes

/** Up to three tappable follow-up chips for `suggest_followups`; renders nothing when empty. */
@Composable
fun SuggestFollowupsRow(
    items: List<String>?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = items?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3).orEmpty()
    if (chips.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            OutlinedButton(
                onClick = { onPick(chip) },
                shape = StudentAiShapes.chip,
            ) {
                Text(
                    chip,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
