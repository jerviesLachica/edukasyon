package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.theme.StudentAiShapes

enum class StudentAiSnackbarType {
    Success,
    Info,
    Error,
}

@Immutable
private data class StudentAiSnackbarColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val iconBackground: Color,
    val iconTint: Color,
)

@Composable
fun StudentAiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        snackbar = { data -> StudentAiSnackbar(data) },
    )
}

@Composable
fun StudentAiSnackbar(data: SnackbarData) {
    val message = data.visuals.message
    val type = remember(message) { inferSnackbarType(message) }
    val colors = rememberStudentAiSnackbarColors(type)
    val icon = snackbarIcon(type)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = StudentAiShapes.snackbar,
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
            .clip(StudentAiShapes.snackbar)
            .border(
                width = 1.dp,
                color = colors.border,
                shape = StudentAiShapes.snackbar,
            ),
        shape = StudentAiShapes.snackbar,
        color = colors.container,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(StudentAiShapes.chip)
                        .background(colors.iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.iconTint,
                    )
                }
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.content,
            )
            data.visuals.actionLabel?.let { actionLabel ->
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberStudentAiSnackbarColors(type: StudentAiSnackbarType): StudentAiSnackbarColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    return remember(type, scheme, isDark) {
        if (isDark) {
            StudentAiSnackbarColors(
                container = scheme.surfaceContainerHigh,
                content = scheme.onSurface,
                border = scheme.outlineVariant.copy(alpha = 0.35f),
                iconBackground = when (type) {
                    StudentAiSnackbarType.Success -> scheme.primaryContainer.copy(alpha = 0.55f)
                    StudentAiSnackbarType.Error -> scheme.errorContainer.copy(alpha = 0.55f)
                    StudentAiSnackbarType.Info -> scheme.secondaryContainer.copy(alpha = 0.45f)
                },
                iconTint = when (type) {
                    StudentAiSnackbarType.Success -> scheme.primary
                    StudentAiSnackbarType.Error -> scheme.error
                    StudentAiSnackbarType.Info -> scheme.secondary
                },
            )
        } else {
            StudentAiSnackbarColors(
                container = Color.White,
                content = Color(0xFF1A1A1A),
                border = scheme.outlineVariant.copy(alpha = 0.28f),
                iconBackground = when (type) {
                    StudentAiSnackbarType.Success -> scheme.primaryContainer.copy(alpha = 0.65f)
                    StudentAiSnackbarType.Error -> scheme.errorContainer.copy(alpha = 0.65f)
                    StudentAiSnackbarType.Info -> Color(0xFFE6EEF9)
                },
                iconTint = when (type) {
                    StudentAiSnackbarType.Success -> Color(0xFF185EE0)
                    StudentAiSnackbarType.Error -> scheme.error
                    StudentAiSnackbarType.Info -> Color(0xFF185EE0)
                },
            )
        }
    }
}

private fun inferSnackbarType(message: String): StudentAiSnackbarType {
    val lower = message.lowercase()
    return when {
        lower.contains("error") ||
            lower.contains("fail") ||
            lower.contains("unable") ||
            lower.contains("unavailable") ||
            lower.contains("invalid") -> StudentAiSnackbarType.Error
        lower.contains("moved") ||
            lower.contains("copied") ||
            lower.contains("saved") ||
            lower.contains("success") ||
            lower.contains("added") ||
            lower.contains("created") ||
            lower.contains("imported") -> StudentAiSnackbarType.Success
        else -> StudentAiSnackbarType.Info
    }
}

private fun snackbarIcon(type: StudentAiSnackbarType): ImageVector? = when (type) {
    StudentAiSnackbarType.Success -> Icons.Default.Check
    StudentAiSnackbarType.Error -> Icons.Default.ErrorOutline
    StudentAiSnackbarType.Info -> Icons.Default.Info
}
