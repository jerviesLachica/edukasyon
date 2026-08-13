package com.edukasyon.studentai.core.gamification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edukasyon.studentai.domain.model.GizmoCompanionState
import com.edukasyon.studentai.domain.model.GizmoConstants
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
        val HEARTS = intPreferencesKey("hearts")
        val SUPER_HEARTS = intPreferencesKey("super_hearts")
        val XP = intPreferencesKey("xp")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
        val HEARTS_BLOCKED_UNTIL = longPreferencesKey("hearts_blocked_until")
        val LAST_HEART_REFILL = longPreferencesKey("last_heart_refill")
        val MEMORISE_MODE = intPreferencesKey("memorise_mode")
    }

    val state: Flow<GizmoCompanionState> = context.gizmoStore.data.map { prefs ->
        val hearts = prefs[Keys.HEARTS] ?: GizmoConstants.MAX_HEARTS
        val xp = prefs[Keys.XP] ?: 0
        val level = computeLevel(xp)
        val streak = prefs[Keys.STREAK_DAYS] ?: 0
        val blockedUntil = prefs[Keys.HEARTS_BLOCKED_UNTIL]?.takeIf { it > 0 }
        val memoriseMode = (prefs[Keys.MEMORISE_MODE] ?: 1) == 1

        GizmoCompanionState(
            hearts = hearts,
            superHearts = prefs[Keys.SUPER_HEARTS] ?: GizmoConstants.MAX_SUPER_HEARTS,
            xp = xp,
            level = level,
            streakDays = streak,
            mood = computeMood(hearts, streak, level),
            heartsBlockedUntil = blockedUntil,
            memoriseMode = memoriseMode,
        )
    }

    suspend fun refreshHearts(): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            val now = System.currentTimeMillis()
            var hearts = prefs[Keys.HEARTS] ?: GizmoConstants.MAX_HEARTS
            val blockedUntil = prefs[Keys.HEARTS_BLOCKED_UNTIL] ?: 0L
            val lastRefill = prefs[Keys.LAST_HEART_REFILL] ?: now

            if (hearts < GizmoConstants.MAX_HEARTS) {
                val elapsed = now - lastRefill
                val heartsToAdd = (elapsed / GizmoConstants.HEART_REFILL_INTERVAL_MS).toInt()
                if (heartsToAdd > 0) {
                    hearts = (hearts + heartsToAdd).coerceAtMost(GizmoConstants.MAX_HEARTS)
                    prefs[Keys.LAST_HEART_REFILL] = now
                }
            }

            if (hearts > 0 && blockedUntil > 0 && now >= blockedUntil) {
                prefs.remove(Keys.HEARTS_BLOCKED_UNTIL)
            }

            prefs[Keys.HEARTS] = hearts
            result = buildState(prefs)
        }
        return result
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

    suspend fun loseHeart(): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            var hearts = prefs[Keys.HEARTS] ?: GizmoConstants.MAX_HEARTS
            var superHearts = prefs[Keys.SUPER_HEARTS] ?: GizmoConstants.MAX_SUPER_HEARTS

            if (superHearts > 0) {
                superHearts -= 1
                prefs[Keys.SUPER_HEARTS] = superHearts
            } else {
                hearts = (hearts - 1).coerceAtLeast(0)
                prefs[Keys.HEARTS] = hearts
                if (hearts == 0) {
                    prefs[Keys.HEARTS_BLOCKED_UNTIL] = System.currentTimeMillis() + GizmoConstants.HEARTS_COOLDOWN_MS
                }
            }

            result = buildState(prefs)
        }
        return result
    }

    suspend fun activateSuperHeart(): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            val superHearts = prefs[Keys.SUPER_HEARTS] ?: 0
            if (superHearts > 0) {
                prefs[Keys.SUPER_HEARTS] = superHearts - 1
            }
            result = buildState(prefs)
        }
        return result
    }

    suspend fun setMemoriseMode(enabled: Boolean): GizmoCompanionState {
        var result = GizmoCompanionState()
        context.gizmoStore.edit { prefs ->
            prefs[Keys.MEMORISE_MODE] = if (enabled) 1 else 0
            result = buildState(prefs)
        }
        return result
    }

    private fun buildState(prefs: Preferences): GizmoCompanionState {
        val hearts = prefs[Keys.HEARTS] ?: GizmoConstants.MAX_HEARTS
        val xp = prefs[Keys.XP] ?: 0
        val level = computeLevel(xp)
        val streak = prefs[Keys.STREAK_DAYS] ?: 0
        return GizmoCompanionState(
            hearts = hearts,
            superHearts = prefs[Keys.SUPER_HEARTS] ?: GizmoConstants.MAX_SUPER_HEARTS,
            xp = xp,
            level = level,
            streakDays = streak,
            mood = computeMood(hearts, streak, level),
            heartsBlockedUntil = prefs[Keys.HEARTS_BLOCKED_UNTIL]?.takeIf { it > 0 },
            memoriseMode = (prefs[Keys.MEMORISE_MODE] ?: 1) == 1,
        )
    }

    private fun computeLevel(xp: Int): Int = (xp / 100) + 1

    private fun computeMood(hearts: Int, streak: Int, level: Int): GizmoMood = when {
        hearts == 0 -> GizmoMood.RESTING
        streak >= 7 -> GizmoMood.EXCITED
        streak >= 3 -> GizmoMood.PROUD
        level >= 5 -> GizmoMood.CHEERFUL
        hearts <= 3 -> GizmoMood.ENCOURAGING
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
