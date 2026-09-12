package com.edukasyon.studentai.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.edukasyon.studentai.R

enum class WidgetPinResult {
    PIN_DIALOG_REQUESTED,
    MANUAL_INSTRUCTIONS_NEEDED,
}

object WidgetPinHelper {

    fun requestPinWidget(context: Context, size: WidgetSize): WidgetPinResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(AppWidgetManager::class.java)
            if (manager != null && manager.isRequestPinAppWidgetSupported) {
                val component = componentNameFor(context, size)
                if (manager.requestPinAppWidget(component, null, null)) {
                    return WidgetPinResult.PIN_DIALOG_REQUESTED
                }
            }
        }
        return WidgetPinResult.MANUAL_INSTRUCTIONS_NEEDED
    }

    fun openCustomize(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, com.edukasyon.studentai.widget.lifecycle.ScheduleWidgetProvider::class.java)
        )

        when {
            ids.isNotEmpty() -> launchConfigure(
                context,
                com.edukasyon.studentai.widget.lifecycle.WidgetConfigActivity::class.java,
                ids.first()
            )
            else -> when (requestPinWidget(context, WidgetSize.SMALL_2X2)) {
                WidgetPinResult.PIN_DIALOG_REQUESTED -> Unit
                WidgetPinResult.MANUAL_INSTRUCTIONS_NEEDED -> showManualInstructionsToast(context)
            }
        }
    }

    fun openHomeScreenSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Try next fallback intent.
            }
        }
        showManualInstructionsToast(context)
    }

    private fun launchConfigure(context: Context, activityClass: Class<*>, appWidgetId: Int) {
        context.startActivity(
            Intent(context, activityClass).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun componentNameFor(context: Context, size: WidgetSize): ComponentName =
        ComponentName(context, com.edukasyon.studentai.widget.lifecycle.ScheduleWidgetProvider::class.java)

    fun showManualInstructionsToast(context: Context) {
        Toast.makeText(
            context,
            context.getString(R.string.widget_manual_hint),
            Toast.LENGTH_LONG
        ).show()
    }
}
