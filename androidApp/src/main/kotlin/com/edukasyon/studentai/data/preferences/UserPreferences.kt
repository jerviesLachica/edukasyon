package com.edukasyon.studentai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

    /** Persisted first-launch choice for how the app authenticates the user. */
    enum class AuthStrategy(val key: String) {
        UNSELECTED(""),
        GUEST("guest"),
        GOOGLE("google");

        companion object {
            fun fromKey(raw: String?): AuthStrategy =
                entries.firstOrNull { raw != null && it.key == raw } ?: UNSELECTED
        }
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
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val AI_CONTEXT_ENABLED = booleanPreferencesKey("ai_context_enabled")
        val USE_MOCK_AI = booleanPreferencesKey("use_mock_ai")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val STEP_MODEL_USAGE_TIMESTAMPS = stringPreferencesKey("step_model_usage_timestamps")
        val SCHEDULE_DAY_TEMPLATES = stringPreferencesKey("schedule_day_templates")
        val FIREBASE_AUTH_EMAIL = stringPreferencesKey("firebase_auth_email")
        val GOOGLE_ACCOUNT_LINKED = booleanPreferencesKey("google_account_linked")
        val AUTH_STRATEGY = stringPreferencesKey("auth_strategy")
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
    val lastSyncedAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_SYNCED_AT] }
    val firebaseAuthEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.FIREBASE_AUTH_EMAIL]?.takeIf { it.isNotBlank() }
    }
    val googleAccountLinked: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.GOOGLE_ACCOUNT_LINKED] ?: false
    }
    val authStrategy: Flow<AuthStrategy> = context.dataStore.data.map { prefs ->
        AuthStrategy.fromKey(prefs[Keys.AUTH_STRATEGY])
    }
    val useMockAi: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_MOCK_AI] ?: false }
    val aiModel: Flow<AiModel> = context.dataStore.data.map { prefs ->
        AiModel.fromSlug(prefs[Keys.AI_MODEL] ?: AiModel.AUTO.slug)
    }

    val stepModelUsageTimestamps: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        decodeStepModelTimestamps(prefs[Keys.STEP_MODEL_USAGE_TIMESTAMPS])
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

    suspend fun setLastSyncedAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNCED_AT] = timestamp }
    }

    suspend fun setFirebaseAuthEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.FIREBASE_AUTH_EMAIL)
            } else {
                prefs[Keys.FIREBASE_AUTH_EMAIL] = email.trim()
            }
        }
    }

    suspend fun setGoogleAccountLinked(linked: Boolean) {
        context.dataStore.edit { it[Keys.GOOGLE_ACCOUNT_LINKED] = linked }
    }

    suspend fun clearFirebaseAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.FIREBASE_AUTH_EMAIL)
            prefs[Keys.GOOGLE_ACCOUNT_LINKED] = false
        }
    }

    suspend fun setAuthStrategy(strategy: AuthStrategy) {
        context.dataStore.edit { it[Keys.AUTH_STRATEGY] = strategy.key }
    }

    suspend fun setUseMockAi(useMock: Boolean) {
        context.dataStore.edit { it[Keys.USE_MOCK_AI] = useMock }
    }

    suspend fun setAiModel(model: AiModel) {
        context.dataStore.edit { it[Keys.AI_MODEL] = model.slug }
    }

    suspend fun setStepModelUsageTimestamps(timestamps: List<Long>) {
        context.dataStore.edit {
            it[Keys.STEP_MODEL_USAGE_TIMESTAMPS] = encodeStepModelTimestamps(timestamps)
        }
    }

    private fun decodeStepModelTimestamps(raw: String?): List<Long> =
        raw?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.sorted()
            ?: emptyList()

    private fun encodeStepModelTimestamps(timestamps: List<Long>): String =
        timestamps.joinToString(",")

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
