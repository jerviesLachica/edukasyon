package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.PlannerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onNavigateGrades: () -> Unit = {},
    onNavigateCalendar: () -> Unit = {},
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var editingAssignment by remember { mutableStateOf<Assignment?>(null) }

    Column(Modifier.fillMaxSize()) {
        GradientHeader(
            title = "Planner",
            subtitle = when (state.selectedTab) {
                1 -> "Assignments"
                2 -> "Exams"
                else -> "Tasks"
            },
            trailing = {
                Row {
                    BouncyIconButton(onClick = onNavigateCalendar) {
                        Icon(Icons.Default.CalendarMonth, "Calendar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    BouncyIconButton(onClick = onNavigateGrades) {
                        Icon(Icons.Default.Grade, "Grades", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        )
        TabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                    onToggle = viewModel::toggleTask,
                    onDelete = viewModel::deleteTask,
                    onEdit = { editingTask = it },
                    viewModel = viewModel
                )
                1 -> AssignmentList(
                    assignments = state.assignments,
                    onComplete = viewModel::completeAssignment,
                    onEdit = { editingAssignment = it },
                    onDelete = viewModel::deleteAssignment
                )
                2 -> ExamList(state.exams)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            BouncyButton(
                onClick = { showAddDialog = true },
                shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
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
}

@Composable
private fun DueDateLabel(dueDate: Long?, dueTime: String?, reminderAt: Long?) {
    dueDate?.let {
        Text(
            DateUtils.formatDueDateTime(it, dueTime) ?: DateUtils.formatFullDate(it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(DateUtils.formatCountdown(it), style = MaterialTheme.typography.labelSmall)
    } ?: Text("No due date", style = MaterialTheme.typography.labelSmall)
    when (reminderAt) {
        0L -> Text("Reminders off", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        null -> {
            dueDate?.let { date ->
                DateUtils.effectiveDueMillis(date, dueTime)?.let { dueMillis ->
                    Text(
                        "Reminder: ${DateUtils.formatReminderAt(DateUtils.defaultReminderAt(dueMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> Text(
            "Reminder: ${DateUtils.formatReminderAt(reminderAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Task) -> Unit,
    viewModel: PlannerViewModel
) {
    if (tasks.isEmpty()) EmptyState("No tasks", "You're all caught up.")
    else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onToggle = onToggle,
                onDelete = onDelete,
                onEdit = onEdit,
                viewModel = viewModel
            )
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
                BouncyIconButton(onClick = { onDelete(task.id) }, modifier = Modifier.size(40.dp)) {
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
            BouncyIconButton(
                onClick = {
                    if (newSubtask.isNotBlank()) {
                        viewModel.addSubtask(task.id, newSubtask.trim())
                        newSubtask = ""
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add subtask",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
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
            Box(modifier) {
                DueDateLabel(task.dueDate, task.dueTime, task.reminderAt)
            }
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
    onComplete: (String) -> Unit,
    onEdit: (Assignment) -> Unit,
    onDelete: (String) -> Unit
) {
    if (assignments.isEmpty()) EmptyState("No assignments", "Add your first assignment.")
    else LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(assignments, key = { it.id }) { assignment ->
            StudentAiCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        AnimatedTaskCheckboxRow(
                            checked = assignment.status == TaskStatus.COMPLETED,
                            onCheckedChange = { if (it) onComplete(assignment.id) },
                            label = assignment.title,
                            textStyle = MaterialTheme.typography.titleSmall,
                            enabled = assignment.status != TaskStatus.COMPLETED
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(assignment.status.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 34.dp))
                        Box(Modifier.padding(start = 34.dp)) {
                            DueDateLabel(assignment.dueDate, assignment.dueTime, assignment.reminderAt)
                        }
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
    }
}

@Composable
private fun ExamList(exams: List<Exam>) {
    if (exams.isEmpty()) EmptyState("No exams", "Track your upcoming exams here.")
    else LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(exams, key = { it.id }) { exam ->
            StudentAiCard {
                Text(exam.title, style = MaterialTheme.typography.titleSmall)
                Text(DateUtils.formatCountdown(exam.examDate))
                Text(DateUtils.formatFullDate(exam.examDate), style = MaterialTheme.typography.bodySmall)
                exam.examTime?.let { Text(DateUtils.formatTime12h(it), style = MaterialTheme.typography.labelSmall) }
                exam.location?.let { Text(it) }
            }
        }
    }
}

@Composable
private fun PlannerItemDialog(
    tab: Int,
    existingTask: Task? = null,
    existingAssignment: Assignment? = null,
    onDismiss: () -> Unit,
    onSaveTask: (Task) -> Unit,
    onSaveAssignment: (Assignment) -> Unit,
    onSaveExam: (Exam) -> Unit
) {
    val isEditing = existingTask != null || existingAssignment != null
    var title by remember(existingTask, existingAssignment) {
        mutableStateOf(existingTask?.title ?: existingAssignment?.title ?: "")
    }
    var schedule by remember(existingTask, existingAssignment) {
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
                else -> PlannerScheduleInput.default()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
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
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (tab != 2) {
                    PlannerScheduleFields(
                        schedule = schedule,
                        onScheduleChange = { schedule = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
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
                    2 -> onSaveExam(
                        Exam(
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
                    )
                }
                onDismiss()
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
