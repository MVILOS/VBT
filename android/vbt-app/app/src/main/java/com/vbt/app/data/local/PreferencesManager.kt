package com.vbt.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vbt_preferences")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val USER_ID = intPreferencesKey("user_id")
        private val USERNAME = stringPreferencesKey("username")
        private val USER_ROLE = stringPreferencesKey("user_role")
        private val COACH_ID = intPreferencesKey("coach_id")
        // Zbiór kluczy metryk pokazywanych na nakładce nagrywania (patrz OverlayMetric)
        private val OVERLAY_METRICS = stringSetPreferencesKey("overlay_metrics")
        // Jakość nagrywania wideo (patrz RecordingQuality)
        private val RECORDING_QUALITY = stringPreferencesKey("recording_quality")
    }

    private val dataStore = context.dataStore

    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[AUTH_TOKEN] = token
        }
    }

    suspend fun saveUser(id: Int, username: String, role: String, coachId: Int? = null) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = id
            preferences[USERNAME] = username
            preferences[USER_ROLE] = role
            if (coachId != null) {
                preferences[COACH_ID] = coachId
            }
        }
    }

    fun getToken(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[AUTH_TOKEN]
    }

    fun getRole(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_ROLE]
    }

    fun getUserId(): Flow<Int?> = dataStore.data.map { preferences ->
        preferences[USER_ID]
    }

    fun getUsername(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[USERNAME]
    }

    fun isLoggedIn(): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTH_TOKEN] != null
    }

    // Wybór metryk nakładki nagrywania. null = nigdy nie ustawiono (konsument
    // stosuje wtedy domyślny zestaw - patrz OverlayMetric.fromKeysOrDefault).
    fun getOverlayMetricKeys(): Flow<Set<String>?> = dataStore.data.map { preferences ->
        preferences[OVERLAY_METRICS]
    }

    suspend fun setOverlayMetricKeys(keys: Set<String>) {
        dataStore.edit { preferences ->
            preferences[OVERLAY_METRICS] = keys
        }
    }

    // Atomowe przełączenie jednej metryki - odczyt i zapis w jednej transakcji
    // dataStore.edit, żeby szybkie przełączanie kilku suwaków nie gubiło zmian
    // (read-modify-write na asynchronicznym Flow potrafił zgubić np. Vmax).
    // [defaultKeys] używane, gdy zbiór nie był jeszcze zapisany.
    suspend fun toggleOverlayMetric(key: String, defaultKeys: Set<String>) {
        dataStore.edit { preferences ->
            val current = preferences[OVERLAY_METRICS] ?: defaultKeys
            preferences[OVERLAY_METRICS] = if (key in current) current - key else current + key
        }
    }

    fun getRecordingQualityKey(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[RECORDING_QUALITY]
    }

    suspend fun setRecordingQualityKey(key: String) {
        dataStore.edit { preferences ->
            preferences[RECORDING_QUALITY] = key
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
