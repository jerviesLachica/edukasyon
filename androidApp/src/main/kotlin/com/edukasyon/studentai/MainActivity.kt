package com.edukasyon.studentai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edukasyon.studentai.core.notifications.NotificationHelper
import com.edukasyon.studentai.ui.StudentAiAppContent
import com.edukasyon.studentai.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingTabRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTabRoute = extractStartTab(intent)
        setContent {
            StudentAiAppContent(
                initialTabRoute = pendingTabRoute,
                onInitialTabConsumed = { pendingTabRoute = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractStartTab(intent)?.let { pendingTabRoute = it }
    }

    private fun extractStartTab(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        return extras.getString(WidgetActions.START_TAB_KEY)
            ?: extras.getString(NotificationHelper.REFERENCE_ID_EXTRA)
    }
}
