package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AiLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = "Generating",
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            GeneratingLoader(
                label = message,
                style = GeneratingLoaderStyle.Full,
            )
        } else {
            GeneratingLoader(style = GeneratingLoaderStyle.Full)
        }
    }
}
