package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.ScheduleViewModel
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenScanner: () -> Unit = {},
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var addForDay by remember { mutableStateOf(state.selectedDay) }
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var showTemplateSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(
                    onClick = onOpenScanner,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.CameraAlt, "Scan schedule")
                }
                FloatingActionButton(
                    onClick = {
                        addForDay = state.selectedDay
                        showAddDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, "Add class")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientHeader(
                title = "Schedule",
                subtitle = when (state.viewMode) {
                    "weekly" -> "My week at a glance"
                    "monthly" -> "Monthly overview"
                    else -> state.selectedDay.displayName
                }
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly").forEach { (mode, label) ->
                    FilterChip(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        label = { Text(label) }
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
                            onDayEmptyClick = { day ->
                                viewModel.selectDay(day)
                                addForDay = day
                                showAddDialog = true
                            },
                            onCustomizeTemplates = { showTemplateSheet = true }
                        )
                    }
                }
                "monthly" -> MonthlyScheduleView(state.allItems)
                else -> DailyScheduleView(state, viewModel, onAdd = {
                    addForDay = state.selectedDay
                    showAddDialog = true
                })
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
}

@Composable
private fun DailyScheduleView(
    state: com.edukasyon.studentai.ui.viewmodel.ScheduleUiState,
    viewModel: ScheduleViewModel,
    onAdd: () -> Unit
) {
    LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DayOfWeek.entries) { day ->
            FilterChip(
                selected = state.selectedDay == day,
                onClick = { viewModel.selectDay(day) },
                label = { Text(day.displayName.take(3)) }
            )
        }
    }
    if (state.isLoading) LoadingState()
    else {
        val items = viewModel.itemsForSelectedDay()
        if (items.isEmpty()) {
            EmptyState(
                "No classes on ${state.selectedDay.displayName}",
                "Add a class for this day.",
                actionLabel = "Add Class",
                onAction = onAdd
            )
        } else {
            LazyColumn {
                items(items, key = { it.id }) { item ->
                    ScheduleItemCard(item) { viewModel.deleteClass(item.id) }
                }
            }
        }
    }
}

@Composable
private fun MonthlyScheduleView(allItems: List<ScheduleItem>) {
    val cal = remember { Calendar.getInstance() }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(Modifier.padding(16.dp)) {
        Text(
            "${cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())} ${cal.get(Calendar.YEAR)}",
            style = MaterialTheme.typography.titleMedium
        )
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
        val cells = (1 until firstDayOfWeek).map { null as Int? } + (1..daysInMonth).map { it }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val dow = DayOfWeek.entries[(day + firstDayOfWeek - 2) % 7]
                            val hasClass = allItems.any { it.dayOfWeek == dow }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", style = MaterialTheme.typography.bodySmall)
                                if (hasClass) {
                                    Box(
                                        Modifier.size(6.dp).background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.extraSmall
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        allItems.groupBy { it.dayOfWeek }.forEach { (day, items) ->
            Text(day.displayName, style = MaterialTheme.typography.labelLarge)
            items.sortedBy { it.startTime }.forEach {
                Text("• ${it.subjectName} ${it.startTime}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(item: ScheduleItem, onDelete: () -> Unit) {
    StudentAiCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(item.subjectName, style = MaterialTheme.typography.titleMedium)
                item.teacher?.let { Text(it) }
                Text("${item.startTime} - ${item.endTime}")
                item.room?.let { Text("Room $it") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
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
    onDelete: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.subjectName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${item.startTime} - ${item.endTime}")
                item.teacher?.let { Text("Teacher: $it") }
                item.room?.let { Text("Room: $it") }
                Text("Day: ${item.dayOfWeek.displayName}")
            }
        },
        confirmButton = {
            TextButton(onClick = { showEdit = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    )
}

@Composable
fun AddClassDialog(day: DayOfWeek, onDismiss: () -> Unit, onConfirm: (ScheduleItem) -> Unit) {
    ClassFormDialog(title = "Add Class", day = day, onDismiss = onDismiss, onConfirm = onConfirm)
}
