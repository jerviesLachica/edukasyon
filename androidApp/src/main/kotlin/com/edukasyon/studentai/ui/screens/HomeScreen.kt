package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.columnCount
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.features.FeatureDestination
import com.edukasyon.studentai.ui.features.FeaturesCatalog
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.Routes
import androidx.compose.ui.graphics.luminance
import com.edukasyon.studentai.ui.viewmodel.HomeViewModel
import com.edukasyon.studentai.ui.viewmodel.HomeWeekDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTask: () -> Unit,
    onAddClass: () -> Unit,
    onAddNote: () -> Unit,
    onAskAi: () -> Unit,
    onNavigateToTab: (MainTab) -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    onOpenFeaturesGuide: () -> Unit = {},
    onNavigateFocus: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val gridColumns = adaptiveWidth.columnCount(default = 2, medium = 3, expanded = 4)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showWidgetCard by rememberSaveable { mutableStateOf(true) }

    if (state.isLoading) {
        LoadingState(message = "Loading dashboard…")
        return
    }

    AdaptiveContentContainer { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (adaptiveWidth == AdaptiveWidth.Compact) 24.dp else 32.dp),
        ) {
            item {
                HomeGreetingHeader(
                    greeting = state.greeting,
                    userName = state.userName,
                    avatarUri = state.avatarUri,
                    showNotificationDot = state.showNotificationDot,
                    onProfileClick = { onNavigateToTab(MainTab.PROFILE) },
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                )
            }

            

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeActivityStatCard(
                        value = state.classesTodayCount,
                        label = "Classes Today",
                        icon = Icons.Default.CalendarMonth,
                        backgroundColor = homePastelBlue(isDark),
                        contentColor = homePastelBlueContent(isDark),
                        modifier = Modifier.weight(1f),
                    )
                    HomeActivityStatCard(
                        value = state.assignmentCount,
                        label = "Assignments",
                        icon = Icons.Default.Edit,
                        backgroundColor = homePastelOrange(isDark),
                        contentColor = homePastelOrangeContent(isDark),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                HomeCompactQuickActions(
                    onAskAi = onAskAi,
                    onAddTask = onAddTask,
                    onFocus = onNavigateFocus,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                )
            }

            state.aiSuggestion?.let { suggestion ->
                item {
                    AiSuggestionCard(
                        suggestion = suggestion,
                        onAskAi = onAskAi,
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
                    )
                }
            }

            item {
                HomeWeekStrip(
                    weekDays = state.weekDays,
                    selectedDay = state.selectedDay,
                    onDaySelected = viewModel::selectDay,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                HomeSectionTitleRow(
                    title = scheduleSectionTitle(state.selectedDay),
                    actionLabel = "See all",
                    onAction = { onNavigateToTab(MainTab.SCHEDULE) },
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
                )
            }

            if (state.selectedDaySchedule.isEmpty()) {
                item {
                    DashboardEmptyState(
                        title = "Nothing scheduled",
                        message = "Scan a timetable or add classes for ${state.selectedDay.displayName}.",
                        icon = Icons.Default.EventAvailable,
                        actionLabel = "Add class",
                        onAction = onAddClass,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            } else {
                items(state.selectedDaySchedule) { item ->
                    HomeClassCard(
                        item = item,
                        isActive = HomeViewModel.isClassActive(item, state.selectedDay),
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 5.dp),
                    )
                }
            }

            item {
                HomeSectionTitleRow(
                    title = "Upcoming",
                    actionLabel = "View all",
                    onAction = { onNavigateToTab(MainTab.PLANNER) },
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                )
            }

            if (state.upcomingTasks.isEmpty()) {
                item {
                    DashboardEmptyState(
                        title = "All caught up",
                        message = "No pending assignments — you're crushing it.",
                        icon = Icons.Default.CheckCircle,
                    )
                }
            } else {
                item {
                    HomeUpcomingTasksCard(
                        tasks = state.upcomingTasks,
                        subjectNames = state.subjectNames,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }

            if (state.upcomingExams.isNotEmpty()) {
                item {
                    HomeSectionTitleRow(
                        title = "Upcoming exams",
                        actionLabel = "View all",
                        onAction = { onNavigateToTab(MainTab.PLANNER) },
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                    )
                }
                items(state.upcomingExams) { exam ->
                    ExamPreviewCard(
                        exam = exam,
                        readiness = state.examReadiness[exam.id],
                    )
                }
            }

            if (showWidgetCard) {
                item {
                    Spacer(Modifier.height(8.dp))
                    WidgetSetupCard(
                        variant = WidgetSetupCardVariant.Home,
                        onDismiss = { showWidgetCard = false },
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }
            }

            item {
                HomeSectionTitleRow(
                    title = "Explore",
                    actionLabel = "View all",
                    onAction = onOpenFeaturesGuide,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                )
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((((FeaturesCatalog.homeDashboardTiles.size + gridColumns - 1) / gridColumns) * 104).dp)
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(FeaturesCatalog.homeDashboardTiles) { feature ->
                        QuickActionTile(
                            label = feature.dashboardLabel ?: feature.title,
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
        }
    }
}

@Composable
private fun HomeGreetingHeader(
    greeting: String,
    userName: String,
    avatarUri: String?,
    showNotificationDot: Boolean,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$userName 👋",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Surface(
                onClick = onProfileClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
                modifier = Modifier.size(44.dp),
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            if (showNotificationDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun HomeActivityStatCard(
    value: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = backgroundColor,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeCompactQuickActions(
    onAskAi: () -> Unit,
    onAddTask: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeQuickActionChip(
            label = "Focus",
            icon = Icons.Default.Timer,
            onClick = onFocus,
            modifier = Modifier.weight(1f),
        )
        HomeQuickActionChip(
            label = "Jevi",
            icon = Icons.Default.Psychology,
            onClick = onAskAi,
            modifier = Modifier.weight(1f),
        )
        HomeQuickActionChip(
            label = "Task",
            icon = Icons.Default.Add,
            onClick = onAddTask,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeQuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeWeekStrip(
    weekDays: List<HomeWeekDay>,
    selectedDay: DayOfWeek,
    onDaySelected: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(weekDays) { day ->
                HomeWeekDayPill(
                    day = day,
                    isSelected = day.dayOfWeek == selectedDay,
                    onClick = { onDaySelected(day.dayOfWeek) },
                )
            }
        }
    }
}

@Composable
private fun HomeWeekDayPill(
    day: HomeWeekDay,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
        },
        shadowElevation = if (isSelected) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = day.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.dateOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTitleRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onAction) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeClassCard(
    item: ScheduleItem,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = parseScheduleColor(item.colorHex)
    val cardColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    val shape = RoundedCornerShape(22.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = cardColor,
        shadowElevation = if (isActive) 0.dp else 2.dp,
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(end = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = item.subjectName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "Now",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                item.teacher?.let { teacher ->
                    Text(
                        text = teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = DateUtils.formatTimeRange(item.startTime, item.endTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.room?.let { room ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = room,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeUpcomingTasksCard(
    tasks: List<Task>,
    subjectNames: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column {
            tasks.forEachIndexed { index, task ->
                HomeUpcomingTaskRow(
                    task = task,
                    subjectName = taskSubjectName(task, subjectNames),
                )
                if (index < tasks.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeUpcomingTaskRow(
    task: Task,
    subjectName: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(priorityAccentColor(task.priority)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subjectName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        task.dueDate?.let { due ->
            HomeDueBadge(label = formatDueBadge(due))
        }
    }
}

@Composable
private fun HomeDueBadge(label: String) {
    val isSoon = label == "Today" || label == "Tomorrow"
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSoon) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isSoon) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ExamPreviewCard(
    exam: Exam,
    readiness: com.edukasyon.studentai.domain.model.ExamReadiness?,
) {
    ExamReadinessCard(
        exam = exam,
        readiness = readiness,
        compact = true,
    )
}

@Composable
private fun priorityAccentColor(priority: Priority): Color = when (priority) {
    Priority.URGENT -> MaterialTheme.colorScheme.error
    Priority.HIGH -> Color(0xFF43A047)
    Priority.MEDIUM -> MaterialTheme.colorScheme.primary
    Priority.LOW -> MaterialTheme.colorScheme.outline
}

private fun scheduleSectionTitle(selectedDay: DayOfWeek): String {
    val today = DateUtils.getTodayDayOfWeek()
    return if (selectedDay == today) "Today's Classes" else "${selectedDay.displayName}'s Classes"
}

private fun taskSubjectName(task: Task, subjectNames: Map<String, String>): String? =
    task.subjectId?.let { subjectNames[it] } ?: task.category?.takeIf { it.isNotBlank() }

private fun formatDueBadge(dueDate: Long): String {
    val days = DateUtils.daysUntil(dueDate)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dueDate))
        else -> DateUtils.formatCountdown(dueDate)
    }
}

private fun parseScheduleColor(colorHex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFF1976D2))

@Composable
private fun homePastelBlue(isDark: Boolean): Color = if (isDark) {
    Color(0xFF1A2940)
} else {
    Color(0xFFE3F2FD)
}

@Composable
private fun homePastelBlueContent(isDark: Boolean): Color = if (isDark) {
    Color(0xFF90CAF9)
} else {
    Color(0xFF1565C0)
}

@Composable
private fun homePastelOrange(isDark: Boolean): Color = if (isDark) {
    Color(0xFF3A2A1E)
} else {
    Color(0xFFFFF3E0)
}

@Composable
private fun homePastelOrangeContent(isDark: Boolean): Color = if (isDark) {
    Color(0xFFFFB74D)
} else {
    Color(0xFFE65100)
}

