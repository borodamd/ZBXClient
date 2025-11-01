package com.itsoul.zbxclient.util

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ThemeManager {

    /**
     * Получает текущую тему из настроек приложения через PreferencesManager
     */
    fun getCurrentTheme(context: Context): com.itsoul.zbxclient.AppTheme {
        return runBlocking {
            try {
                val preferencesManager = com.itsoul.zbxclient.PreferencesManager(context)
                preferencesManager.getThemeFlow().first()
            } catch (e: Exception) {
                com.itsoul.zbxclient.AppTheme.SYSTEM
            }
        }
    }

    /**
     * Получает тему для виджета на основе настроек приложения
     */
    fun getWidgetTheme(context: Context): WidgetTheme {
        return when (getCurrentTheme(context)) {
            com.itsoul.zbxclient.AppTheme.LIGHT -> WidgetTheme.LIGHT
            com.itsoul.zbxclient.AppTheme.DARK -> WidgetTheme.DARK
            com.itsoul.zbxclient.AppTheme.SYSTEM -> {
                if (isSystemDarkTheme(context)) {
                    WidgetTheme.DARK
                } else {
                    WidgetTheme.LIGHT
                }
            }
        }
    }

    /**
     * Применяет тему для Activity
     */
    fun applyTheme(theme: com.itsoul.zbxclient.AppTheme) {
        when (theme) {
            com.itsoul.zbxclient.AppTheme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            com.itsoul.zbxclient.AppTheme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            com.itsoul.zbxclient.AppTheme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * Сохраняет и применяет тему (без вызова PreferencesManager)
     */
    fun saveAndApplyTheme(context: Context, theme: com.itsoul.zbxclient.AppTheme) {
        // Применяем тему сразу
        applyTheme(theme)

        // Уведомляем об изменении темы
        notifyThemeChanged(context)
    }

    /**
     * Проверяет, использует ли система темную тему
     */
    private fun isSystemDarkTheme(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    /**
     * Уведомляет виджеты об изменении темы
     */
    private fun notifyThemeChanged(context: Context) {
        // Отправляем broadcast для обновления виджетов
        val intent = Intent("com.itsoul.zbxclient.THEME_CHANGED")
        context.sendBroadcast(intent)
        android.util.Log.d("ThemeManager", "Theme changed broadcast sent")
    }
}

enum class WidgetTheme {
    LIGHT, DARK
}