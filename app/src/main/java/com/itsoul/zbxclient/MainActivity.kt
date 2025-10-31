package com.itsoul.zbxclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private var currentLanguageCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Применяем сохраненную локаль перед созданием UI
        applySavedLocale(true)

        setContent {
            ZabbixAppContent()
        }
    }

    override fun onResume() {
        super.onResume()
        // Проверяем изменение языка при возвращении в приложение
        applySavedLocale(false)
    }

    private fun applySavedLocale(force: Boolean) {
        val savedLanguage = LocaleManager.getSavedLanguage(this)
        val newLanguageCode = savedLanguage.code

        // Применяем если язык изменился или принудительно
        if (force || currentLanguageCode != newLanguageCode) {
            currentLanguageCode = newLanguageCode
            val newContext = LocaleManager.setLocale(this, savedLanguage)

            // Обновляем ресурсы активности
            resources.updateConfiguration(
                newContext.resources.configuration,
                newContext.resources.displayMetrics
            )

            println("DEBUG: Language applied: $newLanguageCode")
        }
    }
}

@Composable
fun ZabbixAppContent() {
    val context = LocalContext.current
    val preferencesManager = androidx.compose.runtime.remember { PreferencesManager(context) }
    ZabbixApp(preferencesManager = preferencesManager)
}