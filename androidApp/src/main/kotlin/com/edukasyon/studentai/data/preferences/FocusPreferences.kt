package com.edukasyon.studentai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.edukasyon.studentai.domain.model.FocusMode
import com.edukasyon.studentai.domain.model.FocusSessionRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore("focus_prefs")

@Singleton
class FocusPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SESSION_HISTORY = stringPreferencesKey("session_history")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val sessionHistory: Flow<List<FocusSessionRecord>> = context.focusDataStore.data.map { prefs ->
        val raw = prefs[Keys.SESSION_HISTORY] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<FocusSessionRecord>>(raw) }
            .getOrElse { emptyList() }
            .sortedByDescending { it.completedAt }
            .take(MAX_HISTORY)
    }

    suspend fun appendSession(record: FocusSessionRecord) {
        context.focusDataStore.edit { prefs ->
            val existing = prefs[Keys.SESSION_HISTORY]?.let { raw ->
                runCatching { json.decodeFromString<List<FocusSessionRecord>>(raw) }.getOrElse { emptyList() }
            } ?: emptyList()
            val updated = (listOf(record) + existing).take(MAX_HISTORY)
            prefs[Keys.SESSION_HISTORY] = json.encodeToString(updated)
        }
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}
