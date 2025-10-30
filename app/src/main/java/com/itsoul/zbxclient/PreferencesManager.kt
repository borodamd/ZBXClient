// PreferencesManager.kt
package com.itsoul.zbxclient

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.InternalSerializationApi

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@OptIn(InternalSerializationApi::class)
class PreferencesManager(private val context: Context) {

    private companion object {
        const val TAG = "PreferencesManager"
    }

    private object PreferenceKeys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SELECTED_SERVER_ID = longPreferencesKey("selected_server_id")
        val SHOW_ACKNOWLEDGED = booleanPreferencesKey("show_acknowledged")
        val SHOW_IN_MAINTENANCE = booleanPreferencesKey("show_in_maintenance")
        val SERVERS = stringPreferencesKey("servers")
    }

    // Метод для получения настроек приложения
    fun getAppSettings(): Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeOrdinal = preferences[PreferenceKeys.THEME_MODE] ?: AppTheme.SYSTEM.ordinal
        val language = preferences[PreferenceKeys.LANGUAGE] ?: "English"

        AppSettings(
            theme = AppTheme.values()[themeOrdinal],
            language = language
        )
    }

    // Сохранить тему
    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = theme.ordinal
        }
    }

    // Сохранить состояние дашборда
    suspend fun saveDashboardState(state: DashboardState) {
        Log.d(TAG, "SAVING DashboardState: serverId=${state.selectedServerId}, ack=${state.showAcknowledged}, maint=${state.showInMaintenance}")
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_SERVER_ID] = state.selectedServerId
            preferences[PreferenceKeys.SHOW_ACKNOWLEDGED] = state.showAcknowledged
            preferences[PreferenceKeys.SHOW_IN_MAINTENANCE] = state.showInMaintenance
        }
    }

    // Получить состояние дашборда
    fun getDashboardState(): Flow<DashboardState> = context.dataStore.data.map { preferences ->
        val state = DashboardState(
            selectedServerId = preferences[PreferenceKeys.SELECTED_SERVER_ID] ?: 0,
            showAcknowledged = preferences[PreferenceKeys.SHOW_ACKNOWLEDGED] ?: false,
            showInMaintenance = preferences[PreferenceKeys.SHOW_IN_MAINTENANCE] ?: false
        )
        Log.d(TAG, "LOADED DashboardState: serverId=${state.selectedServerId}, ack=${state.showAcknowledged}, maint=${state.showInMaintenance}")
        state
    }

    // СОХРАНИТЬ СЕРВЕРЫ
    suspend fun saveServers(servers: List<ZabbixServer>) {
        Log.d(TAG, "SAVING Servers: ${servers.size} servers")
        try {
            val json = Json.encodeToString(servers)
            Log.d(TAG, "JSON to save: $json")
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.SERVERS] = json
            }
            Log.d(TAG, "Servers saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving servers", e)
        }
    }

    // ПОЛУЧИТЬ СЕРВЕРЫ
    fun getServers(): Flow<List<ZabbixServer>> = context.dataStore.data.map { preferences ->
        try {
            val json = preferences[PreferenceKeys.SERVERS]
            Log.d(TAG, "JSON loaded: $json")
            if (json != null) {
                val servers = Json.decodeFromString<List<ZabbixServer>>(json)
                Log.d(TAG, "LOADED Servers: ${servers.size} servers")
                servers
            } else {
                Log.d(TAG, "No servers found in preferences")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading servers", e)
            emptyList()
        }
    }
}