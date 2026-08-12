package com.edukasyon.studentai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.edukasyon.studentai.ui.StudentAiAppContent
import com.edukasyon.studentai.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudentAiAppContent(initialTabRoute = extractStartTab(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setContent {
            StudentAiAppContent(initialTabRoute = extractStartTab(intent))
        }
    }

    private fun extractStartTab(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        return extras.getString("start_tab")
            ?: extras.getString(WidgetActions.StartTabKey.name)
    }
}
