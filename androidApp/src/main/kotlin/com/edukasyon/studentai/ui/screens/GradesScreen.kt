package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.GradeCalculator
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Subject
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.isMediumOrExpandedWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.theme.parseHexColor
import com.edukasyon.studentai.ui.viewmodel.GradesViewModel
import java.util.UUID

private val gradeCategories = listOf("Quiz", "Exam", "Assignment", "Project", "Participation", "General")
private val gradeTerms = listOf("1st", "2nd", "3rd", "4th")

private data class SubjectGradeGroup(
    val subjectId: String,
    val subjectName: String,
    val colorHex: String,
    val entries: List<GradeEntry>,
    val averagePercent: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(viewModel: GradesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val useGrid = isMediumOrExpandedWidth()
    var showAddSheet by remember { mutableStateOf(false) }
    var preselectedSubjectId by remember { mutableStateOf<String?>(null) }

    val filteredEntries = remember(state.entries, state.selectedTerm) {
        if (state.selectedTerm == null) state.entries
        else state.entries.filter { it.term == state.selectedTerm }
    }

    val subjectGroups = remember(filteredEntries, state.subjects) {
        buildSubjectGroups(filteredEntries, state.subjects)
    }

    val filteredWeighted = remember(filteredEntries) {
        GradeCalculator.calculateWeightedGrade(filteredEntries)
    }

    val availableTerms = remember(state.entries) {
        state.entries.map { it.term }.distinct().sorted()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    preselectedSubjectId = null
                    showAddSheet = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp)) },
                text = { Text("Add Grade") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            )
        }
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item {
                    GradientHeader(
                        title = "Grades",
                        subtitle = if (filteredEntries.isEmpty()) {
                            "Track your academic progress"
                        } else {
                            "Overall weighted average · ${"%.1f".format(filteredWeighted)}%"
                        },
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    StatChip(
                        label = "Weighted avg",
                        value = "${"%.1f".format(filteredWeighted)}%",
                        icon = Icons.Default.Grade,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = "Entries",
                        value = filteredEntries.size.toString(),
                        icon = Icons.Default.MenuBook,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = "Subjects",
                        value = subjectGroups.size.toString(),
                        icon = Icons.Default.School,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (availableTerms.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedTerm == null,
                            onClick = { viewModel.setSelectedTerm(null) },
                            label = { Text("All") }
                        )
                        availableTerms.forEach { term ->
                            FilterChip(
                                selected = state.selectedTerm == term,
                                onClick = {
                                    viewModel.setSelectedTerm(if (state.selectedTerm == term) null else term)
                                },
                                label = { Text(term) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    ModernEmptyState(
                        title = "No grades yet",
                        message = "Add your first assessment to start tracking your weighted average and subject progress.",
                        actionLabel = "Add Grade",
                        onAction = {
                            preselectedSubjectId = null
                            showAddSheet = true
                        },
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
                } else {
                    item { SectionHeader("BY SUBJECT") }
                    if (useGrid) {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((((subjectGroups.size + 1) / 2) * 220).dp)
                                    .padding(horizontal = horizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                userScrollEnabled = false,
                            ) {
                                items(subjectGroups, key = { it.subjectId }) { group ->
                                    SubjectGradeCard(
                                        group = group,
                                        horizontalPadding = 0.dp,
                                        onAddGrade = {
                                            preselectedSubjectId = group.subjectId
                                            showAddSheet = true
                                        },
                                        onDeleteEntry = viewModel::removeGrade,
                                    )
                                }
                            }
                        }
                    } else {
                        items(subjectGroups, key = { it.subjectId }) { group ->
                            SubjectGradeCard(
                                group = group,
                                horizontalPadding = horizontalPadding,
                                onAddGrade = {
                                    preselectedSubjectId = group.subjectId
                                    showAddSheet = true
                                },
                                onDeleteEntry = viewModel::removeGrade,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddGradeBottomSheet(
            subjects = state.subjects,
            preselectedSubjectId = preselectedSubjectId,
            onDismiss = { showAddSheet = false },
            onConfirm = { entry ->
                viewModel.addGrade(entry)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun SubjectGradeCard(
    group: SubjectGradeGroup,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onAddGrade: () -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val accentColor = parseHexColor(group.colorHex) ?: MaterialTheme.colorScheme.primary

    ModernCard(
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.entries.size} assessment${if (group.entries.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(percent = group.averagePercent)
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onAddGrade) {
                    Icon(Icons.Default.Add, contentDescription = "Add grade for ${group.subjectName}")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { (group.averagePercent / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = gradeColorForPercent(group.averagePercent),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )

        Spacer(Modifier.height(12.dp))

        val sortedEntries = group.entries.sortedByDescending { it.score / it.maxScore }
        sortedEntries.forEachIndexed { index, entry ->
            GradeEntryRow(
                entry = entry,
                onDelete = { onDeleteEntry(entry.id) }
            )
            if (index < sortedEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun GradeEntryRow(
    entry: GradeEntry,
    onDelete: () -> Unit
) {
    val percent = GradeCalculator.calculatePercentage(entry.score, entry.maxScore)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.assessment,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.category} · ${entry.term} · weight ${entry.weight}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${entry.score.toInt()}/${entry.maxScore.toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GradeBadge(percent = percent, compact = true)
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ${entry.assessment}",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GradeBadge(percent: Double, compact: Boolean = false) {
    val color = gradeColorForPercent(percent)
    Surface(
        shape = StudentAiShapes.chip,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "${"%.0f".format(percent)}%",
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp
            ),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGradeBottomSheet(
    subjects: List<Subject>,
    preselectedSubjectId: String?,
    onDismiss: () -> Unit,
    onConfirm: (GradeEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var assessment by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    var maxScore by remember { mutableStateOf("100") }
    var category by remember { mutableStateOf("General") }
    var weight by remember { mutableStateOf("1.0") }
    var term by remember { mutableStateOf("1st") }
    var selectedSubjectId by remember(preselectedSubjectId) {
        mutableStateOf(preselectedSubjectId ?: subjects.firstOrNull()?.id ?: "default")
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var termExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    val isValid = assessment.isNotBlank() &&
        score.toDoubleOrNull() != null &&
        (maxScore.toDoubleOrNull() ?: 0.0) > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Grade", style = MaterialTheme.typography.titleLarge)
            Text(
                "Log a new assessment score",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = assessment,
                onValueChange = { assessment = it },
                label = { Text("Assessment name") },
                placeholder = { Text("e.g. Midterm Exam, Quiz 3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (subjects.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it }
                ) {
                    OutlinedTextField(
                        value = subjects.find { it.id == selectedSubjectId }?.name ?: "Select subject",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.name) },
                                onClick = {
                                    selectedSubjectId = subject.id
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Score") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = maxScore,
                    onValueChange = { maxScore = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Max score") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        gradeCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = termExpanded,
                    onExpandedChange = { termExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = term,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Term") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = termExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = termExpanded,
                        onDismissRequest = { termExpanded = false }
                    ) {
                        gradeTerms.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    term = option
                                    termExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Category weight") },
                supportingText = { Text("Higher weight = more impact on overall grade") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    onConfirm(
                        GradeEntry(
                            id = UUID.randomUUID().toString(),
                            subjectId = selectedSubjectId,
                            assessment = assessment.trim(),
                            category = category,
                            score = score.toDoubleOrNull() ?: 0.0,
                            maxScore = maxScore.toDoubleOrNull() ?: 100.0,
                            weight = weight.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0,
                            term = term
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Save Grade")
            }
        }
    }
}

private fun buildSubjectGroups(
    entries: List<GradeEntry>,
    subjects: List<Subject>
): List<SubjectGradeGroup> {
    if (entries.isEmpty()) return emptyList()

    val subjectById = subjects.associateBy { it.id }
    return entries
        .groupBy { it.subjectId }
        .map { (subjectId, groupEntries) ->
            val subject = subjectById[subjectId]
            val average = groupEntries
                .map { GradeCalculator.calculatePercentage(it.score, it.maxScore) }
                .average()
            SubjectGradeGroup(
                subjectId = subjectId,
                subjectName = subject?.name ?: "General",
                colorHex = subject?.colorHex ?: "#3949AB",
                entries = groupEntries,
                averagePercent = average
            )
        }
        .sortedByDescending { it.averagePercent }
}

@Composable
private fun gradeColorForPercent(percent: Double): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        percent >= 90 -> Color(0xFF2E7D32)
        percent >= 75 -> scheme.primary
        percent >= 60 -> Color(0xFFF57C00)
        else -> scheme.error
    }
}
