package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AudioOverviewFormat(val title: String, val description: String) {
    DEEP_DIVE(
        "Deep Dive",
        "A lively conversation between two hosts, unpacking and connecting topics in your sources"
    ),
    BRIEF(
        "Brief",
        "A bite-sized overview to help you grasp the core ideas from your sources quickly"
    ),
    CRITIQUE(
        "Critique",
        "An expert review of your sources, offering constructive feedback to help you improve your material"
    ),
    DEBATE(
        "Debate",
        "A thoughtful debate between two hosts, illuminating different perspectives on your sources"
    )
}

enum class AudioEpisodeLength(val label: String) {
    SHORT("Short"),
    DEFAULT("Default"),
    LONG("Long")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmAudioOverviewCustomizerSheet(
    onDismissRequest: () -> Unit,
    sourcesCount: Int,
    onGenerateNow: (format: AudioOverviewFormat, length: AudioEpisodeLength, prompt: String) -> Unit,
    onGenerateLater: () -> Unit = {},
) {
    var selectedFormat by remember { mutableStateOf(AudioOverviewFormat.DEEP_DIVE) }
    var selectedLength by remember { mutableStateOf(AudioEpisodeLength.DEFAULT) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var focusPrompt by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = NotebookLmColors.Surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = NotebookLmColors.Border,
                shape = CircleShape,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with sparkles and close X (Screenshot 6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = NotebookLmColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Customize Audio Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NotebookLmColors.TextPrimary,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NotebookLmColors.TextSecondary,
                    )
                }
            }

            // Format section header
            Text(
                text = "Format",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NotebookLmColors.TextPrimary,
            )

            // Format cards (Screenshot 6)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AudioOverviewFormat.entries.forEach { format ->
                    val isSelected = selectedFormat == format
                    Surface(
                        onClick = { selectedFormat = format },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) NotebookLmColors.BlueSelectedContainer else NotebookLmColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else NotebookLmColors.BorderSoft
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = format.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NotebookLmColors.TextPrimary,
                                )
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                    color = NotebookLmColors.TextSecondary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(NotebookLmColors.SurfaceHighest)
                                )
                            }
                        }
                    }
                }
            }

            // Choose language (Screenshot 7)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Choose language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NotebookLmColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selectedLanguage, color = NotebookLmColors.TextPrimary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NotebookLmColors.TextSecondary)
                    }
                }
            }

            // Length segmented capsule bar: [ Short ] [ ✓ Default ] [ Long ] (Screenshot 7)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Length",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NotebookLmColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AudioEpisodeLength.entries.forEach { len ->
                            val isSelected = selectedLength == len
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) NotebookLmColors.SurfaceHighest else Color.Transparent)
                                    .clickable { selectedLength = len }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = NotebookLmColors.TextPrimary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    Text(
                                        text = len.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) NotebookLmColors.TextPrimary else NotebookLmColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sources selection dropdown: "13 sources ∨"
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NotebookLmColors.SurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "$sourcesCount sources",
                            fontSize = 13.sp,
                            color = NotebookLmColors.TextPrimary,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // What should the AI hosts focus on in this episode?
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "What should the AI hosts focus on in this episode?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                OutlinedTextField(
                    value = focusPrompt,
                    onValueChange = { focusPrompt = it },
                    placeholder = {
                        Text(
                            "Highlight practical steps for daily use.",
                            color = NotebookLmColors.TextTertiary,
                            fontSize = 13.sp,
                        )
                    },
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NotebookLmColors.TextPrimary,
                        unfocusedTextColor = NotebookLmColors.TextPrimary,
                        focusedBorderColor = NotebookLmColors.BlueAccent,
                        unfocusedBorderColor = NotebookLmColors.Border,
                        focusedContainerColor = NotebookLmColors.SurfaceHigh,
                        unfocusedContainerColor = NotebookLmColors.SurfaceHigh,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // AI Usage progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AI Usage", style = MaterialTheme.typography.bodySmall, color = NotebookLmColors.TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(NotebookLmColors.BlueAccent))
                            Text("Already used", fontSize = 11.sp, color = NotebookLmColors.TextTertiary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                            Text("Estimated use", fontSize = 11.sp, color = NotebookLmColors.TextTertiary)
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { 0.28f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = NotebookLmColors.BlueAccent,
                    trackColor = NotebookLmColors.SurfaceHighest,
                )
            }

            // Actions row: Generate later, Generate now
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    onGenerateLater()
                    onDismissRequest()
                }) {
                    Text("Generate later", color = NotebookLmColors.TextPrimary)
                }

                Button(
                    onClick = {
                        onGenerateNow(selectedFormat, selectedLength, focusPrompt)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NotebookLmColors.SurfaceHighest),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Generate now", color = NotebookLmColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun NotebookLmDockedAudioPlayer(
    title: String,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = NotebookLmColors.SurfaceHigh,
        tonalElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: Title, Thumbs, More, Close (Screenshots 8, 9)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    color = NotebookLmColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = "Good", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.ThumbDown, contentDescription = "Bad", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close player", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Scrubber timeline (Screenshots 8, 9)
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = NotebookLmColors.Border,
                ),
                modifier = Modifier.fillMaxWidth().height(18.dp),
            )

            // Timestamps: e.g. 01:49 / 01:52
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val posMin = positionMs / 60000
                val posSec = (positionMs % 60000) / 1000
                val durMin = durationMs / 60000
                val durSec = (durationMs % 60000) / 1000
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", posMin, posSec, durMin, durSec),
                    fontSize = 11.sp,
                    color = NotebookLmColors.TextTertiary,
                )
            }

            // Bottom controls: Speed, Rewind 10s, Play/Pause, Forward 10s, Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speed button with popup menu
                Box {
                    TextButton(onClick = { showSpeedMenu = true }) {
                        Text(
                            text = "${playbackSpeed}x",
                            fontSize = 13.sp,
                            color = NotebookLmColors.BlueAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(NotebookLmColors.SurfaceHighest),
                    ) {
                        listOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${speed}x",
                                        color = if (playbackSpeed == speed) NotebookLmColors.BlueAccent else NotebookLmColors.TextPrimary,
                                        fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.PlayCircle,
                                        contentDescription = null,
                                        tint = if (playbackSpeed == speed) NotebookLmColors.BlueAccent else NotebookLmColors.TextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    playbackSpeed = speed
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                },
                            )
                        }
                    }
                }

                // Rewind 10s
                IconButton(onClick = onRewind10, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = NotebookLmColors.BlueAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Play / Pause round button
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Forward 10s
                IconButton(onClick = onForward10, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Forward10,
                        contentDescription = "Forward 10s",
                        tint = NotebookLmColors.BlueAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Sleep timer
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = "Timer",
                        tint = NotebookLmColors.TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
