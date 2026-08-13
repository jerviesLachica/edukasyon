package com.edukasyon.studentai.core.gamification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edukasyon.studentai.domain.model.GizmoCompanionState
import com.edukasyon.studentai.domain.model.GizmoMood
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gizmoStore: DataStore<Preferences> by preferencesDataStore("gizmo_companion")

@Singleton
class GizmoGamificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val XP = intPreferencesKey("xp")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
    }

    val state: Flow<GizmoCompanionState> = context.gizmoStore.data.map { prefs ->
        buildState(prefs)
    }

    suspend fun recordActivity(): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            val today = todayString()
            val lastDate = prefs[Keys.LAST_ACTIVE_DATE]
            var streak = prefs[Keys.STREAK_DAYS] ?: 0

            if (lastDate != today) {
                streak = when {
                    lastDate == null -> 1
                    isYesterday(lastDate) -> streak + 1
                    else -> 1
                }
                prefs[Keys.STREAK_DAYS] = streak
                prefs[Keys.LAST_ACTIVE_DATE] = today
            }

            result = buildState(prefs)
        }
        return result
    }

    suspend fun addXp(amount: Int): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            val current = prefs[Keys.XP] ?: 0
            prefs[Keys.XP] = current + amount
            result = buildState(prefs)
        }
        return result
    }

    private fun buildState(prefs: Preferences): GizmoCompanionState {
        val xp = prefs[Keys.XP] ?: 0
        val level = computeLevel(xp)
        val streak = prefs[Keys.STREAK_DAYS] ?: 0
        return GizmoCompanionState(
            xp = xp,
            level = level,
            streakDays = streak,
            mood = computeMood(streak, level),
        )
    }

    private fun computeLevel(xp: Int): Int = (xp / 100) + 1

    private fun computeMood(streak: Int, level: Int): GizmoMood = when {
        streak >= 7 -> GizmoMood.EXCITED
        streak >= 3 -> GizmoMood.PROUD
        level >= 5 -> GizmoMood.CHEERFUL
        else -> GizmoMood.HAPPY
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun isYesterday(dateStr: String): Boolean {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        return dateStr == yesterday
    }
}
