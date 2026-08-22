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
import com.edukasyon.studentai.core.update.AppUpdateMessagingService
import com.edukasyon.studentai.core.update.UpdateManager
import com.edukasyon.studentai.ui.StudentAiAppContent
import com.edukasyon.studentai.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingTabRoute by mutableStateOf<String?>(null)
    private var pendingTriggerUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTabRoute = extractStartTab(intent)
        pendingTriggerUpdate = extractTriggerUpdate(intent)
        setContent {
            val updateManager: UpdateManager = hiltViewModel()
            StudentAiAppContent(
                initialTabRoute = pendingTabRoute,
                onInitialTabConsumed = { pendingTabRoute = null },
                autoTriggerUpdate = pendingTriggerUpdate,
                onAutoTriggerConsumed = { pendingTriggerUpdate = false },
                updateManager = updateManager,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractStartTab(intent)?.let { pendingTabRoute = it }
        if (extractTriggerUpdate(intent)) pendingTriggerUpdate = true
    }

    private fun extractStartTab(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        return extras.getString(WidgetActions.START_TAB_KEY)
            ?: extras.getString(NotificationHelper.REFERENCE_ID_EXTRA)
    }

    private fun extractTriggerUpdate(intent: Intent?): Boolean =
        intent?.getBooleanExtra(AppUpdateMessagingService.EXTRA_TRIGGER_UPDATE, false) == true
}
