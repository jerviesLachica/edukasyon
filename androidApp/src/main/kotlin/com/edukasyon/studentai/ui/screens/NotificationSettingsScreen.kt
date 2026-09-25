package com.edukasyon.studentai.ui.screens

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.notifications.ReviewReminderCoordinator
import com.edukasyon.studentai.core.notifications.ReviewReminderEntryPoint
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.ModernCard
import com.edukasyon.studentai.ui.components.SettingsGroup
import com.edukasyon.studentai.ui.components.SettingsRow
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.NotificationSettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

// Sentinel preview key for the null-URI "System Default" sound (null is
// reserved as the MediaPlayer stop state in the preview logic).
private const val SYSTEM_SOUND_PREVIEW_KEY = "system_default"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemState()
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    LaunchedEffect(Unit) { viewModel.refreshSystemState() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "NOTIFICATIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = StudentAiShapes.chip,
                        color = if (state.notificationsOn) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (state.notificationsOn) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                if (state.notificationsOn) "Notifications On" else "Notifications Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                ModernCard(onClick = onOpenDetail) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = StudentAiShapes.chip,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Notification Settings", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Class alerts, sound & timing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                PermissionInfoCard(
                    title = "Reminders muted by Do Not Disturb?",
                    body = "Grant Do Not Disturb access so class, task, and repeating reminders aren't silenced.",
                    buttonLabel = "Allow SchedMate to bypass Do Not Disturb",
                    granted = state.dndAccessGranted,
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                    }
                )
            }

            item {
                PermissionInfoCard(
                    title = "Alarm timing not matching?",
                    subtitle = "Reminders arriving later than expected? Battery optimization can delay them.",
                    body = "This is essential for accurate class alarms and for the home-screen widget to update on its own (e.g. rolling over to the next day at midnight) — without it, Android can delay or skip these in the background.",
                    buttonLabel = "Disable battery optimization for SchedMate",
                    granted = state.batteryOptimizationDisabled,
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= 33 && !state.notificationPermissionGranted) {
                item {
                    PermissionInfoCard(
                        title = "Notification permission required",
                        body = "Allow notifications so class and task reminders can reach you on time.",
                        buttonLabel = "Allow notifications",
                        granted = false,
                        onAction = {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsDetailScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val composeScope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Alert Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroup(title = "Master") {
                    SettingsRow(
                        title = "Enable notifications",
                        subtitle = "Master toggle for all reminders",
                        trailing = {
                            Switch(
                                checked = state.notificationsEnabled,
                                onCheckedChange = viewModel::setNotificationsEnabled
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Class alerts") {
                    SettingsRow(
                        title = "Class reminders",
                        trailing = {
                            Switch(
                                checked = state.classReminders,
                                onCheckedChange = viewModel::setClassReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "At class time",
                        subtitle = "Notify when class starts",
                        trailing = {
                            Switch(
                                checked = state.classReminderAtTime,
                                onCheckedChange = viewModel::setClassReminderAtTime,
                                enabled = state.classReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "15 minutes before",
                        subtitle = "Early heads-up before class",
                        trailing = {
                            Switch(
                                checked = state.classReminder15MinBefore,
                                onCheckedChange = viewModel::setClassReminder15MinBefore,
                                enabled = state.classReminders
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Other reminders") {
                    SettingsRow(
                        title = "Task reminders",
                        trailing = {
                            Switch(
                                checked = state.taskReminders,
                                onCheckedChange = viewModel::setTaskReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "Exam reminders",
                        trailing = {
                            Switch(
                                checked = state.examReminders,
                                onCheckedChange = viewModel::setExamReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "Study review reminders",
                        subtitle = "Daily nudge when cards are due",
                        trailing = {
                            val appContext = LocalContext.current.applicationContext
                            val reviewPreferences = remember {
                                EntryPointAccessors
                                    .fromApplication(appContext, ReviewReminderEntryPoint::class.java)
                                    .userPreferences()
                            }
                            val reviewEnabled by reviewPreferences.reviewReminderEnabled
                                .collectAsStateWithLifecycle(initialValue = false)
                            val reviewScope = rememberCoroutineScope()
                            Switch(
                                checked = reviewEnabled,
                                onCheckedChange = { enabled ->
                                    reviewScope.launch {
                                        reviewPreferences.setReviewReminderEnabled(enabled)
                                        if (enabled) ReviewReminderCoordinator.sync(appContext)
                                        else ReviewReminderCoordinator.cancel(appContext)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Sound") {
                    SettingsRow(
                        title = "Notification sound",
                        subtitle = "Play sound with reminders",
                        trailing = {
                            Switch(
                                checked = state.notificationSoundEnabled,
                                onCheckedChange = viewModel::setNotificationSoundEnabled
                            )
                        }
                    )
                    // Sound preview: play the candidate alarm through the
                    // alarm stream (same routing the real reminder uses) so
                    // the student hears it BEFORE committing it as their alarm.
                    val appCtx = LocalContext.current.applicationContext
                    var playingPreviewKey by remember { mutableStateOf<String?>(null) }
                    var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }
                    val alarmAudioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    fun stopPreview() {
                        runCatching { previewPlayer?.stop() }
                        runCatching { previewPlayer?.release() }
                        previewPlayer = null
                        runCatching { previewRingtone?.stop() }
                        previewRingtone = null
                        playingPreviewKey = null
                    }
                    fun previewSound(uri: String?) {
                        val key = uri ?: SYSTEM_SOUND_PREVIEW_KEY
                        val wasPlayingThis = playingPreviewKey == key
                        stopPreview()
                        if (wasPlayingThis) return
                        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        if (uri == null) {
                            // System default resolves to a Ringtone, not a file URI MediaPlayer can take.
                            runCatching {
                                val ringtone = defaultUri?.let { RingtoneManager.getRingtone(appCtx, it) }
                                ringtone?.audioAttributes = alarmAudioAttrs
                                ringtone?.play()
                                previewRingtone = ringtone
                                playingPreviewKey = key
                            }
                            return
                        }
                        runCatching {
                            val parsedUri = Uri.parse(uri)
                            previewPlayer = android.media.MediaPlayer().apply {
                                if (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource") {
                                    setDataSource(appCtx, parsedUri)
                                } else {
                                    setDataSource(uri)
                                }
                                setAudioAttributes(alarmAudioAttrs)
                                setOnCompletionListener { stopPreview() }
                                prepare()
                                start()
                                playingPreviewKey = key
                            }
                        }.onFailure { stopPreview() }
                    }
                    DisposableEffect(Unit) {
                        onDispose { stopPreview() }
                    }
                    // Alarm ringtones loop forever via the Ringtone API; clip any
                    // preview to a listenable length instead of leaving it droning.
                    LaunchedEffect(playingPreviewKey) {
                        if (playingPreviewKey != null) {
                            val startedKey = playingPreviewKey
                            kotlinx.coroutines.delay(8_000)
                            if (playingPreviewKey == startedKey) stopPreview()
                        }
                    }

                    val soundPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let { pickedUri ->
                            composeScope.launch(Dispatchers.IO) {
                                // Attempt persistable permission grant safely without throwing if unsupported
                                runCatching {
                                    context.contentResolver.takePersistableUriPermission(
                                        pickedUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }
                                val name = runCatching {
                                    context.contentResolver.query(pickedUri, null, null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                            if (nameIndex >= 0) cursor.getString(nameIndex) else null
                                        } else null
                                    }
                                }.getOrNull()?.takeIf { it.isNotBlank() }
                                    ?: pickedUri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                                    ?: "Custom Audio"

                                // Copy audio file under the transient grant into app-private storage so it survives reboots and file moves
                                val finalUriString = runCatching {
                                    val dir = File(context.filesDir, "custom_audio").apply { mkdirs() }
                                    dir.listFiles()?.forEach { it.delete() }
                                    val ext = name.substringAfterLast('.', "mp3").take(5)
                                    val dest = File(dir, "custom_alarm_sound.$ext")
                                    context.contentResolver.openInputStream(pickedUri)?.use { input ->
                                        FileOutputStream(dest).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (dest.exists() && dest.length() > 0) {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            dest
                                        )
                                        runCatching {
                                            context.grantUriPermission("android", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.grantUriPermission("com.android.systemui", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        contentUri.toString()
                                    } else {
                                        pickedUri.toString()
                                    }
                                }.getOrElse {
                                    pickedUri.toString()
                                }

                                withContext(Dispatchers.Main) {
                                    viewModel.selectAlarmSound(name, finalUriString)
                                    // Hear what you just picked before it becomes your alarm.
                                    previewSound(finalUriString)
                                }
                            }
                        }
                    }

                    val presetSounds = listOf(
                        "System Default" to null,
                        "Gentle Chime (Tone 1)" to "android.resource://${LocalContext.current.packageName}/raw/tone_1",
                        "Crystal Bell (Tone 2)" to "android.resource://${LocalContext.current.packageName}/raw/tone_2",
                        "Digital Pulse (Tone 3)" to "android.resource://${LocalContext.current.packageName}/raw/tone_3",
                        "Morning Glow (Tone 4)" to "android.resource://${LocalContext.current.packageName}/raw/tone_4",
                        "Soft Breeze (Tone 5)" to "android.resource://${LocalContext.current.packageName}/raw/tone_5"
                    )
                    val presetUris = presetSounds.mapNotNull { it.second }.toSet()
                    val isCustomSelected = state.alarmSoundUri != null && !presetUris.contains(state.alarmSoundUri)

                    presetSounds.forEach { (name, uri) ->
                        val previewKey = uri ?: SYSTEM_SOUND_PREVIEW_KEY
                        val isSelected = if (uri == null) {
                            state.alarmSoundUri == null || state.alarmSoundName == "System Default"
                        } else {
                            state.alarmSoundUri == uri
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectAlarmSound(name, uri)
                                    previewSound(uri)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectAlarmSound(name, uri)
                                    previewSound(uri)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { previewSound(uri) }) {
                                Icon(
                                    imageVector = if (playingPreviewKey == previewKey) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (playingPreviewKey == previewKey) "Stop preview" else "Preview $name",
                                    tint = if (playingPreviewKey == previewKey) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (isCustomSelected) {
                        val customUri = state.alarmSoundUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { previewSound(customUri) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = { previewSound(customUri) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.alarmSoundName.ifBlank { "Custom Audio" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Custom Audio file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { previewSound(customUri) }) {
                                Icon(
                                    imageVector = if (playingPreviewKey == customUri) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (playingPreviewKey == customUri) "Stop preview" else "Preview custom audio",
                                    tint = if (playingPreviewKey == customUri) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    soundPickerLauncher.launch("audio/*")
                                }.onFailure {
                                    runCatching { soundPickerLauncher.launch("*/*") }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isCustomSelected) "Change Custom Audio (MP3 / WAV)" else "Pick Custom Audio (MP3 / WAV)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Select any audio file from your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionInfoCard(
    title: String,
    body: String,
    buttonLabel: String,
    granted: Boolean,
    onAction: () -> Unit,
    subtitle: String? = null
) {
    ModernCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!granted) {
                    Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
                        Text(buttonLabel)
                    }
                } else {
                    Text(
                        "Already configured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
