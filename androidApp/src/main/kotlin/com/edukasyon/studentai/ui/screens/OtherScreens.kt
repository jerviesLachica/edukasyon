package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.Note
import com.edukasyon.studentai.domain.model.AiModel
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.R
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.CalendarViewModel
import com.edukasyon.studentai.ui.viewmodel.NotesViewModel
import com.edukasyon.studentai.ui.viewmodel.ProfileViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateChat: () -> Unit = {},
    onNavigateFeaturesGuide: () -> Unit = {},
    onNavigateNotificationSettings: () -> Unit = {},
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
                    title = "Notification settings",
                    subtitle = "Class alerts, DND bypass, battery optimization",
                    trailing = {
                        TextButton(onClick = onNavigateNotificationSettings) {
                            Text("Open")
                        }
                    }
                )
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
                Spacer(Modifier.height(12.dp))
                Text(
                    "Vision model (image chats)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Text-only chat uses the auto model. Standard/Pro applies when you attach photos or images (e.g. homework, schedule screenshots).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiModel.entries.forEach { model ->
                        FilterChip(
                            selected = state.aiModel == model,
                            onClick = { viewModel.setAiModel(model) },
                            label = {
                                Text("${model.displayName} (${model.slug})")
                            },
                            leadingIcon = if (state.aiModel == model) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
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

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.refreshPermissionState() }

    LaunchedEffect(state.step) {
        viewModel.refreshPermissionState()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(StudentAiGradients.subtleSurfaceBrush())
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.step > 0) {
                IconButton(onClick = viewModel::previousStep) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(StudentAiShapes.chip)
            )
        }

        Spacer(Modifier.height(24.dp))
        OnboardingIllustration(step = state.step)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = if (adaptiveWidth == AdaptiveWidth.Expanded) Modifier.width(560.dp) else Modifier.fillMaxWidth()
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
                        0 -> OnboardingWelcomeStep(state, viewModel)
                        1 -> OnboardingSchoolStep(state, viewModel)
                        2 -> OnboardingAppearanceStep(state, viewModel)
                        3 -> OnboardingPermissionsStep(
                            state = state,
                            onRequestNotifications = {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onContinue = viewModel::nextStep
                        )
                        4 -> OnboardingNotifyStep(state, viewModel)
                        5 -> OnboardingWidgetsStep(
                            onExplore = {
                                viewModel.markWidgetsExplored()
                                viewModel.nextStep()
                            },
                            onSkip = viewModel::skipWidgets
                        )
                        else -> OnboardingFinishStep(state, onComplete, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Welcome to StudentAI", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(
        "Let's get to know you so we can personalize your study companion.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.displayName,
        onValueChange = viewModel::updateDisplayName,
        label = { Text("Your name") },
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.chip,
        enabled = !state.isSaving
    )
    Button(
        onClick = viewModel::nextStep,
        enabled = state.displayName.isNotBlank() && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.button
    ) { Text("Continue") }
}

@Composable
private fun OnboardingSchoolStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("School details", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Tell us about your school so we can personalize your experience.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(state.school, viewModel::updateSchool, label = { Text("School") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    OutlinedTextField(state.gradeLevel, viewModel::updateGradeLevel, label = { Text("Grade level") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    OutlinedTextField(state.section, viewModel::updateSection, label = { Text("Section") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingAppearanceStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Appearance", style = MaterialTheme.typography.headlineMedium)
    Text("Choose a theme — you can change this anytime in Profile.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingPermissionsStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    Text("Stay on track", style = MaterialTheme.typography.headlineMedium)
    Text(
        "StudentAI uses notifications for class reminders, tasks, and exams. Grant permission so alerts arrive on time.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (android.os.Build.VERSION.SDK_INT >= 33 && !state.notificationPermissionGranted) {
        Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
            Text("Allow notifications")
        }
    } else {
        Text("Notifications enabled ✓", color = MaterialTheme.colorScheme.secondary)
    }
    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
        Text("Continue")
    }
}

@Composable
private fun OnboardingNotifyStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Notify me", style = MaterialTheme.typography.headlineMedium)
    Text("When should we remind you about classes?", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsRow(
        title = "Class reminders",
        trailing = { Switch(checked = state.classReminders, onCheckedChange = viewModel::setClassReminders) }
    )
    SettingsRow(
        title = "At class time",
        trailing = { Switch(checked = state.classReminderAtTime, onCheckedChange = viewModel::setClassReminderAtTime, enabled = state.classReminders) }
    )
    SettingsRow(
        title = "15 minutes before",
        trailing = { Switch(checked = state.classReminder15MinBefore, onCheckedChange = viewModel::setClassReminder15MinBefore, enabled = state.classReminders) }
    )
    SettingsRow(
        title = "Task reminders",
        trailing = { Switch(checked = state.taskReminders, onCheckedChange = viewModel::setTaskReminders) }
    )
    SettingsRow(
        title = "Exam reminders",
        trailing = { Switch(checked = state.examReminders, onCheckedChange = viewModel::setExamReminders) }
    )
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingWidgetsStep(onExplore: () -> Unit, onSkip: () -> Unit) {
    Text("Home screen widgets", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Pin a widget to see today's schedule or upcoming tasks without opening the app.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    WidgetSetupCard(modifier = Modifier.fillMaxWidth())
    Button(onClick = onExplore, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Got it") }
    TextButton(onClick = onSkip) { Text("Skip for now") }
}

@Composable
private fun OnboardingFinishStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    onComplete: () -> Unit,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("🎓", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Surface(shape = StudentAiShapes.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                "You're all set!",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    Text(
        "Here's what we set up, ${state.displayName.ifBlank { "Student" }}.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OnboardingSummaryRow("Appearance", state.appearanceLabel)
        OnboardingSummaryRow("Permissions", "${state.permissionsGrantedCount}/3 granted")
        OnboardingSummaryRow("Notify me", state.notifyMeSummary)
        OnboardingSummaryRow("Widgets", state.widgetsLabel)
        OnboardingSummaryRow("Profile", state.displayName.ifBlank { "Not set" })
    }
    Button(
        onClick = { viewModel.completeOnboarding(onFinished = onComplete) },
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.button
    ) {
        Text(if (state.isSaving) "Saving…" else "Finish")
    }
}

@Composable
private fun OnboardingSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingIllustration(step: Int) {
    if (step == 0) {
        Image(
            painter = painterResource(R.drawable.wala),
            contentDescription = "Edukasyon logo",
            modifier = Modifier.size(120.dp),
        )
        return
    }
    val icon = when (step) {
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
