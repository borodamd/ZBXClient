package com.itsoul.zbxclient.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object WidgetLocaleManager {

    fun getWidgetLocale(context: Context): Locale {
        val savedLanguage = getSavedLanguage(context)
        return when (savedLanguage) {
            com.itsoul.zbxclient.LocaleManager.AppLanguage.SYSTEM -> getSystemLocale()
            com.itsoul.zbxclient.LocaleManager.AppLanguage.ENGLISH -> Locale.ENGLISH
            com.itsoul.zbxclient.LocaleManager.AppLanguage.RUSSIAN -> Locale("ru", "RU")
            else -> getSystemLocale() // Добавляем else branch
        }
    }

    fun getWidgetResources(context: Context, targetLocale: Locale): Resources {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(targetLocale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration).resources
        } else {
            @Suppress("DEPRECATION")
            context.resources.apply {
                updateConfiguration(configuration, displayMetrics)
            }
            context.resources
        }
    }

    fun getLocalizedString(context: Context, stringResName: String, vararg formatArgs: Any): String {
        val locale = getWidgetLocale(context)
        val widgetResources = getWidgetResources(context, locale)

        val resourceId = getStringResourceId(context, stringResName)
        return if (resourceId != 0) {
            try {
                widgetResources.getString(resourceId, *formatArgs)
            } catch (e: Exception) {
                // Fallback to default resources
                context.resources.getString(resourceId, *formatArgs)
            }
        } else {
            // Fallback to English
            stringResName
        }
    }

    private fun getStringResourceId(context: Context, stringResName: String): Int {
        return context.resources.getIdentifier(stringResName, "string", context.packageName)
    }

    private fun getSavedLanguage(context: Context): com.itsoul.zbxclient.LocaleManager.AppLanguage {
        return com.itsoul.zbxclient.LocaleManager.getSavedLanguage(context)
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }
}