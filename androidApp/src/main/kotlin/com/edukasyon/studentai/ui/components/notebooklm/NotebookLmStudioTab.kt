package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.domain.model.Quiz

data class StudioOutputItem(
    val id: String,
    val title: String,
    val type: StudioArtifactType,
    val metadataText: String,
    val durationText: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isPlaying: Boolean = false,
)

enum class StudioArtifactType(val label: String) {
    AUDIO_OVERVIEW("Audio Overview"),
    QUIZ("Quiz"),
    FLASHCARDS("Flashcards"),
    REPORT("Report"),
    MIND_MAP("Mind Map"),
    SLIDE_DECK("Slide Deck"),
    NOTE("Note")
}

val StudioArtifactType.themedIconColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        StudioArtifactType.AUDIO_OVERVIEW -> NotebookLmColors.StudioAudio
        StudioArtifactType.QUIZ -> NotebookLmColors.StudioQuiz
        StudioArtifactType.FLASHCARDS -> NotebookLmColors.StudioFlashcards
        StudioArtifactType.REPORT -> NotebookLmColors.StudioReports
        StudioArtifactType.MIND_MAP -> NotebookLmColors.StudioMindMap
        StudioArtifactType.SLIDE_DECK -> NotebookLmColors.StudioSlideDeck
        StudioArtifactType.NOTE -> NotebookLmColors.TextPrimary
    }

@Composable
fun NotebookLmStudioTab(
    sourcesCount: Int,
    isGeneratingAudio: Boolean = false,
    audioProgressNote: String? = null,
    isGeneratingQuiz: Boolean = false,
    outputs: List<StudioOutputItem>,
    onSelectTile: (StudioArtifactType) -> Unit,
    onOpenOutput: (StudioOutputItem) -> Unit,
    onDeleteOutput: (StudioOutputItem) -> Unit,
    onAddNote: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPromoBanner by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Promo Banner: "New: You can now create Interactive Reports!" (Screenshots 4, 7, 8, 9)
            if (showPromoBanner) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("🎉", fontSize = 20.sp)
                                Text(
                                    text = "New: You can now create Interactive Reports!",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                    ),
                                    color = NotebookLmColors.TextPrimary,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { onSelectTile(StudioArtifactType.REPORT) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = NotebookLmColors.SurfaceHighest,
                                ) {
                                    Text(
                                        text = "Try it",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NotebookLmColors.TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { showPromoBanner = false },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = NotebookLmColors.TextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Two-Column Grid of 9 Studio Tiles (Screenshots 4, 7, 8, 9)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Audio Overview & Slide Deck
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioActionTile(
                            title = "Audio Overview",
                            icon = Icons.Outlined.AutoAwesome,
                            iconColor = NotebookLmColors.StudioAudio,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.AUDIO_OVERVIEW) },
                        )
                        StudioActionTile(
                            title = "Slide Deck",
                            icon = Icons.Outlined.Slideshow,
                            iconColor = NotebookLmColors.StudioSlideDeck,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.SLIDE_DECK) },
                        )
                    }

                    // Row 2: Video Overview & Mind Map
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioActionTile(
                            title = "Video Overview",
                            icon = Icons.Outlined.Videocam,
                            iconColor = NotebookLmColors.StudioVideo,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.REPORT) },
                        )
                        StudioActionTile(
                            title = "Mind Map",
                            icon = Icons.Outlined.AccountTree,
                            iconColor = NotebookLmColors.StudioMindMap,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.MIND_MAP) },
                        )
                    }

                    // Row 3: Reports (with New! badge) & Flashcards
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioActionTile(
                            title = "Reports",
                            icon = Icons.Outlined.Description,
                            iconColor = NotebookLmColors.StudioReports,
                            badge = "New!",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.REPORT) },
                        )
                        StudioActionTile(
                            title = "Flashcards",
                            icon = Icons.Outlined.Style,
                            iconColor = NotebookLmColors.StudioFlashcards,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.FLASHCARDS) },
                        )
                    }

                    // Row 4: Quiz & Infographic
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioActionTile(
                            title = "Quiz",
                            icon = Icons.Outlined.Quiz,
                            iconColor = NotebookLmColors.StudioQuiz,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.QUIZ) },
                        )
                        StudioActionTile(
                            title = "Infographic",
                            icon = Icons.Outlined.BarChart,
                            iconColor = NotebookLmColors.StudioInfographic,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectTile(StudioArtifactType.REPORT) },
                        )
                    }

                    // Row 5: Data Table
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioActionTile(
                            title = "Data Table",
                            icon = Icons.Outlined.TableRows,
                            iconColor = NotebookLmColors.StudioDataTable,
                            modifier = Modifier.weight(0.5f),
                            onClick = { onSelectTile(StudioArtifactType.REPORT) },
                        )
                        Spacer(Modifier.weight(0.5f))
                    }
                }
            }

            // 3. In-Progress Generator Cards (Screenshots 7, 8)
            if (isGeneratingQuiz) {
                item {
                    StudioGeneratingCard(
                        title = "Generating Quiz…",
                        subtitle = "based on $sourcesCount sources",
                    )
                }
            }

            if (isGeneratingAudio) {
                item {
                    StudioGeneratingCard(
                        title = "Generating Audio Overview…",
                        subtitle = audioProgressNote ?: "Come back in a few minutes",
                    )
                }
            }

            // 4. Studio Outputs list (Screenshot 7)
            if (outputs.isNotEmpty()) {
                items(outputs, key = { it.id }) { item ->
                    StudioOutputCard(
                        item = item,
                        onClick = { onOpenOutput(item) },
                        onDelete = { onDeleteOutput(item) },
                    )
                }
            } else if (!isGeneratingAudio && !isGeneratingQuiz) {
                // Empty state (Screenshot 4)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = NotebookLmColors.BlueAccent,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = "Studio output will be saved here.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = NotebookLmColors.BlueAccent,
                        )
                        Text(
                            text = "After adding sources, click to add Audio Overview, Study Guide, Mind Map, and more!",
                            style = MaterialTheme.typography.bodySmall,
                            color = NotebookLmColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }

        // 5. Sticky bottom "+ Add note" button (Screenshots 4, 7, 8, 9)
        Surface(
            onClick = onAddNote,
            shape = RoundedCornerShape(24.dp),
            color = NotebookLmColors.SurfaceHighest,
            border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.NoteAdd,
                    contentDescription = null,
                    tint = NotebookLmColors.TextPrimary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "Add note",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = NotebookLmColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
fun StudioActionTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = NotebookLmColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = NotebookLmColors.TextPrimary,
                )
            }

            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NotebookLmColors.SurfaceHighest,
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NotebookLmColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun StudioGeneratingCard(
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NotebookLmColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = NotebookLmColors.BlueAccent,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    color = NotebookLmColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = NotebookLmColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
fun StudioOutputCard(
    item: StudioOutputItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = when (item.type) {
                        StudioArtifactType.AUDIO_OVERVIEW -> Icons.Outlined.AutoAwesome
                        StudioArtifactType.QUIZ -> Icons.Outlined.Quiz
                        StudioArtifactType.FLASHCARDS -> Icons.Outlined.Style
                        StudioArtifactType.REPORT -> Icons.Outlined.Description
                        StudioArtifactType.MIND_MAP -> Icons.Outlined.AccountTree
                        StudioArtifactType.SLIDE_DECK -> Icons.Outlined.Slideshow
                        StudioArtifactType.NOTE -> Icons.Outlined.EditNote
                    },
                    contentDescription = null,
                    tint = item.type.themedIconColor,
                    modifier = Modifier.size(22.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = NotebookLmColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.metadataText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = NotebookLmColors.TextTertiary,
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = NotebookLmColors.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(NotebookLmColors.SurfaceHigh),
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = NotebookLmColors.WrongRed) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = NotebookLmColors.WrongRed)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
