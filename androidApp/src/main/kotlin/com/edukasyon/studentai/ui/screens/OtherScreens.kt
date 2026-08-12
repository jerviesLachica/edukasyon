package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Note
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.CalendarViewModel
import com.edukasyon.studentai.ui.viewmodel.GradesViewModel
import com.edukasyon.studentai.ui.viewmodel.NotesViewModel
import com.edukasyon.studentai.ui.viewmodel.ProfileViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateChat: () -> Unit = {},
    onNavigateFeaturesGuide: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp
    val context = androidx.compose.ui.platform.LocalContext.current
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJson(it) } }

    val exportScheduleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportScheduleCsv(it) } }

    val exportGradesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportGradesCsv(it) } }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotifications(true)
    }

    LaunchedEffect(state.notificationsEnabled) {
        if (state.notificationsEnabled && android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    state.backupMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearBackupMessage()
        }
    }

    if (showImportConfirm != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("Import backup") },
            text = { Text("Merge imported data with existing records?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm?.let { viewModel.importJson(it, replace = false) }
                    showImportConfirm = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showImportConfirm?.let { viewModel.importJson(it, replace = true) }
                        showImportConfirm = null
                    }) { Text("Replace") }
                    TextButton(onClick = { showImportConfirm = null }) { Text("Cancel") }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GradientHeader(
                title = state.user?.displayName ?: "Guest Student",
                subtitle = state.user?.school?.takeIf { it.isNotBlank() } ?: "StudentAI profile",
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }

        item {
            SettingsGroup(title = "Help") {
                SettingsRow(
                    title = "Features Guide",
                    subtitle = "Explore all app features and where to find them",
                    trailing = {
                        TextButton(onClick = onNavigateFeaturesGuide) {
                            Text("Open")
                        }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingIcon = if (state.themeMode == mode) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ThemeColorPicker(
                    primaryColorHex = state.primaryColorHex,
                    secondaryColorHex = state.secondaryColorHex,
                    themeMode = state.themeMode,
                    onPrimaryColorSelected = viewModel::setPrimaryColor,
                    onSecondaryColorSelected = viewModel::setSecondaryColor,
                    onResetColors = viewModel::resetThemeColors
                )
            }
        }

        item {
            SettingsGroup(title = "Notifications") {
                SettingsRow(
                    title = "Enable notifications",
                    subtitle = "Master toggle for all reminders",
                    trailing = {
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.setNotifications(it)
                                if (it && android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                )
                SettingsRow(
                    title = "Class reminders",
                    trailing = { Switch(checked = state.classReminders, onCheckedChange = viewModel::setClassReminders) }
                )
                SettingsRow(
                    title = "Task reminders",
                    trailing = { Switch(checked = state.taskReminders, onCheckedChange = viewModel::setTaskReminders) }
                )
                SettingsRow(
                    title = "Exam reminders",
                    trailing = { Switch(checked = state.examReminders, onCheckedChange = viewModel::setExamReminders) }
                )
            }
        }

        item {
            SettingsGroup(title = "Home Screen Widget") {
                WidgetSetupCard(modifier = Modifier.padding(0.dp))
            }
        }

        item {
            SettingsGroup(title = "Study Groups") {
                SettingsRow(
                    title = "Open chats",
                    subtitle = "Collaborate with classmates",
                    trailing = {
                        TextButton(onClick = onNavigateChat) { Text("Open") }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Data") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportJsonLauncher.launch("studentai_backup.json") }) { Text("Export JSON") }
                    OutlinedButton(onClick = { exportScheduleLauncher.launch("schedule.csv") }) { Text("Schedule CSV") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportGradesLauncher.launch("grades.csv") }) { Text("Grades CSV") }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import JSON") }
                }
            }
        }

        item {
            SettingsGroup(title = "AI Settings") {
                SettingsRow(
                    title = "Connection status",
                    subtitle = if (state.isOnline) "Connected to AI backend" else "Offline — local AI fallback active",
                    trailing = {
                        Text(
                            text = if (state.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isOnline) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Privacy") {
                Text(
                    text = "Data is stored locally on your device. AI features send only the content you select to the backend. No API keys are stored in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Notes") })
        OutlinedTextField(state.searchQuery, { viewModel.search(it) }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search notes...") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        if (state.isLoading) LoadingState()
        else if (state.notes.isEmpty()) EmptyState("No notes", "Create your first note.", actionLabel = "Add Note", onAction = { showAdd = true })
        else LazyColumn {
            items(state.notes) { note ->
                StudentAiCard {
                    Text(note.title, style = MaterialTheme.typography.titleSmall)
                    Text(note.content.take(100), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    Row { IconButton(onClick = { viewModel.deleteNote(note.id) }) { Icon(Icons.Default.Delete, null) } }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }
    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("New Note") },
            text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Title") }); OutlinedTextField(content, { content = it }, label = { Text("Content") }, minLines = 4) } },
            confirmButton = { TextButton(onClick = { val now = System.currentTimeMillis(); viewModel.saveNote(Note(UUID.randomUUID().toString(), title, content, null, emptyList(), now, now, false, false)); showAdd = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(viewModel: GradesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Grades") })
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Weighted Grade", style = MaterialTheme.typography.titleSmall)
                Text("%.1f%%".format(state.weightedGrade), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (state.entries.isEmpty()) EmptyState("No grades", "Add your first grade entry.", actionLabel = "Add Grade", onAction = { showAdd = true })
        else LazyColumn {
            items(state.entries) { entry ->
                StudentAiCard {
                    Text(entry.assessment, style = MaterialTheme.typography.titleSmall)
                    Text("${entry.score}/${entry.maxScore} (${entry.category}, weight ${entry.weight})")
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.padding(16.dp)) { Icon(Icons.Default.Add, null) }
    }
    if (showAdd) {
        var assessment by remember { mutableStateOf("") }
        var score by remember { mutableStateOf("") }
        var maxScore by remember { mutableStateOf("100") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Add Grade") },
            text = { Column { OutlinedTextField(assessment, { assessment = it }, label = { Text("Assessment") }); OutlinedTextField(score, { score = it }, label = { Text("Score") }); OutlinedTextField(maxScore, { maxScore = it }, label = { Text("Max Score") }) } },
            confirmButton = { TextButton(onClick = { viewModel.addGrade(GradeEntry(UUID.randomUUID().toString(), "default", assessment, "General", score.toDoubleOrNull() ?: 0.0, maxScore.toDoubleOrNull() ?: 100.0, 1.0, "1st")); showAdd = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val holidaysLoading by viewModel.holidaysLoading.collectAsStateWithLifecycle()
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.refreshHolidays()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendar", style = MaterialTheme.typography.headlineSmall) }
        if (holidaysLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading holidays…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (holidays.isNotEmpty()) {
            item { Text("Philippine Holidays", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(holidays) { holiday ->
                StudentAiCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(holiday.name, style = MaterialTheme.typography.titleSmall)
                            Text(dateFormat.format(java.util.Date(holiday.dateMillis)))
                        }
                        Surface(
                            color = if (holiday.type.name == "REGULAR") MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                holiday.type.label,
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        item { Text("Your Events", style = MaterialTheme.typography.titleSmall) }
        if (events.isEmpty()) {
            item { EmptyState("No events", "Add tasks, exams, or assignments to see them on your calendar.") }
        } else {
            items(events) { event ->
                StudentAiCard {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text("${event.type} • ${dateFormat.format(java.util.Date(event.startAt))}")
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val adaptiveWidth = rememberAdaptiveWidth()

    Column(
        Modifier
            .fillMaxSize()
            .background(StudentAiGradients.subtleSurfaceBrush())
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        OnboardingIllustration(step = state.step)

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = if (adaptiveWidth == AdaptiveWidth.Expanded) {
                Modifier.width(560.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally { it / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally { -it / 4 })
                },
                label = "onboardingStep"
            ) { step ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        0 -> {
                            Text(
                                "Welcome to StudentAI",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Your intelligent student companion for schedules, tasks, notes, and AI-powered study tools.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = viewModel::nextStep,
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = StudentAiShapes.button
                            ) {
                                Text("Get Started")
                            }
                            OutlinedButton(
                                onClick = { viewModel.completeGuestOnboarding(onFinished = onComplete) },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = StudentAiShapes.button
                            ) {
                                Text("Continue Offline as Guest")
                            }
                        }

                        1 -> {
                            Text("School Setup", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Tell us about your school so we can personalize your experience.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                state.school,
                                { viewModel.updateSchool(it) },
                                label = { Text("School Name") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving,
                                shape = StudentAiShapes.chip
                            )
                            OutlinedTextField(
                                state.gradeLevel,
                                { viewModel.updateGradeLevel(it) },
                                label = { Text("Grade Level") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving,
                                shape = StudentAiShapes.chip
                            )
                            OutlinedTextField(
                                state.section,
                                { viewModel.updateSection(it) },
                                label = { Text("Section") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving,
                                shape = StudentAiShapes.chip
                            )
                            Button(
                                onClick = viewModel::nextStep,
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = StudentAiShapes.button
                            ) {
                                Text("Continue")
                            }
                        }

                        else -> {
                            Text("You're all set!", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Start by adding your schedule or scanning it with AI.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.completeGuestOnboarding(onFinished = onComplete) },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                shape = StudentAiShapes.button
                            ) {
                                Icon(Icons.Default.RocketLaunch, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.isSaving) "Saving…" else "Enter StudentAI")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (state.step + 1) / 3f },
            modifier = Modifier
                .then(if (adaptiveWidth == AdaptiveWidth.Expanded) Modifier.width(560.dp) else Modifier.fillMaxWidth())
                .clip(StudentAiShapes.chip)
        )
    }
}

@Composable
private fun OnboardingIllustration(step: Int) {
    val icon = when (step) {
        0 -> Icons.Default.School
        1 -> Icons.Default.Edit
        else -> Icons.Default.CheckCircle
    }
    Surface(
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
