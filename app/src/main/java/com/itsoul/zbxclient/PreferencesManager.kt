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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    private companion object {
        const val TAG = "PreferencesManager"
    }

    private object PreferencesKeys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SELECTED_SERVER_ID = longPreferencesKey("selected_server_id")
        val SHOW_ACKNOWLEDGED = booleanPreferencesKey("show_acknowledged")
        val SHOW_IN_MAINTENANCE = booleanPreferencesKey("show_in_maintenance")
    }

    // Метод для получения настроек приложения
    fun getAppSettings(): Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeOrdinal = preferences[PreferencesKeys.THEME_MODE] ?: AppTheme.SYSTEM.ordinal
        val language = preferences[PreferencesKeys.LANGUAGE] ?: "English"

        AppSettings(
            theme = AppTheme.values()[themeOrdinal],
            language = language
        )
    }

    // Сохранить тему
    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = theme.ordinal
        }
    }

    // Сохранить состояние дашборда
    suspend fun saveDashboardState(state: DashboardState) {
        Log.d(TAG, "SAVING DashboardState: serverId=${state.selectedServerId}, ack=${state.showAcknowledged}, maint=${state.showInMaintenance}")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_SERVER_ID] = state.selectedServerId
            preferences[PreferencesKeys.SHOW_ACKNOWLEDGED] = state.showAcknowledged
            preferences[PreferencesKeys.SHOW_IN_MAINTENANCE] = state.showInMaintenance
        }
    }

    // Получить состояние дашборда
    fun getDashboardState(): Flow<DashboardState> = context.dataStore.data.map { preferences ->
        val state = DashboardState(
            selectedServerId = preferences[PreferencesKeys.SELECTED_SERVER_ID] ?: 0,
            showAcknowledged = preferences[PreferencesKeys.SHOW_ACKNOWLEDGED] ?: false,
            showInMaintenance = preferences[PreferencesKeys.SHOW_IN_MAINTENANCE] ?: false
        )
        Log.d(TAG, "LOADED DashboardState: serverId=${state.selectedServerId}, ack=${state.showAcknowledged}, maint=${state.showInMaintenance}")
        state
    }
}