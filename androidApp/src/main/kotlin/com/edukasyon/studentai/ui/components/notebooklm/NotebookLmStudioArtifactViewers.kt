package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.ui.components.MarkdownChatText

// ==========================================
// 1. Interactive Mind Map Viewer
// ==========================================

data class MindMapNode(
    val title: String,
    val description: String = "",
    val children: List<MindMapNode> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmMindMapViewer(
    title: String,
    rootNode: MindMapNode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedNode by remember { mutableStateOf<MindMapNode?>(rootNode) }

    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookLmColors.Background),
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextPrimary,
                        )
                        Text(
                            text = "Interactive Mind Map",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotebookLmColors.StudioMindMap,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NotebookLmColors.TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = NotebookLmColors.TextSecondary)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Horizontal scrollable mind map tree
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NotebookLmColors.Surface)
                    .border(1.dp, NotebookLmColors.BorderSoft, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    // Root Node
                    MindMapNodeCard(
                        title = rootNode.title,
                        color = NotebookLmColors.StudioMindMap,
                        isSelected = selectedNode == rootNode,
                        onClick = { selectedNode = rootNode },
                    )

                    // Level 1 Branches
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        rootNode.children.forEach { branch ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(36.dp),
                            ) {
                                MindMapNodeCard(
                                    title = branch.title,
                                    color = NotebookLmColors.BlueAccent,
                                    isSelected = selectedNode == branch,
                                    onClick = { selectedNode = branch },
                                )

                                if (branch.children.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        branch.children.forEach { leaf ->
                                            MindMapNodeCard(
                                                title = leaf.title,
                                                color = NotebookLmColors.StudioVideo,
                                                isSelected = selectedNode == leaf,
                                                onClick = { selectedNode = leaf },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Node Inspector Detail Card
            selectedNode?.let { node ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NotebookLmColors.SurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = node.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextPrimary,
                        )
                        Text(
                            text = node.description.ifBlank { "Core concept synthesized from active notebook sources." },
                            style = MaterialTheme.typography.bodySmall,
                            color = NotebookLmColors.TextSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MindMapNodeCard(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else NotebookLmColors.SurfaceHighest,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) color else NotebookLmColors.BorderSoft,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = NotebookLmColors.TextPrimary,
            )
        }
    }
}

// ==========================================
// 2. Interactive Slide Deck Presentation Viewer
// ==========================================

data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val points: List<String>,
    val speakerNotes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmSlideDeckViewer(
    deckTitle: String,
    slides: List<SlideItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides.getOrNull(currentSlideIndex) ?: return

    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookLmColors.Background),
                title = {
                    Column {
                        Text(
                            text = deckTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextPrimary,
                        )
                        Text(
                            text = "Slide ${currentSlide.slideNumber} of ${slides.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotebookLmColors.StudioSlideDeck,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NotebookLmColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Fullscreen, contentDescription = "Fullscreen", tint = NotebookLmColors.TextSecondary)
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = NotebookLmColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                        enabled = currentSlideIndex > 0,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NotebookLmColors.TextPrimary),
                    ) {
                        Text("Previous")
                    }

                    Text(
                        text = "${currentSlideIndex + 1} / ${slides.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NotebookLmColors.TextSecondary,
                    )

                    Button(
                        onClick = { if (currentSlideIndex < slides.size - 1) currentSlideIndex++ },
                        enabled = currentSlideIndex < slides.size - 1,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("Next")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Main Slide Canvas (Aspect Ratio Card)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NotebookLmColors.SurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = currentSlide.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = NotebookLmColors.TextPrimary,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        currentSlide.points.forEach { point ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NotebookLmColors.StudioSlideDeck),
                                )
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                                    color = NotebookLmColors.TextPrimary,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "Slide ${currentSlide.slideNumber}",
                            fontSize = 11.sp,
                            color = NotebookLmColors.TextTertiary,
                        )
                    }
                }
            }

            // Speaker Notes Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NotebookLmColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Outlined.SpeakerNotes,
                            contentDescription = null,
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Speaker Notes",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextSecondary,
                        )
                    }
                    Text(
                        text = currentSlide.speakerNotes,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = NotebookLmColors.TextTertiary,
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. Interactive Report / Briefing Doc Viewer
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmReportViewer(
    title: String,
    reportMarkdown: String,
    sourcesCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookLmColors.Background),
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Interactive Report • Grounded in $sourcesCount sources",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotebookLmColors.StudioReports,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NotebookLmColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = NotebookLmColors.TextSecondary)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Export", tint = NotebookLmColors.TextSecondary)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NotebookLmColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MarkdownChatText(
                        markdown = reportMarkdown,
                        contentColor = NotebookLmColors.TextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ==========================================
// 4. Interactive Flashcards Studio Player
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmFlashcardsPlayer(
    deckTitle: String,
    cards: List<Flashcard>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember(currentIndex) { mutableStateOf(false) }
    val currentCard = cards.getOrNull(currentIndex) ?: return

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "card_flip",
    )

    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookLmColors.Background),
                title = {
                    Column {
                        Text(
                            text = deckTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.TextPrimary,
                        )
                        Text(
                            text = "Card ${currentIndex + 1} of ${cards.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotebookLmColors.StudioFlashcards,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NotebookLmColors.TextPrimary)
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = NotebookLmColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentIndex < cards.size - 1) currentIndex++
                            else currentIndex = 0
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Hard")
                    }

                    Button(
                        onClick = {
                            if (currentIndex < cards.size - 1) currentIndex++
                            else currentIndex = 0
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NotebookLmColors.StudioFlashcards),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Good", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (currentIndex < cards.size - 1) currentIndex++
                            else currentIndex = 0
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NotebookLmColors.CorrectGreen),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Easy", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / cards.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NotebookLmColors.StudioFlashcards,
                trackColor = NotebookLmColors.SurfaceHighest,
            )

            // 3D Flip Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(NotebookLmColors.SurfaceHigh)
                    .border(1.dp, NotebookLmColors.Border, RoundedCornerShape(20.dp))
                    .clickable { isFlipped = !isFlipped }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (rotation <= 90f) {
                    // Front: Question
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "QUESTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.StudioFlashcards,
                        )
                        Text(
                            text = currentCard.question,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                            ),
                            color = NotebookLmColors.TextPrimary,
                        )
                        Text(
                            text = "Tap to flip",
                            fontSize = 11.sp,
                            color = NotebookLmColors.TextTertiary,
                        )
                    }
                } else {
                    // Back: Answer
                    Column(
                        modifier = Modifier.graphicsLayer { rotationY = 180f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "ANSWER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NotebookLmColors.CorrectGreen,
                        )
                        Text(
                            text = currentCard.answer,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                            ),
                            color = NotebookLmColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}
