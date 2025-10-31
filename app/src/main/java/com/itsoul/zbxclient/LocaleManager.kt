package com.itsoul.zbxclient

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.*

object LocaleManager {
    private const val PREFS_NAME = "settings"
    private const val LANGUAGE_KEY = "language"

    enum class AppLanguage(val code: String) {
        SYSTEM("system"),
        ENGLISH("en"),
        RUSSIAN("ru");

        companion object {
            fun fromCode(code: String): AppLanguage {
                return values().find { it.code == code } ?: SYSTEM
            }
        }
    }

    fun setLocale(context: Context, language: AppLanguage): Context {
        persistLanguage(context, language)
        return updateResources(context, language)
    }

    fun getSavedLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val languageCode = prefs.getString(LANGUAGE_KEY, AppLanguage.SYSTEM.code)
        return AppLanguage.fromCode(languageCode ?: AppLanguage.SYSTEM.code)
    }

    private fun persistLanguage(context: Context, language: AppLanguage) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_KEY, language.code).apply()
    }

    private fun updateResources(context: Context, language: AppLanguage): Context {
        val locale = when (language) {
            AppLanguage.SYSTEM -> getSystemLocale()
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.RUSSIAN -> Locale("ru", "RU")
        }

        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
            val newContext = context.createConfigurationContext(configuration)
            // Также обновляем конфигурацию ресурсов основного контекста
            resources.updateConfiguration(configuration, resources.displayMetrics)
            newContext
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            context
        }
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }

    fun getDisplayName(language: AppLanguage, context: Context): String {
        return when (language) {
            AppLanguage.SYSTEM -> context.getString(R.string.language_system)
            AppLanguage.ENGLISH -> context.getString(R.string.language_english)
            AppLanguage.RUSSIAN -> context.getString(R.string.language_russian)
        }
    }

    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
}