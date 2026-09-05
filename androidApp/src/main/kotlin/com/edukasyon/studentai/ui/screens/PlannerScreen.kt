package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.isMediumOrExpandedWidth
import com.edukasyon.studentai.ui.adaptive.listPaneWeight
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.PlannerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onNavigateGrades: () -> Unit = {},
    onNavigateCalendar: () -> Unit = {},
    onOpenAssignmentIntelligence: () -> Unit = {},
    onNavigateFocus: () -> Unit = {},
    initialTaskId: String? = null,
    onInitialTaskConsumed: () -> Unit = {},
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val twoPane = isMediumOrExpandedWidth()
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var editingAssignment by remember { mutableStateOf<Assignment?>(null) }
    var editingExam by remember { mutableStateOf<Exam?>(null) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var linkingExam by remember { mutableStateOf<Exam?>(null) }
    var deletingExam by remember { mutableStateOf<Exam?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialTaskId) {
        if (initialTaskId != null) {
            selectedTaskId = initialTaskId
            onInitialTaskConsumed()
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
    ) { padding ->
    AdaptiveContentContainer(
        modifier = Modifier.padding(padding),
    ) {
        contentModifier ->
        Column(contentModifier.fillMaxSize()) {
            GradientHeader(
                title = "Planner",
                subtitle = when (state.selectedTab) {
                    1 -> "Assignments"
                    2 -> "Exams"
                    else -> "Tasks"
                },
                inlineSubtitle = true,
                trailing = {
                    Row {
                        BouncyIconButton(
                            onClick = onNavigateFocus,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                "Focus",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        BouncyIconButton(
                            onClick = onNavigateCalendar,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                "Calendar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        BouncyIconButton(
                            onClick = onNavigateGrades,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Grade,
                                "Grades",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
            )
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.height(44.dp),
            ) {
                Tab(selected = state.selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Tasks") })
                Tab(selected = state.selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Assignments") })
                Tab(selected = state.selectedTab == 2, onClick = { viewModel.selectTab(2) }, text = { Text("Exams") })
            }
            AnimatedContent(
                targetState = state.selectedTab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "plannerTabs",
            ) { tab ->
                when (tab) {
                    0 -> TaskList(
                        tasks = state.tasks,
                        twoPane = twoPane,
                        listPaneWeight = adaptiveWidth.listPaneWeight(),
                        horizontalPadding = horizontalPadding,
                        selectedTaskId = selectedTaskId,
                        onSelectTask = { selectedTaskId = it },
                        onToggle = viewModel::toggleTask,
                        onDelete = viewModel::deleteTask,
                        onEdit = { editingTask = it },
                        viewModel = viewModel,
                    )
                    1 -> AssignmentList(
                        assignments = state.assignments,
                        twoPane = twoPane,
                        horizontalPadding = horizontalPadding,
                        onComplete = viewModel::completeAssignment,
                        onEdit = { editingAssignment = it },
                        onDelete = viewModel::deleteAssignment,
                    )
                   2 -> ExamList(
                        exams = state.exams,
                        examReadiness = state.examReadiness,
                        expandedExamId = state.expandedExamId,
                        horizontalPadding = horizontalPadding,
                        onDelete = viewModel::deleteExam,
                        onEdit = { editingExam = it },
                        onDuplicate = viewModel::duplicateExam,
                        onToggleExpanded = viewModel::toggleExamExpanded,
                        onLinkStudy = { linkingExam = it },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontalPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = false,
                    onClick = onOpenAssignmentIntelligence,
                    label = { Text("Analyze Assignment") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                BouncyButton(
                    onClick = {
                        if (state.selectedTab == 2) showAddExamDialog = true else showAddDialog = true
                    },
                    shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button,
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    }
    }
    if (showAddDialog) {
        PlannerItemDialog(
            tab = state.selectedTab,
            onDismiss = { showAddDialog = false },
            onSaveTask = viewModel::addTask,
            onSaveAssignment = viewModel::addAssignment,
            onSaveExam = viewModel::addExam
        )
    }
    editingTask?.let { task ->
        PlannerItemDialog(
            tab = 0,
            existingTask = task,
            onDismiss = { editingTask = null },
            onSaveTask = { updated ->
                viewModel.updateTask(updated)
                editingTask = null
            },
            onSaveAssignment = {},
            onSaveExam = {}
        )
    }
    editingAssignment?.let { assignment ->
        PlannerItemDialog(
            tab = 1,
            existingAssignment = assignment,
            onDismiss = { editingAssignment = null },
            onSaveTask = {},
            onSaveAssignment = { updated ->
                viewModel.updateAssignment(updated)
                editingAssignment = null
            },
            onSaveExam = {}
        )
    }
    editingExam?.let { exam ->
        ExamEditDialog(
            existingExam = exam,
            subjects = state.subjects,
            decks = state.jeviDecks,
            onDismiss = { editingExam = null },
            onSave = { updated ->
                viewModel.updateExam(updated)
                editingExam = null
            },
        )
    }
    if (showAddExamDialog) {
        ExamEditDialog(
            existingExam = null,
            subjects = state.subjects,
            decks = state.jeviDecks,
            onDismiss = { showAddExamDialog = false },
            onSave = { exam ->
                viewModel.addExam(exam)
                showAddExamDialog = false
            },
        )
    }
    linkingExam?.let { exam ->
        ExamLinkStudyDialog(
            exam = exam,
            subjects = state.subjects,
            decks = state.jeviDecks,
            onDismiss = { linkingExam = null },
            onConfirm = { subjectId, deckId, newDeckTitle ->
                viewModel.linkExamStudy(exam, subjectId, deckId, newDeckTitle)
                linkingExam = null
            },
        )
    }

    deletingExam?.let { exam ->
        AlertDialog(
            onDismissRequest = { deletingExam = null },
            title = { Text("Delete exam") },
            text = { Text("Are you sure you want to delete \"${exam.title}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExam(exam.id)
                    deletingExam = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExam = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DueDateLabel(
    dueDate: Long?,
    dueTime: String?,
    reminderAt: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (dueDate != null) {
            val dueLine = buildString {
                append("Due ")
                append(DateUtils.formatFullDate(dueDate))
                dueTime?.let { append(" · ${DateUtils.formatTime12h(it)}") }
            }
            Text(
                text = dueLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = DateUtils.formatCountdown(dueDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "No due date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (reminderAt) {
            0L -> Text(
                text = "Reminders off",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            null -> {
                dueDate?.let { date ->
                    DateUtils.effectiveDueMillis(date, dueTime)?.let { dueMillis ->
                        Text(
                            text = "Reminder: ${DateUtils.formatReminderAt(DateUtils.defaultReminderAt(dueMillis))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> Text(
                text = "Reminder: ${DateUtils.formatReminderAt(reminderAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    twoPane: Boolean,
    listPaneWeight: Float,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    selectedTaskId: String?,
    onSelectTask: (String?) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Task) -> Unit,
    viewModel: PlannerViewModel,
) {
    if (tasks.isEmpty()) {
        EmptyState("No tasks", "You're all caught up.")
        return
    }

    val selectedTask = tasks.find { it.id == selectedTaskId }

    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(listPaneWeight)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    val selected = task.id == selectedTaskId
                    Surface(
                        onClick = { onSelectTask(task.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Text(
                            task.title,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f - listPaneWeight)
                    .fillMaxHeight()
                    .padding(end = horizontalPadding, top = 8.dp, bottom = 8.dp),
            ) {
                if (selectedTask != null) {
                    TaskCard(
                        task = selectedTask,
                        onToggle = onToggle,
                        onDelete = onDelete,
                        onEdit = onEdit,
                        viewModel = viewModel,
                    )
                } else {
                    EmptyState(
                        title = "Select a task",
                        message = "Choose a task from the list to view details and subtasks.",
                    )
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onEdit = onEdit,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Task) -> Unit,
    viewModel: PlannerViewModel
) {
    var newSubtask by remember(task.id) { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isCompleted = task.status == TaskStatus.COMPLETED
    val contentIndent = 34.dp

    StudentAiCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedTaskCheckboxRow(
                    checked = isCompleted,
                    onCheckedChange = { onToggle(task.id) },
                    label = task.title,
                    textStyle = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    task.status.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = contentIndent)
                )
                TaskTimestampLabel(task = task, modifier = Modifier.padding(start = contentIndent, top = 2.dp))
            }
            Row {
                BouncyIconButton(onClick = { onEdit(task) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BouncyIconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        task.subtasks.forEach { sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedTaskCheckboxRow(
                    checked = sub.isCompleted,
                    onCheckedChange = { viewModel.toggleSubtask(task.id, sub.id) },
                    label = sub.title,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                BouncyIconButton(
                    onClick = { viewModel.deleteSubtask(task.id, sub.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove subtask",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSubtask,
                onValueChange = { newSubtask = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "New subtask",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newSubtask.isNotBlank()) {
                            viewModel.addSubtask(task.id, newSubtask.trim())
                            newSubtask = ""
                        }
                    }
                )
            )
            FilledIconButton(
                onClick = {
                    if (newSubtask.isNotBlank()) {
                        viewModel.addSubtask(task.id, newSubtask.trim())
                        newSubtask = ""
                    }
                },
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add subtask",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete task") },
            text = { Text("Are you sure you want to delete \"${task.title}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(task.id)
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TaskTimestampLabel(task: Task, modifier: Modifier = Modifier) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    when {
        isCompleted && task.completedAt != null -> {
            Text(
                DateUtils.formatDateTime(task.completedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
        task.dueDate != null -> {
            DueDateLabel(
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                reminderAt = task.reminderAt,
                modifier = modifier,
            )
        }
        else -> {
            Text(
                DateUtils.formatDateTime(task.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun AssignmentList(
    assignments: List<Assignment>,
    twoPane: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onComplete: (String) -> Unit,
    onEdit: (Assignment) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (assignments.isEmpty()) {
        EmptyState("No assignments", "Add your first assignment.")
        return
    }

    val contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp)
    val cardContent: @Composable (Assignment) -> Unit = { assignment ->
        StudentAiCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AnimatedTaskCheckboxRow(
                        checked = assignment.status == TaskStatus.COMPLETED,
                        onCheckedChange = { if (it) onComplete(assignment.id) },
                        label = assignment.title,
                        textStyle = MaterialTheme.typography.titleSmall,
                        enabled = assignment.status != TaskStatus.COMPLETED,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(assignment.status.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 34.dp))
                    DueDateLabel(
                        dueDate = assignment.dueDate,
                        dueTime = assignment.dueTime,
                        reminderAt = assignment.reminderAt,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
                Row {
                    BouncyIconButton(onClick = { onEdit(assignment) }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    BouncyIconButton(onClick = { onDelete(assignment.id) }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }

    if (twoPane) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(assignments, key = { it.id }) { assignment -> cardContent(assignment) }
        }
    } else {
        LazyColumn(contentPadding = contentPadding) {
            items(assignments, key = { it.id }) { assignment -> cardContent(assignment) }
        }
    }
}

@Composable
private fun ExamList(
    exams: List<Exam>,
    examReadiness: Map<String, com.edukasyon.studentai.domain.model.ExamReadiness>,
    expandedExamId: String?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onEdit: (Exam) -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (Exam) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onLinkStudy: (Exam) -> Unit,
) {
    if (exams.isEmpty()) EmptyState("No exams", "Track your upcoming exams here.")
    else LazyColumn(contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp)) {
        items(exams, key = { it.id }) { exam ->
            val readiness = examReadiness[exam.id]
            val expanded = expandedExamId == exam.id
            StudentAiCard(onClick = { onToggleExpanded(exam.id) }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(exam.title, style = MaterialTheme.typography.titleSmall)
                            exam.examTime?.let { Text(DateUtils.formatTime12h(it), style = MaterialTheme.typography.labelSmall) }
                            exam.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        BouncyIconButton(onClick = { onEdit(exam) }) {
                            Icon(Icons.Default.Edit, "Edit exam")
                        }
                        BouncyIconButton(onClick = { onDelete(exam.id) }) {
                            Icon(Icons.Default.Delete, "Delete exam")
                        }
                        BouncyIconButton(onClick = { onDuplicate(exam) }) {
                            Icon(Icons.Default.ContentCopy, "Duplicate exam")
                        }
                    }
                    ExamReadinessCard(
                        exam = exam,
                        readiness = readiness,
                        compact = true,
                        expanded = expanded,
                        onLinkStudy = { onLinkStudy(exam) },
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannerItemDialog(
    tab: Int,
    existingTask: Task? = null,
    existingAssignment: Assignment? = null,
    existingExam: Exam? = null,
    onDismiss: () -> Unit,
    onSaveTask: (Task) -> Unit,
    onSaveAssignment: (Assignment) -> Unit,
    onSaveExam: (Exam) -> Unit
) {
    val isEditing = existingTask != null || existingAssignment != null || existingExam != null
    var title by remember(existingTask, existingAssignment, existingExam) {
        mutableStateOf(existingTask?.title ?: existingAssignment?.title ?: existingExam?.title ?: "")
    }
    var schedule by remember(existingTask, existingAssignment, existingExam) {
        mutableStateOf(
            when {
                existingTask != null -> PlannerScheduleInput.fromDue(
                    existingTask.dueDate,
                    existingTask.dueTime,
                    existingTask.reminderAt
                )
                existingAssignment != null -> PlannerScheduleInput.fromDue(
                    existingAssignment.dueDate,
                    existingAssignment.dueTime,
                    existingAssignment.reminderAt
                )
                existingExam != null -> PlannerScheduleInput.fromDue(
                    existingExam.examDate,
                    existingExam.examTime,
                    existingExam.reminderAt
                )
                else -> PlannerScheduleInput.default()
            }
        )
    }
    var titleError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isEditing && tab == 2 -> "Edit Exam"
                    isEditing && tab == 1 -> "Edit Assignment"
                    isEditing -> "Edit Task"
                    tab == 1 -> "Add Assignment"
                    tab == 2 -> "Add Exam"
                    else -> "Add Task"
                }
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (titleError != null && it.isNotBlank()) titleError = null
                    },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } }
                )
                PlannerScheduleFields(
                    schedule = schedule,
                    onScheduleChange = { schedule = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    titleError = "Please enter a title"
                    return@TextButton
                }
                titleError = null
                val now = System.currentTimeMillis()
                when (tab) {
                    0 -> {
                        val task = existingTask?.copy(
                            title = title,
                            dueDate = schedule.dueDateForSave(),
                            dueTime = schedule.dueTimeForSave(),
                            reminderAt = schedule.reminderAtForSave(),
                            updatedAt = now
                        ) ?: Task(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            description = null,
                            subjectId = null,
                            priority = Priority.MEDIUM,
                            dueDate = schedule.dueDateForSave(),
                            dueTime = schedule.dueTimeForSave(),
                            status = TaskStatus.PENDING,
                            category = null,
                            reminderAt = schedule.reminderAtForSave(),
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null
                        )
                        onSaveTask(task)
                    }
                    1 -> {
                        val assignment = existingAssignment?.copy(
                            title = title,
                            dueDate = schedule.dueDateForSave(),
                            dueTime = schedule.dueTimeForSave(),
                            reminderAt = schedule.reminderAtForSave()
                        ) ?: Assignment(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            subjectId = null,
                            description = null,
                            dueDate = schedule.dueDateForSave(),
                            dueTime = schedule.dueTimeForSave(),
                            attachmentUri = null,
                            priority = Priority.MEDIUM,
                            status = TaskStatus.PENDING,
                            grade = null,
                            notes = null,
                            reminderAt = schedule.reminderAtForSave()
                        )
                        onSaveAssignment(assignment)
                    }
                    2 -> {
                        val exam = existingExam?.copy(
                            title = title,
                            examDate = schedule.dueDateForSave() ?: existingExam.examDate,
                            examTime = schedule.dueTimeForSave(),
                            reminderAt = schedule.reminderAtForSave()
                        ) ?: Exam(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            subjectId = null,
                            examDate = schedule.dueDateForSave() ?: DateUtils.tomorrowStartOfDay(),
                            examTime = schedule.dueTimeForSave(),
                            location = null,
                            coverage = null,
                            notes = null,
                            reminderAt = schedule.reminderAtForSave()
                        )
                        onSaveExam(exam)
                    }
                }
                onDismiss()
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
