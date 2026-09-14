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
import com.edukasyon.studentai.widget.render.WidgetRenderer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingTabRoute by mutableStateOf<String?>(null)
    private var pendingTriggerUpdate by mutableStateOf(false)
    private var pendingTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Remove the system's white contrast scrim under the transparent nav
        // bar (API 29+); the app paints its own background behind the inset.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        pendingTabRoute = extractStartTab(intent) ?: extractShareRoute(intent)
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
        (extractStartTab(intent) ?: extractShareRoute(intent))?.let { pendingTabRoute = it }
        if (extractTriggerUpdate(intent)) pendingTriggerUpdate = true
        extractTaskId(intent)?.let { pendingTaskId = it }
    }

    private fun extractStartTab(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        // ONLY the widget sets START_TAB_KEY, always with a MainTab route name.
        // A notification's reference id must never leak in here — it's a data
        // UUID, not a navigation route (navigating it crashes the NavController).
        return extras.getString(WidgetRenderer.START_TAB_KEY)
            ?: notificationStartTab(extras)
    }

    private fun extractTriggerUpdate(intent: Intent?): Boolean =
        intent?.getBooleanExtra(AppUpdateMessagingService.EXTRA_TRIGGER_UPDATE, false) == true

    /**
     * Share deep link (schedmate://share/<CODE>) → the redeem route with the
     * code prefilled. Malformed or invalid codes are ignored so a bad link
     * just opens the app normally.
     */
    private fun extractShareRoute(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.scheme != "schedmate" || data.host != "share") return null
        val code = (data.path?.trim('/') ?: data.lastPathSegment ?: "")
            .uppercase(java.util.Locale.US)
        if (!com.edukasyon.studentai.core.share.ShareCode.isValid(code)) return null
        return com.edukasyon.studentai.ui.navigation.Routes.redeemShare(code)
    }

    private fun extractTaskId(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        extras.getString(WidgetRenderer.TASK_ID_KEY)?.let { return it }
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
