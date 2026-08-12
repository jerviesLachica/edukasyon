package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.ScheduleViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "Add class")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientHeader(
                title = "Schedule",
                subtitle = state.selectedDay.displayName
            )
            LazyRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    EmptyState("No classes on ${state.selectedDay.displayName}", "Add a class for this day.", actionLabel = "Add Class", onAction = { showAddDialog = true })
                } else {
                    LazyColumn {
                        items(items) { item ->
                            StudentAiCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(item.subjectName, style = MaterialTheme.typography.titleMedium)
                                        item.teacher?.let { Text(it) }
                                        Text("${item.startTime} - ${item.endTime}")
                                        item.room?.let { Text("Room $it") }
                                    }
                                    IconButton(onClick = { viewModel.deleteClass(item.id) }) {
                                        Icon(Icons.Default.Delete, "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAddDialog) AddClassDialog(
        day = state.selectedDay,
        onDismiss = { showAddDialog = false },
        onConfirm = { item -> viewModel.addClass(item); showAddDialog = false }
    )
}

@Composable
fun AddClassDialog(day: DayOfWeek, onDismiss: () -> Unit, onConfirm: (ScheduleItem) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("09:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teacher, { teacher = it }, label = { Text("Teacher") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(room, { room = it }, label = { Text("Room") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startTime, { startTime = it }, label = { Text("Start Time") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endTime, { endTime = it }, label = { Text("End Time") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (subject.isNotBlank()) onConfirm(ScheduleItem(
                    UUID.randomUUID().toString(), null, subject, teacher.ifBlank { null },
                    room.ifBlank { null }, null, day, startTime, endTime, "#1A237E", null, "", ""
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
