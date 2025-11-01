// PreferencesManager.kt
package com.itsoul.zbxclient

import android.content.Context
import android.content.Intent  // Добавьте этот импорт
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
import com.itsoul.zbxclient.widget.ProblemWidgetService

// Добавьте эти импорты:
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

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

        // Добавим ключ для хранения темы как строки (для совместимости с ThemeManager)
        val THEME = stringPreferencesKey("theme")
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

    // Сохранить тему (без вызова ThemeManager.saveTheme)
    suspend fun saveTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = theme.ordinal
            preferences[PreferenceKeys.THEME] = theme.name
        }

        // Применяем тему через ThemeManager (без рекурсии)
        com.itsoul.zbxclient.util.ThemeManager.applyTheme(theme)

        // Уведомляем об изменении темы
        notifyThemeChanged()
    }

    // Альтернативный метод для получения темы как Flow
    fun getThemeFlow(): Flow<AppTheme> = context.dataStore.data.map { preferences ->
        try {
            val themeName = preferences[PreferenceKeys.THEME] ?: AppTheme.SYSTEM.name
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }

    // Упрощенный метод для получения темы (без Flow)
    suspend fun getCurrentTheme(): AppTheme {
        return try {
            context.dataStore.data
                .map { preferences ->
                    val themeName = preferences[PreferenceKeys.THEME] ?: AppTheme.SYSTEM.name
                    AppTheme.valueOf(themeName)
                }
                .first()
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }

    /**
     * Уведомляет виджеты об изменении темы
     */


    private fun notifyThemeChanged() {
        Log.d("PreferencesManager", "Theme changed - updating widgets directly")

        try {
            // Прямое обновление всех виджетов через ProblemWidgetService
            val updateIntent = Intent(context, Class.forName("com.itsoul.zbxclient.widget.ProblemWidgetService"))
            updateIntent.action = "update_all_widgets"
            ProblemWidgetService.enqueueWork(context, updateIntent)
            Log.d("PreferencesManager", "Widget update requested")
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error updating widgets", e)
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