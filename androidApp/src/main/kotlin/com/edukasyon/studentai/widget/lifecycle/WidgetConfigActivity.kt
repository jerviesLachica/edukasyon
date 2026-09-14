package com.edukasyon.studentai.widget.lifecycle

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import com.edukasyon.studentai.ui.theme.parseHexColor
import com.edukasyon.studentai.widget.WidgetBackgroundGenerator
import com.edukasyon.studentai.widget.WidgetConfig
import com.edukasyon.studentai.widget.WidgetDesignPreset
import com.edukasyon.studentai.widget.WidgetDisplayType
import com.edukasyon.studentai.widget.WidgetSize
import com.edukasyon.studentai.widget.store.WidgetConfigStore
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import java.io.File
import java.io.FileOutputStream
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
                    initialPhotoPath = existing?.backgroundImagePath,
                    onSave = { type, design, photoPath -> save(type, design, photoPath) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun save(type: WidgetDisplayType, design: WidgetDesignPreset, photoPath: String?) {
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
            designColor3 = null,
            backgroundImagePath = photoPath
        )
        WidgetConfigStore.save(this, config)
        Log.i(TAG, "WIDGET_CONFIG_LOADED: saved id=$id $type/$design bgPath=${photoPath?.take(30)}... (CONFIGURATION_SAVED)")
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
    initialPhotoPath: String?,
    onSave: (WidgetDisplayType, WidgetDesignPreset, String?) -> Unit,
    onCancel: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var design by remember { mutableStateOf(initialDesign) }
    var photoPath by remember { mutableStateOf(initialPhotoPath) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = context.copyAndScalePhoto(uri)
            if (savedPath != null) {
                photoPath = savedPath
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Widget setup") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live preview card — reflects current type + design + photo selections.
            WidgetPreviewCard(
                displayType = type,
                designPreset = design,
                photoPath = photoPath
            )
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
            Text("Background photo", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = { photoPicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            ) {
                Text(if (photoPath != null) "Photo selected — change" else "Pick photo")
            }
            if (photoPath != null) {
                androidx.compose.material3.TextButton(
                    onClick = { photoPath = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use design instead")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!saving) {
                        saving = true
                        onSave(type, design, photoPath)
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

// ===== Preview + photo helpers =====

/**
 * Compose-only preview of how the widget will look with the currently
 * selected preset / photo. It is intentionally approximate: it shows sample
 * text over the design background, since the real widget is RemoteViews
 * owned by the launcher and cannot be rendered inside the configuration
 * activity.
 */
@Composable
private fun WidgetPreviewCard(
    displayType: WidgetDisplayType,
    designPreset: WidgetDesignPreset,
    photoPath: String?
) {
    val context = LocalContext.current
    val colors = designPreset.defaultColors()
    val isLight = designPreset == WidgetDesignPreset.MINIMAL
    val onSurface = if (isLight) 0xFF1A1A1A else 0xFFF5F5F5
    val muted = if (isLight) 0xFF6B7280 else 0xFF9CA3AF
    val bgColor = parseHexColor(colors.color1)
        ?: Color(0xFF1F2A44)

    // Same generated background the real 2x2 widget renders, capped square.
    val backgroundBitmap = remember(designPreset, colors, context) {
        WidgetBackgroundGenerator.getBitmap(context, designPreset, colors)
    }

    Box(
        modifier = Modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 320.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
    ) {
        // Design background layer — the same generated bitmap the real
        // widget renders (no asImagePainter: it doesn't exist in Compose
        // 1.7.0; android.graphics.Bitmap goes through asImageBitmap).
        Image(
            painter = BitmapPainter(backgroundBitmap.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Photo layer. Coil 2.7 cannot decode a schemeless Uri, so feed the
        // painter a File directly — photoPath is an absolute file path.
        if (!photoPath.isNullOrBlank()) {
            val painter = rememberAsyncImagePainter(
                model = File(photoPath),
                error = null
            )
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Content overlay.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Mon",
                        color = Color(onSurface),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "September",
                        color = Color(muted),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = "15",
                    color = Color(onSurface),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            val sampleItems = previewItems(displayType)
            sampleItems.forEach { item ->
                Text(
                    text = item,
                    color = Color(onSurface),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun previewItems(displayType: WidgetDisplayType): List<String> {
    return when (displayType) {
        WidgetDisplayType.TASKS -> listOf(
            "○ Math assignment",
            "● Read chapter 4",
            "○ Submit lab report"
        )
        WidgetDisplayType.SCHEDULE -> listOf(
            "08:00 AM • Calculus",
            "10:30 AM • Biology",
            "01:00 PM • History"
        )
        WidgetDisplayType.COMBINED -> listOf(
            "○ Math assignment",
            "10:30 AM • Biology"
        )
    }
}

/**
 * Copies a content URI (from the photo picker) into app-private storage,
 * downscales it to a Binder-safe size, and returns the absolute file path.
 * Returns null if the URI cannot be read or processed safely.
 */
private fun Context.copyAndScalePhoto(uri: Uri, maxPx: Int = 512): String? {
    return try {
        // No persistable-permission call here: the picker used GetContent(),
        // whose transient grant rejects persistence claims. We copy the bytes
        // immediately under that transient grant instead.
        val dir = File(filesDir, "widget-bg").apply { mkdirs() }
        val outFile = File(dir, "widget_bg_${System.currentTimeMillis()}.png")
        val bitmap = decodeCappedPhoto(uri, maxPx) ?: return null
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        if (bitmap.isRecycled.not()) bitmap.recycle()
        // Orphan cleanup: the previous copies are superseded by outFile, so
        // drop older widget_bg_*.png before handing the new path back.
        dir.listFiles { f ->
            f.isFile && f.name.startsWith("widget_bg_") && f.name != outFile.name
        }?.forEach { it.delete() }
        outFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun Context.decodeCappedPhoto(uri: Uri, maxPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) > maxPx) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
        }
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // Scale so the largest dimension is exactly maxPx.
        val ratio = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
        if (ratio < 1f) {
            val targetW = (decoded.width * ratio).toInt()
            val targetH = (decoded.height * ratio).toInt()
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    } catch (_: Exception) {
        null
    }
}