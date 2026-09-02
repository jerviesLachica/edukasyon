package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.Subject
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditDialog(
    existingExam: Exam? = null,
    subjects: List<Subject> = emptyList(),
    decks: List<JeviDeck> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
) {
    val isEditing = existingExam != null
    var title by remember(existingExam) { mutableStateOf(existingExam?.title.orEmpty()) }
    var location by remember(existingExam) { mutableStateOf(existingExam?.location.orEmpty()) }
    var coverage by remember(existingExam) { mutableStateOf(existingExam?.coverage.orEmpty()) }
    var notes by remember(existingExam) { mutableStateOf(existingExam?.notes.orEmpty()) }
    var selectedSubjectId by remember(existingExam, subjects) {
        mutableStateOf(existingExam?.subjectId)
    }
    var selectedDeckId by remember(existingExam, decks) {
        mutableStateOf(existingExam?.linkedDeckId)
    }
    var subjectExpanded by remember { mutableStateOf(false) }
    var deckExpanded by remember { mutableStateOf(false) }
    var schedule by remember(existingExam) {
        mutableStateOf(
            existingExam?.let {
                PlannerScheduleInput.fromDue(it.examDate, it.examTime, it.reminderAt)
            } ?: PlannerScheduleInput.defaultForExam(),
        )
    }
    var showMoreDetails by remember { mutableStateOf(false) }

    val subjectDecks = decks.filter { deck ->
        selectedSubjectId == null || deck.subjectId == selectedSubjectId || deck.subjectId == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Exam" else "Add Exam") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (subjects.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = subjectExpanded,
                        onExpandedChange = { subjectExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = subjects.find { it.id == selectedSubjectId }?.name ?: "No subject",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subject") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = subjectExpanded,
                            onDismissRequest = { subjectExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("No subject") },
                                onClick = {
                                    selectedSubjectId = null
                                    selectedDeckId = null
                                    subjectExpanded = false
                                },
                            )
                            subjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text(subject.name) },
                                    onClick = {
                                        selectedSubjectId = subject.id
                                        selectedDeckId = decks.firstOrNull { it.subjectId == subject.id }?.id
                                        subjectExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (subjectDecks.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = deckExpanded,
                        onExpandedChange = { deckExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = subjectDecks.find { it.id == selectedDeckId }?.title ?: "No deck",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked JEVI deck") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = deckExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = deckExpanded,
                            onDismissRequest = { deckExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("No deck") },
                                onClick = {
                                    selectedDeckId = null
                                    deckExpanded = false
                                },
                            )
                            subjectDecks.forEach { deck ->
                                DropdownMenuItem(
                                    text = { Text(deck.title) },
                                    onClick = {
                                        selectedDeckId = deck.id
                                        deckExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                PlannerScheduleFields(
                    schedule = schedule,
                    onScheduleChange = { schedule = it },
                )

                OutlinedButton(
                    onClick = { showMoreDetails = !showMoreDetails },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (showMoreDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showMoreDetails) "Hide details" else "More details")
                }

                if (showMoreDetails) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = coverage,
                            onValueChange = { coverage = it },
                            label = { Text("Coverage / topics") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) return@TextButton
                    val exam = existingExam?.copy(
                        title = title.trim(),
                        subjectId = selectedSubjectId,
                        linkedDeckId = selectedDeckId,
                        examDate = schedule.dueDateForSave() ?: existingExam.examDate,
                        examTime = schedule.dueTimeForSave(),
                        location = location.trim().ifBlank { null },
                        coverage = coverage.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        reminderAt = schedule.reminderAtForSave(),
                    ) ?: Exam(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        subjectId = selectedSubjectId,
                        linkedDeckId = selectedDeckId,
                        examDate = schedule.dueDateForSave() ?: DateUtils.tomorrowStartOfDay(),
                        examTime = schedule.dueTimeForSave(),
                        location = location.trim().ifBlank { null },
                        coverage = coverage.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        reminderAt = schedule.reminderAtForSave(),
                    )
                    onSave(exam)
                },
                enabled = title.isNotBlank(),
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun ExamEditDialogAddPreview() {
    StudentAiTheme {
        ExamEditDialog(
            subjects = listOf(
                Subject("1", "Mathematics", "MATH101", "Dr. Smith", "#4F46E5", "1", "2024"),
                Subject("2", "Physics", "PHYS102", "Dr. Brown", "#06B6D4", "1", "2024")
            ),
            decks = listOf(
                JeviDeck("d1", "Calculus I", null, "1", null, "#4F46E5", 0L, 0L),
                JeviDeck("d2", "Mechanics", null, "2", null, "#06B6D4", 0L, 0L)
            ),
            onDismiss = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ExamEditDialogEditPreview() {
    StudentAiTheme {
        ExamEditDialog(
            existingExam = Exam(
                id = "e1",
                title = "Midterm Exam",
                subjectId = "1",
                linkedDeckId = "d1",
                examDate = System.currentTimeMillis(),
                examTime = "10:00 AM",
                location = "Room 302",
                coverage = "Chapters 1-5",
                notes = "Bring calculator",
                reminderAt = null
            ),
            subjects = listOf(
                Subject("1", "Mathematics", "MATH101", "Dr. Smith", "#4F46E5", "1", "2024")
            ),
            decks = listOf(
                JeviDeck("d1", "Calculus I", null, "1", null, "#4F46E5", 0L, 0L)
            ),
            onDismiss = {},
            onSave = {}
        )
    }
}
