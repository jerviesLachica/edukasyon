package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ErrorBannerBackground = Color(0xFF232531)
private val ErrorBannerIconTint = Color(0xFFD65563)
private val ErrorBannerTitleColor = Color.White
private val ErrorBannerSubtitleColor = Color(0xFF9CA3AF)
private val ErrorBannerDismissColor = Color(0xFF9CA3AF)

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Please try again",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ErrorBannerBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = ErrorBannerIconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = ErrorBannerTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorBannerSubtitleColor,
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
                tint = ErrorBannerDismissColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
