package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Holiday
import com.edukasyon.studentai.domain.model.HolidayType
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.isMediumOrExpandedWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.parseHexColor
import com.edukasyon.studentai.ui.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenScanner: () -> Unit = {},
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var addForDay by remember { mutableStateOf(state.selectedDay) }
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var createEventDateMillis by remember { mutableStateOf<Long?>(null) }
    var showQuickActions by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val twoPaneDaily = isMediumOrExpandedWidth()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            StudentAiFab(
                onClick = { showQuickActions = true },
                icon = Icons.Default.Add,
                contentDescription = "Add or scan schedule",
            )
        }
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            Column(contentModifier.fillMaxSize()) {
                GradientHeader(
                    title = "Schedule",
                    subtitle = when (state.viewMode) {
                        "weekly" -> "My week at a glance"
                        "monthly" -> "Monthly overview"
                        else -> state.selectedDay.displayName
                    },
                    inlineSubtitle = true,
                )
                Row(
                    Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly").forEach { (mode, label) ->
                        FilterChip(
                            selected = state.viewMode == mode,
                            onClick = { viewModel.setViewMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
                when (state.viewMode) {
                    "weekly" -> {
                        if (state.isLoading) {
                            LoadingState()
                        } else {
                            WeeklyScheduleGrid(
                                itemsByDay = viewModel.itemsGroupedByDay(),
                                dayTemplates = state.dayTemplates,
                                selectedDay = state.selectedDay,
                                onItemClick = { editingItem = it },
                                onItemMove = { item, targetDay ->
                                    viewModel.moveClassToDay(item, targetDay)?.let { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                },
                                onDayEmptyClick = { day ->
                                    viewModel.selectDay(day)
                                    addForDay = day
                                    showAddDialog = true
                                },
                                onCustomizeTemplates = { showTemplateSheet = true },
                            )
                        }
                    }
                    "monthly" -> MonthlyScheduleView(
                        year = state.calendarYear,
                        allItems = state.allItems,
                        holidays = state.holidays,
                        horizontalPadding = horizontalPadding,
                        onDateClick = { createEventDateMillis = it },
                    )
                    else -> DailyScheduleView(
                        state = state,
                        viewModel = viewModel,
                        horizontalPadding = horizontalPadding,
                        twoPane = twoPaneDaily,
                        onAdd = {
                            addForDay = state.selectedDay
                            showAddDialog = true
                        },
                        onItemClick = { editingItem = it },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ClassFormDialog(
            title = "Add Class",
            day = addForDay,
            onDismiss = { showAddDialog = false },
            onConfirm = { item ->
                viewModel.addClass(item)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { item ->
        ClassActionDialog(
            item = item,
            onDismiss = { editingItem = null },
            onEdit = { updated ->
                viewModel.updateClass(updated)
                editingItem = null
            },
            onDuplicate = { targetDay ->
                val message = viewModel.duplicateClass(item, targetDay)
                scope.launch { snackbarHostState.showSnackbar(message) }
                editingItem = null
            },
            onDelete = {
                viewModel.deleteClass(item.id)
                editingItem = null
            }
        )
    }

    if (showTemplateSheet) {
        DayTemplateCustomizationSheet(
            dayTemplates = state.dayTemplates,
            onDismiss = { showTemplateSheet = false },
            onDayColorSelected = { day, template -> viewModel.setDayTemplate(day, template) },
            onReset = { viewModel.resetDayTemplates() }
        )
    }

    createEventDateMillis?.let { dateMillis ->
        CreateEventBottomSheet(
            dateMillis = dateMillis,
            onDismiss = { createEventDateMillis = null },
            onConfirm = { title, description ->
                viewModel.createCalendarEvent(title, description, dateMillis)
                createEventDateMillis = null
            }
        )
    }

    if (showQuickActions) {
        QuickActionsSheet(
            onDismiss = { showQuickActions = false },
            onScanSchedule = {
                showQuickActions = false
                onOpenScanner()
            },
            onAddClass = {
                showQuickActions = false
                addForDay = state.selectedDay
                showAddDialog = true
            },
        )
    }
}

@Composable
private fun DailyScheduleView(
    state: com.edukasyon.studentai.ui.viewmodel.ScheduleUiState,
    viewModel: ScheduleViewModel,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    twoPane: Boolean,
    onAdd: () -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
) {
    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 180.dp)
                    .fillMaxHeight()
                    .padding(start = horizontalPadding, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = state.selectedDay == day,
                        onClick = { viewModel.selectDay(day) },
                        label = { Text(day.displayName) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            DailyScheduleList(
                state = state,
                viewModel = viewModel,
                horizontalPadding = horizontalPadding,
                onAdd = onAdd,
                onItemClick = onItemClick,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        LazyRow(
            Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DayOfWeek.entries) { day ->
                FilterChip(
                    selected = state.selectedDay == day,
                    onClick = { viewModel.selectDay(day) },
                    label = { Text(day.displayName.take(3)) },
                )
            }
        }
        DailyScheduleList(
            state = state,
            viewModel = viewModel,
            horizontalPadding = horizontalPadding,
            onAdd = onAdd,
            onItemClick = onItemClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DailyScheduleList(
    state: com.edukasyon.studentai.ui.viewmodel.ScheduleUiState,
    viewModel: ScheduleViewModel,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onAdd: () -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(modifier = modifier)
    } else {
        val items = viewModel.itemsForSelectedDay()
        if (items.isEmpty()) {
            EmptyState(
                "No classes on ${state.selectedDay.displayName}",
                "Add a class for this day.",
                actionLabel = "Add Class",
                onAction = onAdd,
                modifier = modifier,
            )
        } else {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val isCurrent = isCurrentClass(item, state.selectedDay)
                    DailyTimelineBlock(
                        item = item,
                        isCurrent = isCurrent,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTimelineBlock(
    item: ScheduleItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val subjectColor = parseHexColor(item.colorHex) ?: MaterialTheme.colorScheme.primary
    val timeLabel = DateUtils.formatTimeRange(item.startTime, item.endTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(52.dp),
        ) {
            Text(
                text = DateUtils.formatTime12h(item.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (isCurrent) MaterialTheme.colorScheme.primary else subjectColor.copy(alpha = 0.5f)),
            )
        }
        Card(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (isCurrent) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium,
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    subjectColor.copy(alpha = 0.12f)
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(subjectColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    "Now",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    Text(timeLabel, style = MaterialTheme.typography.bodySmall)
                    item.room?.let {
                        Text("Room $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.teacher?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyScheduleView(
    year: Int,
    allItems: List<ScheduleItem>,
    holidays: List<Holiday>,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onDateClick: (Long) -> Unit,
) {
    val todayCal = remember { Calendar.getInstance() }
    val holidayByDay = remember(holidays) {
        holidays.groupBy { holiday ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = holiday.dateMillis
            cal.get(Calendar.YEAR) to cal.get(Calendar.DAY_OF_YEAR)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                "$year Calendar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Tap any date to create an event. Red dots mark Philippine holidays.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(12) { monthIndex ->
            MonthCalendarSection(
                year = year,
                monthIndex = monthIndex,
                allItems = allItems,
                holidayByDay = holidayByDay,
                todayYear = todayCal.get(Calendar.YEAR),
                todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR),
                onDateClick = onDateClick,
            )
        }
    }
}

@Composable
private fun MonthCalendarSection(
    year: Int,
    monthIndex: Int,
    allItems: List<ScheduleItem>,
    holidayByDay: Map<Pair<Int, Int>, List<Holiday>>,
    todayYear: Int,
    todayDayOfYear: Int,
    onDateClick: (Long) -> Unit,
) {
    val cal = remember(year, monthIndex) {
        Calendar.getInstance().apply {
            set(year, monthIndex, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(monthName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val cells = (1 until firstDayOfWeek).map { null as Int? } + (1..daysInMonth).map { it }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dayCal = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                        val dayOfYear = dayCal.get(Calendar.DAY_OF_YEAR)
                        val dayKey = year to dayOfYear
                        val dayHolidays = holidayByDay[dayKey].orEmpty()
                        val isToday = year == todayYear && dayOfYear == todayDayOfYear
                        val dow = dayOfWeekFromCalendar(dayCal.get(Calendar.DAY_OF_WEEK))
                        val hasClass = allItems.any { it.dayOfWeek == dow }

                        CalendarDayCell(
                            day = day,
                            isToday = isToday,
                            hasClass = hasClass,
                            holidays = dayHolidays,
                            modifier = Modifier.weight(1f),
                            onClick = { onDateClick(startOfDayMillis(dayCal)) },
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    hasClass: Boolean,
    holidays: List<Holiday>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hasRegularHoliday = holidays.any { it.type == HolidayType.REGULAR }
    val hasSpecialHoliday = holidays.any { it.type == HolidayType.SPECIAL }
    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        hasRegularHoliday -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        hasSpecialHoliday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasClass) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                if (hasRegularHoliday) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                } else if (hasSpecialHoliday) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEventBottomSheet(
    dateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val dateLabel = remember(dateMillis) {
        SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(java.util.Date(dateMillis))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Create Event", style = MaterialTheme.typography.titleLarge)
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), description.trim()) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Event")
            }
        }
    }
}

private fun isCurrentClass(item: ScheduleItem, selectedDay: DayOfWeek): Boolean {
    if (selectedDay != DateUtils.getTodayDayOfWeek()) return false
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val start = parseTimeMinutes(item.startTime)
    val end = parseTimeMinutes(item.endTime)
    return nowMinutes in start until end
}

private fun parseTimeMinutes(time: String): Int {
    val parts = time.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun dayOfWeekFromCalendar(calendarDow: Int): DayOfWeek = when (calendarDow) {
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    else -> DayOfWeek.SUNDAY
}

private fun startOfDayMillis(cal: Calendar): Long {
    val copy = cal.clone() as Calendar
    copy.set(Calendar.HOUR_OF_DAY, 0)
    copy.set(Calendar.MINUTE, 0)
    copy.set(Calendar.SECOND, 0)
    copy.set(Calendar.MILLISECOND, 0)
    return copy.timeInMillis
}


@Composable
private fun ClassFormDialog(
    title: String,
    day: DayOfWeek,
    initial: ScheduleItem? = null,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleItem) -> Unit
) {
    var subject by remember(initial) { mutableStateOf(initial?.subjectName.orEmpty()) }
    var teacher by remember(initial) { mutableStateOf(initial?.teacher.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var startTime by remember(initial) { mutableStateOf(initial?.startTime ?: "08:00") }
    var endTime by remember(initial) { mutableStateOf(initial?.endTime ?: "09:00") }
    var selectedDay by remember(initial, day) { mutableStateOf(initial?.dayOfWeek ?: day) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    subject, { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    teacher, { teacher = it },
                    label = { Text("Teacher") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    room, { room = it },
                    label = { Text("Room") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    startTime, { startTime = it },
                    label = { Text("Start Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    endTime, { endTime = it },
                    label = { Text("End Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Day", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.entries) { d ->
                        FilterChip(
                            selected = selectedDay == d,
                            onClick = { selectedDay = d },
                            label = { Text(d.displayName.take(3)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (subject.isNotBlank()) {
                    onConfirm(
                        ScheduleItem(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            subjectId = initial?.subjectId,
                            subjectName = subject,
                            teacher = teacher.ifBlank { null },
                            room = room.ifBlank { null },
                            building = initial?.building,
                            dayOfWeek = selectedDay,
                            startTime = startTime,
                            endTime = endTime,
                            colorHex = initial?.colorHex ?: "#1A237E",
                            notes = initial?.notes,
                            semester = initial?.semester ?: "",
                            schoolYear = initial?.schoolYear ?: ""
                        )
                    )
                }
            }) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ClassActionDialog(
    item: ScheduleItem,
    onDismiss: () -> Unit,
    onEdit: (ScheduleItem) -> Unit,
    onDuplicate: (DayOfWeek) -> Unit,
    onDelete: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var showDuplicatePicker by remember { mutableStateOf(false) }

    if (showEdit) {
        ClassFormDialog(
            title = "Edit Class",
            day = item.dayOfWeek,
            initial = item,
            onDismiss = { showEdit = false },
            onConfirm = { updated ->
                onEdit(updated)
                showEdit = false
            }
        )
        return
    }

    if (showDuplicatePicker) {
        DuplicateDayDialog(
            item = item,
            onDismiss = { showDuplicatePicker = false },
            onConfirm = { targetDay ->
                onDuplicate(targetDay)
                showDuplicatePicker = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.subjectName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${item.startTime} - ${item.endTime}")
                    item.teacher?.let { Text("Teacher: $it") }
                    item.room?.let { Text("Room: $it") }
                    Text("Day: ${item.dayOfWeek.displayName}")
                }
                HorizontalDivider()
                TextButton(
                    onClick = { showEdit = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }
                }
                TextButton(
                    onClick = { showDuplicatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Duplicate")
                    }
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DuplicateDayDialog(
    item: ScheduleItem,
    onDismiss: () -> Unit,
    onConfirm: (DayOfWeek) -> Unit,
) {
    var selectedDay by remember { mutableStateOf(item.dayOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Copy \"${item.subjectName}\" to which day?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.entries) { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(day.displayName.take(3)) },
                        )
                    }
                }
                Text(
                    "Same time: ${DateUtils.formatTime12h(item.startTime)} – ${DateUtils.formatTime12h(item.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDay) }) { Text("Duplicate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun AddClassDialog(day: DayOfWeek, onDismiss: () -> Unit, onConfirm: (ScheduleItem) -> Unit) {
    ClassFormDialog(title = "Add Class", day = day, onDismiss = onDismiss, onConfirm = onConfirm)
}
