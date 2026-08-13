package com.edukasyon.studentai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edukasyon.studentai.domain.model.AiModel
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleDayTemplate
import com.edukasyon.studentai.domain.model.ScheduleWeekTemplates
import com.edukasyon.studentai.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("studentai_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_PRIMARY_COLOR = "#3949AB"
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_COLOR_HEX = stringPreferencesKey("primary_color_hex")
        val SECONDARY_COLOR_HEX = stringPreferencesKey("secondary_color_hex")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CLASS_REMINDERS = booleanPreferencesKey("class_reminders")
        val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        val EXAM_REMINDERS = booleanPreferencesKey("exam_reminders")
        val CLASS_REMINDER_AT_TIME = booleanPreferencesKey("class_reminder_at_time")
        val CLASS_REMINDER_15_MIN = booleanPreferencesKey("class_reminder_15_min")
        val NOTIFICATION_SOUND_ENABLED = booleanPreferencesKey("notification_sound_enabled")
        val ONBOARDING_WIDGETS_EXPLORED = booleanPreferencesKey("onboarding_widgets_explored")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val AI_CONTEXT_ENABLED = booleanPreferencesKey("ai_context_enabled")
        val USE_MOCK_AI = booleanPreferencesKey("use_mock_ai")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val SCHEDULE_DAY_TEMPLATES = stringPreferencesKey("schedule_day_templates")
    }

    private val templateJson = Json { ignoreUnknownKeys = true }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        ThemeMode.entries.find { it.name.equals(raw, ignoreCase = true) } ?: ThemeMode.SYSTEM
    }
    val primaryColorHex: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRIMARY_COLOR_HEX]?.takeIf { it.isNotBlank() } ?: DEFAULT_PRIMARY_COLOR
    }
    val secondaryColorHex: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SECONDARY_COLOR_HEX]?.takeIf { it.isNotBlank() }
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val classReminders: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLASS_REMINDERS] ?: true }
    val taskReminders: Flow<Boolean> = context.dataStore.data.map { it[Keys.TASK_REMINDERS] ?: true }
    val examReminders: Flow<Boolean> = context.dataStore.data.map { it[Keys.EXAM_REMINDERS] ?: true }
    val classReminderAtTime: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLASS_REMINDER_AT_TIME] ?: true }
    val classReminder15MinBefore: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLASS_REMINDER_15_MIN] ?: true }
    val notificationSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATION_SOUND_ENABLED] ?: true }
    val onboardingWidgetsExplored: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_WIDGETS_EXPLORED] ?: false }
    val useMockAi: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_MOCK_AI] ?: false }
    val aiModel: Flow<AiModel> = context.dataStore.data.map { prefs ->
        AiModel.fromSlug(prefs[Keys.AI_MODEL] ?: AiModel.STANDARD.slug)
    }

    val scheduleDayTemplates: Flow<ScheduleWeekTemplates> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SCHEDULE_DAY_TEMPLATES]
        if (raw.isNullOrBlank()) {
            ScheduleWeekTemplates.defaults()
        } else {
            runCatching { templateJson.decodeFromString<ScheduleWeekTemplates>(raw) }
                .getOrElse { ScheduleWeekTemplates.defaults() }
        }
    }

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

    suspend fun setPrimaryColorHex(hex: String) {
        context.dataStore.edit { it[Keys.PRIMARY_COLOR_HEX] = hex.trim() }
    }

    suspend fun setSecondaryColorHex(hex: String?) {
        context.dataStore.edit { prefs ->
            if (hex.isNullOrBlank()) {
                prefs.remove(Keys.SECONDARY_COLOR_HEX)
            } else {
                prefs[Keys.SECONDARY_COLOR_HEX] = hex.trim()
            }
        }
    }

    suspend fun resetThemeColors() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PRIMARY_COLOR_HEX)
            prefs.remove(Keys.SECONDARY_COLOR_HEX)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setClassReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLASS_REMINDERS] = enabled }
    }

    suspend fun setTaskReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TASK_REMINDERS] = enabled }
    }

    suspend fun setExamReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXAM_REMINDERS] = enabled }
    }

    suspend fun setClassReminderAtTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLASS_REMINDER_AT_TIME] = enabled }
    }

    suspend fun setClassReminder15MinBefore(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLASS_REMINDER_15_MIN] = enabled }
    }

    suspend fun setNotificationSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_SOUND_ENABLED] = enabled }
    }

    suspend fun setOnboardingWidgetsExplored(explored: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_WIDGETS_EXPLORED] = explored }
    }

    suspend fun setUseMockAi(useMock: Boolean) {
        context.dataStore.edit { it[Keys.USE_MOCK_AI] = useMock }
    }

    suspend fun setAiModel(model: AiModel) {
        context.dataStore.edit { it[Keys.AI_MODEL] = model.slug }
    }

    suspend fun setScheduleDayTemplates(templates: ScheduleWeekTemplates) {
        context.dataStore.edit {
            it[Keys.SCHEDULE_DAY_TEMPLATES] = templateJson.encodeToString(templates)
        }
    }

    suspend fun setScheduleDayTemplate(day: DayOfWeek, template: ScheduleDayTemplate) {
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.SCHEDULE_DAY_TEMPLATES]?.let { raw ->
                runCatching { templateJson.decodeFromString<ScheduleWeekTemplates>(raw) }.getOrNull()
            } ?: ScheduleWeekTemplates.defaults()
            val updated = existing.withTemplate(day, template)
            prefs[Keys.SCHEDULE_DAY_TEMPLATES] = templateJson.encodeToString(updated)
        }
    }

    suspend fun resetScheduleDayTemplates() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SCHEDULE_DAY_TEMPLATES)
        }
    }
}
