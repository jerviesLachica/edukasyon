package com.edukasyon.studentai.widget.lifecycle

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import com.edukasyon.studentai.widget.WidgetConfig
import com.edukasyon.studentai.widget.WidgetDesignPreset
import com.edukasyon.studentai.widget.WidgetDisplayType
import com.edukasyon.studentai.widget.WidgetSize
import com.edukasyon.studentai.widget.store.WidgetConfigStore
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import kotlinx.coroutines.launch

/**
 * V2 configure activity. Save flow per spec: validate → persist config by
 * AppWidgetId → rebuild snapshot + render through [WidgetUpdateManager] →
 * RESULT_OK → finish. The widget is guaranteed rendered before we return.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existing = WidgetConfigStore.load(this, appWidgetId)
        setContent {
            StudentAiTheme(
                themeMode = ThemeMode.LIGHT,
                primaryColorHex = "#F97316",
                secondaryColorHex = "#F8B195"
            ) {
                V2ConfigScreen(
                    initialType = existing?.displayType ?: WidgetDisplayType.TASKS,
                    initialDesign = existing?.designPreset ?: WidgetDesignPreset.LINE_GRID,
                    onSave = { type, design -> save(type, design) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun save(type: WidgetDisplayType, design: WidgetDesignPreset) {
        val id = appWidgetId
        val tall = WidgetUpdateManager.isTall(this, id)
        // 1. Validate (enums from chips are always valid) + 2. persist by ID.
        val config = WidgetConfig(
            appWidgetId = id,
            widgetSize = if (tall) WidgetSize.TALL_2X3 else WidgetSize.SMALL_2X2,
            displayType = type,
            accentHex = null,
            designPreset = design,
            designColor1 = null,
            designColor2 = null,
            designColor3 = null
        )
        WidgetConfigStore.save(this, config)
        Log.i(TAG, "WIDGET_CONFIG_LOADED: saved id=$id $type/$design (CONFIGURATION_SAVED)")
        // 3-4. Rebuild + render through the manager BEFORE RESULT_OK.
        lifecycleScope.launch {
            try {
                WidgetUpdateManager.refresh(
                    this@WidgetConfigActivity, id,
                    WidgetUpdateManager.RefreshReason.CONFIGURATION_SAVED
                )
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_RENDER_COMPLETE: save-time refresh failed id=$id", e)
            } finally {
                // Rebind safeguard: some launchers bind AFTER RESULT_OK without
                // firing onUpdate, which would strand the widget on its loading
                // shell. A delayed one-shot refresh catches the late bind.
                runCatching {
                    com.edukasyon.studentai.worker.WidgetSyncWorker.scheduleOneShot(
                        this@WidgetConfigActivity, 12_000L,
                        WidgetUpdateManager.RefreshReason.INITIAL_CREATION
                    )
                }
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
                finish()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetV2"
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun V2ConfigScreen(
    initialType: WidgetDisplayType,
    initialDesign: WidgetDesignPreset,
    onSave: (WidgetDisplayType, WidgetDesignPreset) -> Unit,
    onCancel: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var design by remember { mutableStateOf(initialDesign) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Widget setup") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("What to show", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetDisplayType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Text("Design", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetDesignPreset.entries.forEach { option ->
                    FilterChip(
                        selected = design == option,
                        onClick = { design = option },
                        label = { Text(option.displayName) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!saving) {
                        saving = true
                        onSave(type, design)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            ) {
                Text(if (saving) "Adding…" else "Add widget")
            }
            androidx.compose.material3.TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
