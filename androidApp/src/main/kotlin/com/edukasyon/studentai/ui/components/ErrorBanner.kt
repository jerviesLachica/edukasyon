package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.theme.StudentAiShapes

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) scheme.surfaceContainerHigh else Color.White
    val contentColor = if (isDark) scheme.onSurface else Color(0xFF1A1A1A)
    val subtitleColor = if (isDark) scheme.onSurfaceVariant else contentColor.copy(alpha = 0.72f)
    val borderColor = scheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.28f)
    val iconBackground = scheme.errorContainer.copy(alpha = if (isDark) 0.55f else 0.65f)
    val showTitle = !title.isNullOrBlank() && !title.equals(message, ignoreCase = true)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .shadow(
                elevation = 6.dp,
                shape = StudentAiShapes.snackbar,
                clip = false,
                ambientColor = scheme.primary.copy(alpha = 0.08f),
                spotColor = scheme.primary.copy(alpha = 0.12f),
            )
            .clip(StudentAiShapes.snackbar)
            .border(width = 1.dp, color = borderColor, shape = StudentAiShapes.snackbar),
        shape = StudentAiShapes.snackbar,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(StudentAiShapes.chip)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showTitle) {
                    Text(
                        text = title!!,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = message,
                    style = if (showTitle) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    fontWeight = if (showTitle) FontWeight.Normal else FontWeight.Medium,
                    color = if (showTitle) subtitleColor else contentColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
