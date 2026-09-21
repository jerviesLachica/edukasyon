package com.edukasyon.studentai.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderDismissManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("schedmate_dismissed_reminders", Context.MODE_PRIVATE)

    fun markDismissed(type: ReminderType, referenceId: String) {
        val key = "${type.name}_$referenceId"
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun isDismissed(type: ReminderType, referenceId: String): Boolean {
        val key = "${type.name}_$referenceId"
        return prefs.contains(key)
    }

    fun markFired(type: ReminderType, referenceId: String) {
        val key = "fired_${type.name}_$referenceId"
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun isFired(type: ReminderType, referenceId: String): Boolean {
        val key = "fired_${type.name}_$referenceId"
        return prefs.contains(key)
    }

    fun isDismissedOrFired(type: ReminderType, referenceId: String): Boolean {
        return isDismissed(type, referenceId) || isFired(type, referenceId)
    }

    fun resetReminder(type: ReminderType, referenceId: String) {
        val keyDismissed = "${type.name}_$referenceId"
        val keyFired = "fired_${type.name}_$referenceId"
        prefs.edit().remove(keyDismissed).remove(keyFired).apply()
    }
}
