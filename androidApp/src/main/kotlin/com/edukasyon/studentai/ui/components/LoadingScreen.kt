package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.R
import com.edukasyon.studentai.ui.components.mascot.MascotMood

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    title: String? = "SchedMate",
    message: String? = null,
    mood: MascotMood = MascotMood.Planning,
    videoFilePath: String? = null,
) {
    val context = LocalContext.current
    val rawResId = remember(mood) {
        val specificId = context.resources.getIdentifier(mood.rawResName, "raw", context.packageName)
        if (specificId != 0) {
            specificId
        } else {
            val generalId = context.resources.getIdentifier("loading_animation", "raw", context.packageName)
            if (generalId != 0) generalId else null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (rawResId != null || !videoFilePath.isNullOrBlank()) {
                // MP4 / Animated Video
                Mp4VideoPlayer(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    rawResId = rawResId,
                    filePath = videoFilePath,
                    isLooping = true,
                    isMuted = true,
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Default Logo + Motif Fallback
                Image(
                    painter = painterResource(R.drawable.wala),
                    contentDescription = "SchedMate logo",
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(24.dp))
                StudentAiLoader(
                    label = message ?: "Loading",
                    style = StudentAiLoaderStyle.Full,
                )
            }

            if (title != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
