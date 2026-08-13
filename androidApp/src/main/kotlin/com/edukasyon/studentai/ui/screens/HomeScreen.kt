package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.columnCount
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.features.FeatureDestination
import com.edukasyon.studentai.ui.features.FeaturesCatalog
import com.edukasyon.studentai.ui.navigation.Routes
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTask: () -> Unit,
    onAddClass: () -> Unit,
    onAddNote: () -> Unit,
    onAskAi: () -> Unit,
    onNavigateToTab: (com.edukasyon.studentai.ui.navigation.MainTab) -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    onOpenFeaturesGuide: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val gridColumns = adaptiveWidth.columnCount(default = 2, medium = 2, expanded = 4)

    if (state.isLoading) {
        LoadingState(message = "Loading dashboard…")
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudentAiGradients.meshBackgroundBrush())
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (adaptiveWidth == AdaptiveWidth.Compact) 24.dp else 32.dp)
        ) {
            item {
                GradientHeader(
                    title = "${state.greeting}, ${state.userName} 👋",
                    subtitle = if (state.isOnline) {
                        "You're online — AI features ready"
                    } else {
                        "Offline mode — local data available"
                    },
                    trailing = {
                        OnlineStatusBadge(isOnline = state.isOnline)
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-20).dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassStatCard(
                        label = "Classes today",
                        value = state.todaySchedule.size,
                        icon = Icons.Default.CalendarMonth,
                        modifier = Modifier.weight(1f),
                        accentIndex = 0,
                    )
                    GlassStatCard(
                        label = "Tasks due",
                        value = state.upcomingTasks.size,
                        icon = Icons.Default.TaskAlt,
                        modifier = Modifier.weight(1f),
                        accentIndex = 1,
                    )
                    GlassStatCard(
                        label = "Exams",
                        value = state.upcomingExams.size,
                        icon = Icons.Default.School,
                        modifier = Modifier.weight(1f),
                        accentIndex = 2,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GradientQuickAction(
                        label = "Ask Gizmo",
                        icon = Icons.Default.Psychology,
                        onClick = onAskAi,
                        modifier = Modifier.weight(1f),
                        accentIndex = 0,
                    )
                    GradientQuickAction(
                        label = "Scan Schedule",
                        icon = Icons.Default.DocumentScanner,
                        onClick = { onNavigateToRoute(Routes.SCHEDULE_SCANNER) },
                        modifier = Modifier.weight(1f),
                        accentIndex = 1,
                    )
                    GradientQuickAction(
                        label = "Add Task",
                        icon = Icons.Default.Add,
                        onClick = onAddTask,
                        modifier = Modifier.weight(1f),
                        accentIndex = 2,
                    )
                }
            }

            state.aiSuggestion?.let { suggestion ->
                item {
                    AiSuggestionCard(
                        suggestion = suggestion,
                        onAskAi = onAskAi,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                DashboardSectionHeader(title = "Next class")
            }
            item {
                if (state.nextClass != null) {
                    NextClassHeroCard(
                        subjectName = state.nextClass!!.subjectName,
                        startTime = state.nextClass!!.startTime,
                        room = state.nextClass!!.room,
                    )
                } else {
                    DashboardEmptyState(
                        title = "Your day is wide open",
                        message = "No classes on deck — add one or enjoy the break.",
                        emoji = "☀️",
                        actionLabel = "Add class",
                        onAction = onAddClass,
                    )
                }
            }

            item {
                DashboardSectionHeader(title = "Today's schedule")
            }
            if (state.todaySchedule.isEmpty()) {
                item {
                    DashboardEmptyState(
                        title = "Nothing scheduled",
                        message = "Scan a timetable or add classes to fill your day.",
                        icon = Icons.Default.EventAvailable,
                        actionLabel = "Scan schedule",
                        onAction = { onNavigateToRoute(Routes.SCHEDULE_SCANNER) },
                    )
                }
            } else if (adaptiveWidth != AdaptiveWidth.Compact) {
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((state.todaySchedule.size + 1) / 2 * 128).coerceAtLeast(128).dp)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false,
                    ) {
                        items(state.todaySchedule) { item ->
                            SchedulePreviewCard(item = item)
                        }
                    }
                }
            } else {
                items(state.todaySchedule) { item ->
                    SchedulePreviewCard(item = item)
                }
            }

            item {
                DashboardSectionHeader(title = "Upcoming tasks")
            }
            if (state.upcomingTasks.isEmpty()) {
                item {
                    DashboardEmptyState(
                        title = "All caught up",
                        message = "No pending tasks — you're crushing it.",
                        emoji = "✨",
                    )
                }
            } else {
                items(state.upcomingTasks) { task ->
                    TaskPreviewCard(task = task)
                }
            }

            item {
                DashboardSectionHeader(title = "Upcoming exams")
            }
            if (state.upcomingExams.isEmpty()) {
                item {
                    DashboardEmptyState(
                        title = "Exam-free zone",
                        message = "No exams coming up. Stay ahead with Gizmo.",
                        icon = Icons.Default.Celebration,
                    )
                }
            } else {
                items(state.upcomingExams) { exam ->
                    ExamPreviewCard(exam = exam)
                }
            }

            item {
                WidgetSetupCard(modifier = Modifier.padding(top = 8.dp))
            }

            item {
                DashboardSectionHeader(
                    title = "Explore features",
                    actionLabel = "View all →",
                    onAction = onOpenFeaturesGuide,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((((FeaturesCatalog.homeDashboardTiles.size + gridColumns - 1) / gridColumns) * 104).dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(FeaturesCatalog.homeDashboardTiles) { feature ->
                        QuickActionTile(
                            label = feature.title,
                            icon = feature.icon,
                            onClick = {
                                when (val dest = feature.destination) {
                                    is FeatureDestination.Tab -> onNavigateToTab(dest.tab)
                                    is FeatureDestination.Route -> onNavigateToRoute(dest.route)
                                    FeatureDestination.WidgetInstructions -> onOpenFeaturesGuide()
                                }
                            },
                        )
                    }
                }
            }

            item {
                DashboardSectionHeader(title = "Quick actions")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((((4 + gridColumns - 1) / gridColumns) * 104).dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
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
}

@Composable
private fun SchedulePreviewCard(item: ScheduleItem) {
    AccentPreviewCard(
        title = item.subjectName,
        subtitle = buildString {
            append("${item.startTime} – ${item.endTime}")
            item.room?.let { append(" · Room $it") }
        },
        meta = item.teacher?.let { "Prof. $it" },
        accentColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TaskPreviewCard(task: Task) {
    AccentPreviewCard(
        title = task.title,
        subtitle = task.priority.label,
        meta = task.dueDate?.let { DateUtils.formatCountdown(it) },
        accentColor = priorityAccentColor(task.priority),
    )
}

@Composable
private fun ExamPreviewCard(exam: Exam) {
    AccentPreviewCard(
        title = exam.title,
        subtitle = exam.location ?: "Upcoming exam",
        meta = DateUtils.formatCountdown(exam.examDate),
        accentColor = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun priorityAccentColor(priority: Priority): Color = when (priority) {
    Priority.URGENT -> MaterialTheme.colorScheme.error
    Priority.HIGH -> MaterialTheme.colorScheme.tertiary
    Priority.MEDIUM -> MaterialTheme.colorScheme.primary
    Priority.LOW -> MaterialTheme.colorScheme.outline
}
