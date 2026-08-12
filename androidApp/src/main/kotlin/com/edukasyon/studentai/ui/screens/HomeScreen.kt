package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.columnCount
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTask: () -> Unit,
    onAddClass: () -> Unit,
    onAddNote: () -> Unit,
    onAskAi: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val gridColumns = adaptiveWidth.columnCount(default = 2, medium = 2, expanded = 4)

    if (state.isLoading) {
        LoadingState(message = "Loading dashboard…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 24.dp)
    ) {
        item {
            GradientHeader(
                title = "${state.greeting}, ${state.userName} 👋",
                subtitle = if (state.isOnline) "You're online — AI features ready" else "Offline mode — local data available",
                trailing = {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (state.isOnline) "● Online" else "○ Offline",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = "Classes today",
                    value = state.todaySchedule.size.toString(),
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Tasks due",
                    value = state.upcomingTasks.size.toString(),
                    icon = Icons.Default.TaskAlt,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Exams",
                    value = state.upcomingExams.size.toString(),
                    icon = Icons.Default.School,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader("NEXT CLASS")
            if (state.nextClass != null) {
                ModernCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(state.nextClass!!.subjectName, style = MaterialTheme.typography.titleMedium)
                    state.nextClass!!.room?.let { Text("Room $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        state.nextClass!!.startTime,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                EmptyState("No classes today", "Add your first class to begin.", "Add Class", onAddClass)
            }
        }

        if (adaptiveWidth != AdaptiveWidth.Compact && state.todaySchedule.isNotEmpty()) {
            item {
                SectionHeader("TODAY'S SCHEDULE")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((state.todaySchedule.size + 1) / 2 * 120).coerceAtLeast(120).dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(state.todaySchedule) { item ->
                        ModernCard {
                            Text(item.subjectName, style = MaterialTheme.typography.titleSmall)
                            Text("${item.startTime} - ${item.endTime}", style = MaterialTheme.typography.bodySmall)
                            item.room?.let { Text("Room $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        } else {
            item { SectionHeader("TODAY'S SCHEDULE") }
            if (state.todaySchedule.isEmpty()) {
                item { EmptyState("No classes", "Your schedule is clear today.") }
            } else {
                items(state.todaySchedule) { item ->
                    StudentAiCard {
                        Text(item.subjectName, style = MaterialTheme.typography.titleSmall)
                        Text("${item.startTime} - ${item.endTime}")
                        item.room?.let { Text("Room $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        item { SectionHeader("UPCOMING TASKS") }
        if (state.upcomingTasks.isEmpty()) {
            item { EmptyState("No tasks", "You're all caught up.") }
        } else {
            items(state.upcomingTasks) { task ->
                StudentAiCard {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    Text(task.priority.label, style = MaterialTheme.typography.labelSmall)
                    task.dueDate?.let { Text(DateUtils.formatCountdown(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item { SectionHeader("UPCOMING EXAMS") }
        if (state.upcomingExams.isEmpty()) {
            item { EmptyState("No exams", "No upcoming exams scheduled.") }
        } else {
            items(state.upcomingExams) { exam ->
                StudentAiCard {
                    Text(exam.title, style = MaterialTheme.typography.titleSmall)
                    Text(DateUtils.formatCountdown(exam.examDate))
                }
            }
        }

        state.aiSuggestion?.let { suggestion ->
            item {
                SectionHeader("AI SUGGESTIONS")
                StudentAiCard(onClick = onAskAi) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(suggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            WidgetSetupCard()
        }

        item {
            SectionHeader("QUICK ACTIONS")
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((((4 + gridColumns - 1) / gridColumns) * 96).dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                item {
                    QuickActionTile("Add Task", Icons.Default.Add, onAddTask)
                }
                item {
                    QuickActionTile("Add Class", Icons.Default.CalendarMonth, onAddClass)
                }
                item {
                    QuickActionTile("Create Note", Icons.Default.Note, onAddNote)
                }
                item {
                    QuickActionTile("Ask AI", Icons.Default.Psychology, onAskAi)
                }
            }
        }
    }
}
