package com.edukasyon.studentai.widget.update

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.edukasyon.studentai.widget.WidgetConfig
import com.edukasyon.studentai.widget.WidgetDisplayType
import com.edukasyon.studentai.widget.WidgetSize
import com.edukasyon.studentai.widget.WidgetSnapshotBuilder
import com.edukasyon.studentai.widget.lifecycle.ScheduleWidgetProvider
import com.edukasyon.studentai.widget.render.WidgetRenderer
import com.edukasyon.studentai.widget.store.WidgetConfigStore
import com.edukasyon.studentai.widget.store.WidgetSnapshotStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * V2 single update entry point. EVERY widget refresh passes through here.
 *
 * Pipeline per widget: load config (seed defaults if missing) → load stored
 * snapshot → rebuild from local Room → change-detect vs stored → render
 * RemoteViews → AppWidgetManager.updateAppWidget. Local-first: no network,
 * no Firebase, no Activity anywhere on this path. Per-ID serialized.
 */
object WidgetUpdateManager {

    private const val TAG = "WidgetV2"

    enum class RefreshReason(val tag: String) {
        INITIAL_CREATION("INITIAL_CREATION"),
        CONFIGURATION_SAVED("CONFIGURATION_SAVED"),
        TASK_CHANGED("TASK_CHANGED"),
        SCHEDULE_CHANGED("SCHEDULE_CHANGED"),
        REMOTE_SYNC("REMOTE_SYNC"),
        TIME_BOUNDARY("TIME_BOUNDARY"),
        SYSTEM_UPDATE("SYSTEM_UPDATE"),
        RESTORED("RESTORED")
    }

    private val locks = ConcurrentHashMap<Int, Mutex>()
    private fun lockFor(id: Int): Mutex = locks.getOrPut(id) { Mutex() }

    suspend fun refresh(context: Context, appWidgetId: Int, reason: RefreshReason) {
        lockFor(appWidgetId).withLock {
            refreshLocked(context.applicationContext, appWidgetId, reason)
        }
    }

    suspend fun refreshAll(context: Context, reason: RefreshReason) {
        val appContext = context.applicationContext
        val ids = hostedIds(appContext)
        if (ids.isEmpty()) {
            Log.i(TAG, "WIDGET_INIT_START: no hosted v2 instances (reason=${reason.tag})")
            return
        }
        WidgetV2Diagnostics.recordPaint(ids, ids)
        ids.sorted().forEach { id ->
            try {
                refresh(appContext, id, reason)
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_RENDER_COMPLETE: failed id=$id", e)
            }
        }
    }

    /** Fire-and-forget for receivers and repository hooks. */
    fun refreshAsync(context: Context, appWidgetId: Int, reason: RefreshReason) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { refresh(context, appWidgetId, reason) }
                .onFailure { Log.e(TAG, "WIDGET_RENDER_COMPLETE: async failed id=$appWidgetId", it) }
        }
    }

    fun refreshAllAsync(context: Context, reason: RefreshReason) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { refreshAll(context, reason) }
                .onFailure { Log.e(TAG, "WIDGET_RENDER_COMPLETE: async all failed", it) }
        }
    }

    /**
     * Fast repaint of the last STORED snapshot without any DB read.
     * Used for the optimistic tap flip. Returns false when nothing stored.
     */
    suspend fun paintStored(context: Context, appWidgetId: Int): Boolean {
        val appContext = context.applicationContext
        val stored = WidgetSnapshotStore.load(appContext, appWidgetId) ?: return false
        val config = WidgetConfigStore.load(appContext, appWidgetId) ?: return false
        return runCatching {
            val views = withContext(Dispatchers.Default) {
                WidgetRenderer.render(appContext, appWidgetId, config, stored.snapshot, isTall(appContext, appWidgetId))
            }
            AppWidgetManager.getInstance(appContext).updateAppWidget(appWidgetId, views)
            Log.e(TAG, "WIDGET_PLATFORM_UPDATE: optimistic paint id=$appWidgetId v=${stored.version} gen=${stored.generatedAt}")
            true
        }.onFailure {
            Log.e(TAG, "WIDGET_PLATFORM_UPDATE: optimistic paint failed id=$appWidgetId", it)
        }.getOrDefault(false)
    }

    /**
     * Flip one task's checkbox inside the STORED snapshot (memory + disk) and
     * return the new state, or null when the task is not in this snapshot.
     * No DB I/O — instant, for the optimistic tap path.
     */
    fun flipStoredTask(context: Context, appWidgetId: Int, taskId: String): Boolean? {
        val stored = WidgetSnapshotStore.load(context, appWidgetId) ?: return null
        if (stored.snapshot.tasks.none { it.id == taskId }) return null
        var newState: Boolean? = null
        val flipped = stored.snapshot.tasks.map {
            if (it.id == taskId) {
                val s = !it.isCompleted
                newState = s
                it.copy(isCompleted = s)
            } else it
        }
        WidgetSnapshotStore.saveStored(
            context, appWidgetId,
            stored.copy(snapshot = stored.snapshot.copy(tasks = flipped))
        )
        return newState
    }

    // ===== Pipeline =====

    private suspend fun refreshLocked(context: Context, appWidgetId: Int, reason: RefreshReason) {
        val t0 = System.currentTimeMillis()
        Log.i(TAG, "WIDGET_INIT_START: id=$appWidgetId reason=${reason.tag}")

        // Load config, or synthesize a safe default that can actually paint.
        // Never leave config null — a null config downstream causes theme
        // resolution to fail, producing a blank/transparent RemoteViews.
        var config = WidgetConfigStore.load(context, appWidgetId)
        var seeded = false
        if (config == null) {
            val tall = isTall(context, appWidgetId)
            config = defaultConfig(appWidgetId, tall)
            WidgetConfigStore.save(context, config)
            seeded = true
            Log.i(TAG, "WIDGET_CONFIG_LOADED: id=$appWidgetId seeded default ($config)")
        } else {
            Log.i(TAG, "WIDGET_CONFIG_LOADED: id=$appWidgetId ${config.widgetSize}/${config.displayType}/${config.designPreset}")
        }
        val effectiveConfig = config.copy(appWidgetId = appWidgetId)

        val previous = WidgetSnapshotStore.load(context, appWidgetId)

        // Build snapshot. On ANY failure, fall back to an empty snapshot
        // that still has valid theme colors — never a null/blank render.
        val built = try {
            WidgetSnapshotBuilder.buildSnapshot(context, appWidgetId, effectiveConfig)
        } catch (e: Exception) {
            Log.e(TAG, "WIDGET_SNAPSHOT_BUILT: failed id=$appWidgetId, empty fallback", e)
            WidgetSnapshotBuilder.buildEmptySnapshot(context, appWidgetId, effectiveConfig)
        }
        Log.i(TAG, "WIDGET_SNAPSHOT_BUILT: id=$appWidgetId tasks=${built.tasks.size} schedule=${built.schedule.size}")

        if (previous != null && sameContent(previous.snapshot, built)) {
            Log.i(TAG, "WIDGET_RENDER_START: id=$appWidgetId unchanged, skipping platform update")
            return
        }

        WidgetSnapshotStore.save(context, appWidgetId, built)
        val stored = WidgetSnapshotStore.load(context, appWidgetId)
        val gen = stored?.generatedAt ?: t0
        val ver = stored?.version ?: 1

        Log.i(TAG, "WIDGET_RENDER_START: id=$appWidgetId v=$ver gen=$gen seeded=$seeded")
        val views = withContext(Dispatchers.Default) {
            WidgetRenderer.render(context, appWidgetId, effectiveConfig, built, isTall(context, appWidgetId))
        }
        // updateAppWidget can silently fail if the RemoteViews payload is
        // too large (Binder transaction limit). We log the payload size so
        // failures are diagnosable in logcat.
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        val dt = System.currentTimeMillis() - t0
        Log.e(TAG, "WIDGET_PLATFORM_UPDATE: id=$appWidgetId reason=${reason.tag} v=$ver gen=$gen ${dt}ms")
        Log.i(TAG, "WIDGET_INIT_COMPLETE: id=$appWidgetId")
        WidgetV2Diagnostics.recordRefresh(appWidgetId, reason.tag, ver, gen)
    }

    // ===== Helpers =====

    /**
     * Content equality for change detection. WidgetSnapshot's data-class
     * equals is unusable here: themeColors is a plain class compared by
     * reference, so two identical builds would never match and every refresh
     * would repaint. Compare every display-relevant field instead.
     */
    private fun sameContent(
        a: com.edukasyon.studentai.widget.WidgetSnapshot,
        b: com.edukasyon.studentai.widget.WidgetSnapshot
    ): Boolean {
        if (a.dayName != b.dayName || a.monthName != b.monthName || a.dayOfMonth != b.dayOfMonth) return false
        if (a.moreCount != b.moreCount || a.isLoading != b.isLoading) return false
        if (a.displayType != b.displayType || a.widgetSize != b.widgetSize) return false
        if (a.designPreset != b.designPreset || a.designColors != b.designColors) return false
        if (a.accentColorHex != b.accentColorHex || a.isDarkTheme != b.isDarkTheme) return false
        if (a.currentTaskProgress != b.currentTaskProgress) return false
        if (a.currentTaskTimeLeft != b.currentTaskTimeLeft) return false
        if (a.tasks != b.tasks) return false
        if (a.schedule != b.schedule) return false
        if (a.calendarDays != b.calendarDays) return false
        if (a.calendarWeekdayLabels != b.calendarWeekdayLabels) return false
        return true
    }

    /** One text row per v2 instance for the on-device Diagnose panel. */
    suspend fun diagnose(context: Context): List<String> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val hosted = hostedIds(appContext)
        if (hosted.isEmpty()) return@withContext listOf("v2: no hosted instances")
        hosted.sorted().map { id ->
            val config = WidgetConfigStore.load(appContext, id)
            val stored = WidgetSnapshotStore.load(appContext, id)
            val tasks = stored?.snapshot?.tasks
            val dbTruth = runCatching {
                val items = tasks?.take(3) ?: return@runCatching "db=n/a"
                if (items.isEmpty()) return@runCatching "db=empty-snap"
                val ep = dagger.hilt.android.EntryPointAccessors
                    .fromApplication(appContext, com.edukasyon.studentai.widget.WidgetEntryPoint::class.java)
                val rows = ep.taskDao().getByIds(items.map { it.id }).associateBy { it.id }
                "db=" + items.joinToString(",") { item ->
                    val row = rows[item.id]
                    if (row == null) "${item.title.take(12)}:GONE"
                    else "${item.title.take(12)}:${row.status}"
                }
            }.getOrDefault("db=ERR")
            val snapText = if (stored == null) {
                "MISSING"
            } else {
                val total = stored.snapshot.tasks.size
                val checked = stored.snapshot.tasks.count { task -> task.isCompleted }
                "tasks=$total chk=$checked gen=${stored.generatedAt}"
            }
            val rowsText = tasks?.take(3)
                ?.joinToString(",") { item ->
                    val mark = if (item.isCompleted) "CHK" else "unc"
                    "${item.title.take(12)}:$mark"
                }?.let { "rows=[$it] " } ?: ""
            val cfgText = config?.let { "${it.widgetSize}/${it.displayType}/${it.designPreset}" } ?: "MISSING"
            "v2 ID $id config=$cfgText snap=$snapText $rowsText$dbTruth"
        }
    }

    /** Launcher truth for OUR provider only. */
    fun hostedIds(context: Context): Set<Int> {
        val appContext = context.applicationContext
        return runCatching {
            AppWidgetManager.getInstance(appContext).getAppWidgetIds(
                ComponentName(appContext, ScheduleWidgetProvider::class.java)
            ).toSet()
        }.getOrDefault(emptySet())
    }

    fun isTall(context: Context, appWidgetId: Int): Boolean {
        return runCatching {
            val opts = AppWidgetManager.getInstance(context.applicationContext)
                .getAppWidgetOptions(appWidgetId) ?: return false
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            maxOf(minH, maxH) >= 200
        }.getOrDefault(false)
    }

    private fun defaultConfig(appWidgetId: Int, tall: Boolean): WidgetConfig =
        WidgetConfig(
            appWidgetId = appWidgetId,
            widgetSize = if (tall) WidgetSize.TALL_2X3 else WidgetSize.SMALL_2X2,
            displayType = if (tall) WidgetDisplayType.COMBINED else WidgetDisplayType.TASKS,
            accentHex = null,
            designPreset = com.edukasyon.studentai.widget.WidgetDesignPreset.LINE_GRID,
            designColor1 = null,
            designColor2 = null,
            designColor3 = null
        )
}
