package com.edukasyon.studentai.widget.store

import android.content.Context
import com.edukasyon.studentai.widget.WidgetSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * V2 per-instance snapshot store. Holds the last fully-built, display-ready
 * [WidgetSnapshot] per AppWidgetId, versioned with its generation timestamp.
 *
 * Envelope format on disk: `v1|<generatedAt>|<json>`.
 * Disk is truth; memory is a read-through cache only.
 */
object WidgetSnapshotStore {
    private const val PREFS_NAME = "schedmate_widget_v2_snapshot"
    private const val VERSION = 1

    /** Disk write + memory publish of a freshly built snapshot. */
    data class StoredSnapshot(
        val version: Int,
        val generatedAt: Long,
        val snapshot: WidgetSnapshot
    )

    private val memory = ConcurrentHashMap<Int, StoredSnapshot>()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Synchronous commit — the stored state survives process death immediately. */
    fun save(context: Context, appWidgetId: Int, snapshot: WidgetSnapshot) {
        saveStored(context, appWidgetId, StoredSnapshot(VERSION, System.currentTimeMillis(), snapshot))
    }

    fun saveStored(context: Context, appWidgetId: Int, stored: StoredSnapshot) {
        memory[appWidgetId] = stored
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(appWidgetId), encode(stored))
            .commit()
    }

    fun load(context: Context, appWidgetId: Int): StoredSnapshot? {
        memory[appWidgetId]?.let { return it }
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(appWidgetId), null) ?: return null
        return decode(raw)?.also { memory[appWidgetId] = it }
    }

    fun remove(context: Context, appWidgetId: Int) {
        memory.remove(appWidgetId)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(appWidgetId))
            .apply()
    }

    private fun key(appWidgetId: Int) = "snapshot_$appWidgetId"

    private fun encode(stored: StoredSnapshot): String =
        "${stored.version}|${stored.generatedAt}|" +
            json.encodeToString(WidgetSnapshot.serializer(), stored.snapshot)

    private fun decode(raw: String): StoredSnapshot? {
        return try {
            val firstBar = raw.indexOf('|')
            val secondBar = raw.indexOf('|', firstBar + 1)
            if (firstBar < 0 || secondBar < 0) return null
            StoredSnapshot(
                version = raw.substring(0, firstBar).toInt(),
                generatedAt = raw.substring(firstBar + 1, secondBar).toLong(),
                snapshot = json.decodeFromString(
                    WidgetSnapshot.serializer(),
                    raw.substring(secondBar + 1)
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}
