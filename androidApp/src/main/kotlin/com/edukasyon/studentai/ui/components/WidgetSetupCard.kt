package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.R

@Composable
fun WidgetSetupCard(modifier: Modifier = Modifier) {
    ModernCard(modifier = modifier.padding(horizontal = 16.dp)) {
        Icon(
            Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.widget_add_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.widget_add_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("• 2×2 — tasks or today's schedule", style = MaterialTheme.typography.labelMedium)
            Text("• 2×3 — combined tasks + calendar", style = MaterialTheme.typography.labelMedium)
            Text("• Tap widget to open Planner or Schedule", style = MaterialTheme.typography.labelMedium)
            Text("• Customize accent color when adding", style = MaterialTheme.typography.labelMedium)
        }
    }
}
