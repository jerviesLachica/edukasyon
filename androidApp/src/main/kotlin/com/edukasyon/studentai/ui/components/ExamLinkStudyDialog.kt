package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.model.Subject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamLinkStudyDialog(
    exam: Exam,
    subjects: List<Subject>,
    decks: List<JeviDeck>,
    onDismiss: () -> Unit,
    onConfirm: (subjectId: String, deckId: String?, newDeckTitle: String?) -> Unit,
) {
    var selectedSubjectId by remember(exam.id, subjects) {
        mutableStateOf(exam.subjectId ?: subjects.firstOrNull()?.id)
    }
    var deckExpanded by remember { mutableStateOf(false) }
    var selectedDeckId by remember(exam.id, decks) {
        mutableStateOf(exam.linkedDeckId ?: decks.firstOrNull()?.id)
    }
    var createNewDeck by remember { mutableStateOf(decks.isEmpty()) }
    var newDeckTitle by remember(exam.id) {
        mutableStateOf(exam.title.trim().ifBlank { "Exam prep" })
    }

    val subjectDecks = decks.filter { it.subjectId == selectedSubjectId || it.subjectId == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link study data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Connect ${exam.title} to a subject and JEVI deck for readiness tracking.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )

                var subjectExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it },
                ) {
                    OutlinedTextField(
                        value = subjects.find { it.id == selectedSubjectId }?.name ?: "Select subject",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false },
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.name) },
                                onClick = {
                                    selectedSubjectId = subject.id
                                    selectedDeckId = decks.firstOrNull {
                                        it.subjectId == subject.id
                                    }?.id
                                    subjectExpanded = false
                                },
                            )
                        }
                    }
                }

                if (subjectDecks.isNotEmpty() && !createNewDeck) {
                    ExposedDropdownMenuBox(
                        expanded = deckExpanded,
                        onExpandedChange = { deckExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = subjectDecks.find { it.id == selectedDeckId }?.title ?: "Select deck",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("JEVI deck") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deckExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = deckExpanded,
                            onDismissRequest = { deckExpanded = false },
                        ) {
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
                    TextButton(onClick = { createNewDeck = true }) {
                        Text("Create new deck instead")
                    }
                } else {
                    OutlinedTextField(
                        value = newDeckTitle,
                        onValueChange = { newDeckTitle = it },
                        label = { Text("New deck title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (subjectDecks.isNotEmpty()) {
                        TextButton(onClick = { createNewDeck = false }) {
                            Text("Use existing deck")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val subjectId = selectedSubjectId ?: return@TextButton
                    if (createNewDeck) {
                        onConfirm(subjectId, null, newDeckTitle.trim())
                    } else {
                        onConfirm(subjectId, selectedDeckId, null)
                    }
                },
                enabled = selectedSubjectId != null &&
                    (createNewDeck && newDeckTitle.isNotBlank() || !createNewDeck && selectedDeckId != null),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
