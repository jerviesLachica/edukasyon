package com.edukasyon.studentai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.edukasyon.studentai.core.notifications.NotificationHelper
import com.edukasyon.studentai.core.notifications.ReminderType
import com.edukasyon.studentai.core.update.AppUpdateMessagingService
import com.edukasyon.studentai.core.update.UpdateManager
import com.edukasyon.studentai.ui.StudentAiAppContent
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingTabRoute by mutableStateOf<String?>(null)
    private var pendingTriggerUpdate by mutableStateOf(false)
    private var pendingTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTabRoute = extractStartTab(intent)
        pendingTriggerUpdate = extractTriggerUpdate(intent)
        pendingTaskId = extractTaskId(intent)
        setContent {
            val updateManager: UpdateManager = hiltViewModel()
            StudentAiAppContent(
                initialTabRoute = pendingTabRoute,
                onInitialTabConsumed = { pendingTabRoute = null },
                autoTriggerUpdate = pendingTriggerUpdate,
                onAutoTriggerConsumed = { pendingTriggerUpdate = false },
                updateManager = updateManager,
                initialTaskId = pendingTaskId,
                onInitialTaskConsumed = { pendingTaskId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractStartTab(intent)?.let { pendingTabRoute = it }
        if (extractTriggerUpdate(intent)) pendingTriggerUpdate = true
        extractTaskId(intent)?.let { pendingTaskId = it }
    }

    private fun extractStartTab(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        // ONLY the widget sets START_TAB_KEY, always with a MainTab route name.
        // A notification's reference id must never leak in here — it's a data
        // UUID, not a navigation route (navigating it crashes the NavController).
        return extras.getString(WidgetActions.START_TAB_KEY)
            ?: notificationStartTab(extras)
    }

    private fun extractTriggerUpdate(intent: Intent?): Boolean =
        intent?.getBooleanExtra(AppUpdateMessagingService.EXTRA_TRIGGER_UPDATE, false) == true

    private fun extractTaskId(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        extras.getString(WidgetActions.TASK_ID_KEY)?.let { return it }
        // Task reminders deep-link to the Planner with the task preselected,
        // mirroring the widget's tap-through flow.
        if (extras.getString(NotificationHelper.REFERENCE_TYPE_EXTRA) == ReminderType.TASK.name) {
            return extras.getString(NotificationHelper.REFERENCE_ID_EXTRA)
        }
        return null
    }

    /**
     * Maps a reminder notification tap to the tab that owns the reminder.
     * Assignments and exams live in the Planner; class reminders point at the
     * Schedule; task reminders are handled by extractTaskId instead. Unknown or
     * legacy ids (notifications scheduled before the type extra existed) land
     * on the Planner rather than crashing navigation with a UUID "route".
     */
    private fun notificationStartTab(extras: Bundle): String? {
        extras.getString(NotificationHelper.REFERENCE_ID_EXTRA) ?: return null
        return when (extras.getString(NotificationHelper.REFERENCE_TYPE_EXTRA)) {
            ReminderType.CLASS.name -> MainTab.SCHEDULE.route
            else -> MainTab.PLANNER.route
        }
    }
}
