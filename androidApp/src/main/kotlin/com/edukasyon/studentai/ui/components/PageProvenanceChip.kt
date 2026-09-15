package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Displays page provenance chips for flashcards/quiz items that cite specific pages.
 * Each chip shows "Page N" with a document icon, allowing users to verify
 * the source page for a given card or question.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageProvenanceChips(
    pageRefs: List<Int>,
    modifier: Modifier = Modifier,
) {
    if (pageRefs.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pageRefs.forEach { pageNum ->
            PageProvenanceChip(pageNum = pageNum)
        }
    }
}

/**
 * A single page provenance chip showing "Page N".
 */
@Composable
fun PageProvenanceChip(
    pageNum: Int,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(
                "Page $pageNum",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    )
}

/**
 * Utility to extract page references from markdown page notes.
 * Looks for patterns like "**Page 3:**" or "Page 3" in the text.
 */
object PageProvenanceParser {
    private val pagePattern = Regex("""\*\*Page\s+(\d+)\*\*|Page\s+(\d+)""")

    /**
     * Extract page numbers referenced in the given text.
     */
    fun extractPageRefs(text: String): List<Int> {
        return pagePattern.findAll(text)
            .mapNotNull { match ->
                (match.groupValues[1].takeIf { it.isNotEmpty() }
                    ?: match.groupValues[2].takeIf { it.isNotEmpty() })?.toIntOrNull()
            }
            .distinct()
            .sorted()
            .toList()
    }
}
