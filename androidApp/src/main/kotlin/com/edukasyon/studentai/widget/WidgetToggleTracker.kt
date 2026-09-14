package com.edukasyon.studentai.widget

import android.content.Context

/**
 * Remembers tasks the user toggled from the widget (either direction) so the
 * snapshot pins them into the visible window. Without this, an unchecked task
 * could sort outside the top-N (priority + due date) and appear to never come
 * back, even though the refresh fired correctly.
 *
 * Persisted in SharedPreferences so it survives process death; entries expire
 * after [WINDOW_MS].
 */
object WidgetToggleTracker {
    private const val PREFS_NAME = "studentai_widget_toggle_tracker"
    private const val WINDOW_MS = 15 * 60_000L

    fun markToggled(context: Context, taskId: String) {
        val prefs = prefs(context)
        // commit() (sync) instead of apply() (async): Huawei can wipe the
        // in-memory SharedPreferences map between the toggle write and the
        // snapshot rebuild if the process is trimmed. Sync write closes that window.
        prefs.edit().putLong(key(taskId), System.currentTimeMillis()).commit()
        prune(prefs)
    }

    /** IDs toggled within the window, most-recent first. */
    fun recentIds(context: Context): List<String> {
        val prefs = prefs(context)
        prune(prefs)
        val now = System.currentTimeMillis()
        return prefs.all
            .filterKeys { it.startsWith("toggle_") }
            .mapNotNull { (k, v) ->
                val ts = (v as? Long) ?: return@mapNotNull null
                if (now - ts <= WINDOW_MS) k.removePrefix("toggle_") to ts else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun prune(prefs: android.content.SharedPreferences) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        var changed = false
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("toggle_")) {
                val ts = (v as? Long) ?: 0L
                if (now - ts > WINDOW_MS) {
                    editor.remove(k)
                    changed = true
                }
            }
        }
        if (changed) editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(taskId: String) = "toggle_$taskId"
}
