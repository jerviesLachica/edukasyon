package com.edukasyon.studentai.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberAdaptiveHorizontalPadding(): Dp = when (rememberAdaptiveWidth()) {
    AdaptiveWidth.Compact -> 16.dp
    AdaptiveWidth.Medium -> 24.dp
    AdaptiveWidth.Expanded -> 32.dp
}

@Composable
fun rememberChatContentMaxWidth(): Dp = when (rememberAdaptiveWidth()) {
    AdaptiveWidth.Compact -> Dp.Unspecified
    AdaptiveWidth.Medium -> 640.dp
    AdaptiveWidth.Expanded -> 720.dp
}

@Composable
fun rememberFlashcardMaxWidth(): Dp = when (rememberAdaptiveWidth()) {
    AdaptiveWidth.Compact -> Dp.Unspecified
    AdaptiveWidth.Medium -> 480.dp
    AdaptiveWidth.Expanded -> 560.dp
}

/**
 * Centers content and caps width on medium/expanded screens so lists and forms
 * do not stretch edge-to-edge on tablets and foldables.
 */
@Composable
fun AdaptiveContentContainer(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.(Modifier) -> Unit,
) {
    val contentMaxWidth = rememberContentMaxWidth()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment,
    ) {
        content(
            Modifier
                .widthIn(max = contentMaxWidth.dp)
                .fillMaxWidth(),
        )
    }
}
