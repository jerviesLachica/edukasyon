package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion

enum class QuizQuestionCount(val label: String) {
    FEWER("Fewer"),
    STANDARD("Standard (Default)"),
    MORE("More")
}

enum class QuizDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium (Default)"),
    HARD("Hard")
}

enum class QuizTypeOption(val label: String) {
    MULTIPLE_CHOICE("Multiple Choice"),
    MULTIPLE_SELECT("Multiple Select"),
    FILL_IN_THE_BLANK("Fill in the Blank"),
    SHORT_ANSWER("Short Answer")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmQuizCustomizerSheet(
    onDismissRequest: () -> Unit,
    sourcesCount: Int,
    onGenerateNow: (
        count: QuizQuestionCount,
        difficulty: QuizDifficulty,
        types: Set<QuizTypeOption>,
        topic: String,
    ) -> Unit,
    onGenerateLater: () -> Unit = {},
) {
    var selectedCount by remember { mutableStateOf(QuizQuestionCount.STANDARD) }
    var selectedDifficulty by remember { mutableStateOf(QuizDifficulty.MEDIUM) }
    var selectedTypes by remember {
        mutableStateOf(setOf(QuizTypeOption.MULTIPLE_CHOICE, QuizTypeOption.MULTIPLE_SELECT))
    }
    var topicPrompt by remember { mutableStateOf("") }

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
            // Header: Quiz icon + Close (Screenshot 6, 7)
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
                        Icons.Outlined.Quiz,
                        contentDescription = null,
                        tint = NotebookLmColors.StudioQuiz,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Quiz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NotebookLmColors.TextPrimary,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = NotebookLmColors.TextSecondary)
                }
            }

            // Number of Questions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Number of Questions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuizQuestionCount.entries.forEach { count ->
                        val isSelected = selectedCount == count
                        Surface(
                            onClick = { selectedCount = count },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NotebookLmColors.SurfaceHighest else NotebookLmColors.SurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NotebookLmColors.BlueAccent else NotebookLmColors.BorderSoft
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = NotebookLmColors.BlueAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = count.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) NotebookLmColors.TextPrimary else NotebookLmColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            // Level of Difficulty
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Level of Difficulty",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NotebookLmColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuizDifficulty.entries.forEach { diff ->
                        val isSelected = selectedDifficulty == diff
                        Surface(
                            onClick = { selectedDifficulty = diff },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NotebookLmColors.SurfaceHighest else NotebookLmColors.SurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NotebookLmColors.BlueAccent else NotebookLmColors.BorderSoft
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = NotebookLmColors.BlueAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = diff.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) NotebookLmColors.TextPrimary else NotebookLmColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            // Question Types (New!)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Question Types",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NotebookLmColors.TextPrimary,
                    )
                    Surface(shape = RoundedCornerShape(10.dp), color = NotebookLmColors.SurfaceHighest) {
                        Text("New!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NotebookLmColors.TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(QuizTypeOption.MULTIPLE_CHOICE, QuizTypeOption.MULTIPLE_SELECT).forEach { opt ->
                        val isSelected = selectedTypes.contains(opt)
                        Surface(
                            onClick = {
                                selectedTypes = if (isSelected) selectedTypes - opt else selectedTypes + opt
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NotebookLmColors.SurfaceHighest else NotebookLmColors.SurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NotebookLmColors.BlueAccent else NotebookLmColors.BorderSoft
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = NotebookLmColors.BlueAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(opt.label, fontSize = 12.sp, color = NotebookLmColors.TextPrimary)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(QuizTypeOption.FILL_IN_THE_BLANK, QuizTypeOption.SHORT_ANSWER).forEach { opt ->
                        val isSelected = selectedTypes.contains(opt)
                        Surface(
                            onClick = {
                                selectedTypes = if (isSelected) selectedTypes - opt else selectedTypes + opt
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NotebookLmColors.SurfaceHighest else NotebookLmColors.SurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NotebookLmColors.BlueAccent else NotebookLmColors.BorderSoft
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = NotebookLmColors.BlueAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(opt.label, fontSize = 12.sp, color = NotebookLmColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            // Sources
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NotebookLmColors.TextPrimary)
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
                        Text("$sourcesCount sources", fontSize = 13.sp, color = NotebookLmColors.TextPrimary)
                        Icon(Icons.Default.ArrowDropDown, null, tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // What should the topic be?
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What should the topic be?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NotebookLmColors.TextPrimary)
                OutlinedTextField(
                    value = topicPrompt,
                    onValueChange = { topicPrompt = it },
                    placeholder = {
                        Text("Add 5 scenario questions testing practical application of the core methodology.", color = NotebookLmColors.TextTertiary, fontSize = 13.sp)
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

                // Quick chips: + Scenario Application, + Distractor Focus, + Concept Testing (Screenshot 6, 7)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("+ Scenario Application", "+ Distractor Focus", "+ Concept Testing").forEach { chip ->
                        Surface(
                            onClick = {
                                topicPrompt = if (topicPrompt.isBlank()) chip.removePrefix("+ ")
                                else "$topicPrompt, ${chip.removePrefix("+ ")}"
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = NotebookLmColors.SurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                        ) {
                            Text(chip, fontSize = 11.sp, color = NotebookLmColors.TextSecondary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }

            // AI Usage
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AI Usage", style = MaterialTheme.typography.bodySmall, color = NotebookLmColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    progress = { 0.35f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = NotebookLmColors.BlueAccent,
                    trackColor = NotebookLmColors.SurfaceHighest,
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                        onGenerateNow(selectedCount, selectedDifficulty, selectedTypes, topicPrompt)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmInteractiveQuizRunner(
    quiz: Quiz,
    onBack: () -> Unit,
    sourcesCount: Int = 13,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentQuestion = quiz.questions.getOrNull(currentIndex) ?: return

    var selectedAnswers by remember(currentIndex) { mutableStateOf(setOf<String>()) }
    var revealed by remember(currentIndex) { mutableStateOf(false) }
    var hintExpanded by remember(currentIndex) { mutableStateOf(false) }
    var expandedExplanations by remember(currentIndex) { mutableStateOf(setOf<Int>()) }

    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Top bar: Back, Title, Share, Edit, Close, More (Screenshot 10, 11)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NotebookLmColors.TextPrimary)
                        }
                        Text(
                            text = quiz.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NotebookLmColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = "Customize", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = NotebookLmColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // "View 13 sources" pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NotebookLmColors.SurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp),
                ) {
                    Text(
                        text = "View $sourcesCount sources",
                        fontSize = 11.sp,
                        color = NotebookLmColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NotebookLmColors.Background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Previous / Next buttons (Screenshots 10, 11)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentIndex > 0) currentIndex--
                        },
                        enabled = currentIndex > 0,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NotebookLmColors.Surface,
                            contentColor = NotebookLmColors.TextPrimary,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text("Previous")
                    }

                    Button(
                        onClick = {
                            if (!revealed) {
                                revealed = true
                            } else if (currentIndex < quiz.questions.size - 1) {
                                currentIndex++
                            } else {
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text(if (currentIndex < quiz.questions.size - 1) "Next" else "Finish")
                    }
                }

                // Good content / Bad content feedback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { },
                    ) {
                        Icon(Icons.Outlined.ThumbUp, null, tint = NotebookLmColors.TextTertiary, modifier = Modifier.size(16.dp))
                        Text("Good content", fontSize = 12.sp, color = NotebookLmColors.TextSecondary)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { },
                    ) {
                        Icon(Icons.Outlined.ThumbDown, null, tint = NotebookLmColors.TextTertiary, modifier = Modifier.size(16.dp))
                        Text("Bad content", fontSize = 12.sp, color = NotebookLmColors.TextSecondary)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Question counter: "3 / 12"
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${currentIndex + 1} / ${quiz.questions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NotebookLmColors.TextTertiary,
                )
                Icon(Icons.Default.MoreVert, null, tint = NotebookLmColors.TextTertiary, modifier = Modifier.size(18.dp))
            }

            // Question Text (Mathematical notation / formulation support)
            Text(
                text = currentQuestion.question,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
                color = NotebookLmColors.TextPrimary,
            )

            // Source citation card note (Screenshots 10, 11)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "A handwritten page showing mixture problem steps",
                    fontSize = 12.sp,
                    color = NotebookLmColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "received_1829255521605605.jpeg",
                    fontSize = 11.sp,
                    color = NotebookLmColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            // Answer choices
            val isMultiSelect = currentQuestion.type.name.contains("MULTI", ignoreCase = true)
            val labels = listOf("A", "B", "C", "D", "E")

            currentQuestion.options.forEachIndexed { optIndex, option ->
                val optLabel = labels.getOrElse(optIndex) { "${optIndex + 1}" }
                val isSelected = selectedAnswers.contains(option)
                val isCorrect = currentQuestion.isAnswerCorrect(option)

                // Styling logic matching Screenshots 10 & 11
                val borderColor = when {
                    !revealed && isSelected -> NotebookLmColors.BlueAccent
                    revealed && isCorrect -> NotebookLmColors.CorrectBorder
                    revealed && isSelected && !isCorrect -> NotebookLmColors.WrongBorder
                    else -> NotebookLmColors.BorderSoft
                }

                val cardBg = when {
                    revealed && isCorrect -> NotebookLmColors.CorrectContainer
                    revealed && isSelected && !isCorrect -> NotebookLmColors.WrongContainer
                    isSelected -> NotebookLmColors.SurfaceHighest
                    else -> NotebookLmColors.Surface
                }

                val isExpanded = expandedExplanations.contains(optIndex)

                Surface(
                    onClick = {
                        if (!revealed) {
                            if (isMultiSelect) {
                                selectedAnswers = if (isSelected) selectedAnswers - option else selectedAnswers + option
                            } else {
                                selectedAnswers = setOf(option)
                                revealed = true
                            }
                        } else {
                            expandedExplanations = if (isExpanded) expandedExplanations - optIndex else expandedExplanations + optIndex
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(if (isSelected || (revealed && isCorrect)) 1.5.dp else 1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (revealed) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isCorrect) NotebookLmColors.CorrectGreen else NotebookLmColors.WrongRed,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Text(
                                        text = "$optLabel.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = NotebookLmColors.TextSecondary,
                                    )
                                }

                                Text(
                                    text = if (revealed && isSelected) "$option · Your answer" else option,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                    ),
                                    color = NotebookLmColors.TextPrimary,
                                )
                            }

                            if (revealed) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand explanation",
                                    tint = NotebookLmColors.TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Detailed explanation on reveal
                        if (revealed) {
                            AnimatedVisibility(
                                visible = isExpanded || isSelected || isCorrect,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 28.dp, top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isCorrect) {
                                        Text(
                                            text = "Right answer",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = NotebookLmColors.CorrectGreen,
                                        )
                                        Text(
                                            text = when (currentIndex) {
                                                0 -> "By setting up the equation 35(0.89) + x(0) = (35 + x)(0.75), we solve for x to find the volume of water needed to achieve the target concentration."
                                                1 -> "Spirals, tessellations, and radial/bilateral symmetries are recognized patterns found in nature."
                                                else -> "To be a 'proper' subset, there must be at least one element in B that is not in A, meaning they cannot be identical."
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                            color = NotebookLmColors.TextSecondary,
                                        )
                                    } else if (isSelected) {
                                        Text(
                                            text = "Not quite",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = NotebookLmColors.WrongRed,
                                        )
                                        Text(
                                            text = when (currentIndex) {
                                                0 -> "This could be a result of confusing the target concentration percentage with the final volume calculation."
                                                1 -> "Linear chaos describes dynamic divergence, not a recurring natural geometric pattern."
                                                else -> "Set A cannot contain more elements than set B, as all elements of A must exist within B."
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                            color = NotebookLmColors.TextSecondary,
                                        )
                                    } else {
                                        Text(
                                            text = when (currentIndex) {
                                                0 -> "This is a common error resulting from dividing the original volume by the percentage difference instead of using the mixture formula."
                                                1 -> "Recognized geometric patterns are ordered symmetries governed by mathematical laws."
                                                else -> "The intersection being empty describes disjoint sets, not proper subsets."
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                            color = NotebookLmColors.TextTertiary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hint Accordion directly under options (Screenshots 9, 10)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { hintExpanded = !hintExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = NotebookLmColors.SurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Hint", fontSize = 12.sp, color = NotebookLmColors.TextSecondary)
                        Icon(
                            if (hintExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = hintExpanded) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = NotebookLmColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = NotebookLmColors.BlueAccent,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Water has a 0% acid concentration; use the balance equation for the total amount of pure acid before and after mixing.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                color = NotebookLmColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            // Status score banner if revealed (Screenshots 10, 11)
            if (revealed) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NotebookLmColors.SlateBlueBanner,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val correctSelected = selectedAnswers.count { currentQuestion.isAnswerCorrect(it) }
                        val totalCorrect = currentQuestion.correctAnswer.split(",").size.coerceAtLeast(1)
                        val scoreLabel = if (isMultiSelect) {
                            if (correctSelected > 0) "$correctSelected/$totalCorrect Correct" else "1 Incorrect"
                        } else {
                            if (correctSelected > 0) "1/1 Correct" else "1 Incorrect"
                        }

                        Text(
                            text = scoreLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NotebookLmColors.TextPrimary,
                        )
                        OutlinedButton(
                            onClick = {},
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = NotebookLmColors.TextPrimary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Icon(Icons.Outlined.Flag, null, tint = NotebookLmColors.TextPrimary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Explain", fontSize = 11.sp, color = NotebookLmColors.TextPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
