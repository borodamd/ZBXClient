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

        applySavedLocale(true)

        setContent {
            ZabbixAppContent()
        }
    }

    override fun onResume() {
        super.onResume()
        applySavedLocale(false)
    }

    private fun applySavedLocale(force: Boolean) {
        val savedLanguage = LocaleManager.getSavedLanguage(this)
        val newLanguageCode = savedLanguage.code

        if (force || currentLanguageCode != newLanguageCode) {
            currentLanguageCode = newLanguageCode
            LocaleManager.setLocale(this, savedLanguage)
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