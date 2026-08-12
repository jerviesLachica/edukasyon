package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                    IconButton(onClick = onNavigateCalendar) {
                        Icon(Icons.Default.CalendarMonth, "Calendar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateGrades) {
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
        Box(Modifier.weight(1f)) {
            when (state.selectedTab) {
                0 -> TaskList(state.tasks, onComplete = viewModel::completeTask, onDelete = viewModel::deleteTask, viewModel = viewModel)
                1 -> AssignmentList(state.assignments)
                2 -> ExamList(state.exams)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = { showAddDialog = true },
                shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
    if (showAddDialog) AddPlannerDialog(state.selectedTab, { showAddDialog = false }, viewModel)
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onComplete: (String) -> Unit,
    onDelete: (String) -> Unit,
    viewModel: PlannerViewModel
) {
    if (tasks.isEmpty()) EmptyState("No tasks", "You're all caught up.")
    else LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(tasks) { task ->
            var expanded by remember(task.id) { mutableStateOf(false) }
            var newSubtask by remember(task.id) { mutableStateOf("") }
            StudentAiCard(modifier = Modifier.clickable { expanded = !expanded }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                        Text(task.status.label, style = MaterialTheme.typography.labelSmall)
                        task.dueDate?.let { Text(DateUtils.formatCountdown(it)) }
                        if (task.subtasks.isNotEmpty()) {
                            Text("${task.subtasks.count { it.isCompleted }}/${task.subtasks.size} subtasks",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        if (task.status != TaskStatus.COMPLETED) {
                            IconButton(onClick = { onComplete(task.id) }) { Icon(Icons.Default.Check, "Complete") }
                        }
                        IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
                if (expanded) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    task.subtasks.forEach { sub ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = sub.isCompleted, onCheckedChange = { viewModel.toggleSubtask(task.id, sub.id) })
                            Text(sub.title, Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deleteSubtask(task.id, sub.id) }) {
                                Icon(Icons.Default.Delete, "Remove subtask", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            newSubtask, { newSubtask = it },
                            Modifier.weight(1f),
                            placeholder = { Text("New subtask") },
                            singleLine = true
                        )
                        IconButton(onClick = {
                            if (newSubtask.isNotBlank()) {
                                viewModel.addSubtask(task.id, newSubtask)
                                newSubtask = ""
                            }
                        }) { Icon(Icons.Default.Add, "Add subtask") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentList(assignments: List<Assignment>) {
    if (assignments.isEmpty()) EmptyState("No assignments", "Add your first assignment.")
    else LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(assignments) { a ->
            StudentAiCard {
                Text(a.title, style = MaterialTheme.typography.titleSmall)
                a.dueDate?.let { Text(DateUtils.formatCountdown(it)) }
                Text(a.status.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ExamList(exams: List<Exam>) {
    if (exams.isEmpty()) EmptyState("No exams", "Track your upcoming exams here.")
    else LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(exams) { exam ->
            StudentAiCard {
                Text(exam.title, style = MaterialTheme.typography.titleSmall)
                Text(DateUtils.formatCountdown(exam.examDate))
                exam.location?.let { Text(it) }
            }
        }
    }
}

@Composable
private fun AddPlannerDialog(tab: Int, onDismiss: () -> Unit, viewModel: PlannerViewModel) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableLongStateOf(System.currentTimeMillis() + 86400000) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (tab) { 1 -> "Add Assignment"; 2 -> "Add Exam"; else -> "Add Task" }) },
        text = { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                val now = System.currentTimeMillis()
                when (tab) {
                    0 -> viewModel.addTask(Task(UUID.randomUUID().toString(), title, null, null, Priority.MEDIUM, dueDate, null, TaskStatus.PENDING, null, null, now, now, null))
                    1 -> viewModel.addAssignment(Assignment(UUID.randomUUID().toString(), title, null, null, dueDate, null, Priority.MEDIUM, TaskStatus.PENDING, null, null))
                    2 -> viewModel.addExam(Exam(UUID.randomUUID().toString(), title, null, dueDate, null, null, null, null, null))
                }
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
