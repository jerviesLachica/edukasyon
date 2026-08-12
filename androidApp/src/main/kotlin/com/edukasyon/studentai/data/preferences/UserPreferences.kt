package com.edukasyon.studentai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edukasyon.studentai.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("studentai_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CLASS_REMINDERS = booleanPreferencesKey("class_reminders")
        val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        val EXAM_REMINDERS = booleanPreferencesKey("exam_reminders")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val AI_CONTEXT_ENABLED = booleanPreferencesKey("ai_context_enabled")
        val USE_MOCK_AI = booleanPreferencesKey("use_mock_ai")
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        ThemeMode.entries.find { it.name.equals(raw, ignoreCase = true) } ?: ThemeMode.SYSTEM
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val useMockAi: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_MOCK_AI] ?: false }

    /** One-time migration: existing installs may have mock AI enabled; force remote as default. */
    suspend fun ensureRemoteAiEnabled() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.USE_MOCK_AI] != false) {
                prefs[Keys.USE_MOCK_AI] = false
            }
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setUseMockAi(useMock: Boolean) {
        context.dataStore.edit { it[Keys.USE_MOCK_AI] = useMock }
    }
}
