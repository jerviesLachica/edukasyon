package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ModernEmptyState(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = "Loading…") {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ModernLoadingState(message = message)
    }
}

@Composable
fun ErrorState(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Please try again",
) {
    ErrorBanner(
        message = message,
        title = title,
        onDismiss = onDismiss,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun StudentAiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModernCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        content = content
    )
}
