package com.itsoul.zbxclient

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ZabbixApp(preferencesManager: PreferencesManager) {
    val appSettings by preferencesManager.getAppSettings().collectAsState(initial = AppSettings())

    // Упрощенная версия без isSystemInDarkTheme
    val useDarkTheme = when (appSettings.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> false // временно
    }

    ZabbixAppTheme(
        darkTheme = useDarkTheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(preferencesManager, appSettings)
        }
    }
}

@Composable
fun ZabbixAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            background = androidx.compose.ui.graphics.Color(0xFF121212),
            surface = androidx.compose.ui.graphics.Color(0xFF121212),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),
            onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
            onTertiary = androidx.compose.ui.graphics.Color(0xFF000000),
            onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            background = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
            onTertiary = androidx.compose.ui.graphics.Color(0xFF000000),
            onBackground = androidx.compose.ui.graphics.Color(0xFF000000),
            onSurface = androidx.compose.ui.graphics.Color(0xFF000000),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AppNavigation(preferencesManager: PreferencesManager, appSettings: AppSettings) {
    val servers by preferencesManager.getServers().collectAsState(initial = emptyList())

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }
    var isLoading by remember { mutableStateOf(true) }

    // Показываем SplashScreen пока загружаем данные
    if (isLoading) {
        SplashScreen {
            isLoading = false
            currentScreen = AppScreen.Main
        }
    } else {
        when (currentScreen) {
            AppScreen.Splash -> SplashScreen {
                currentScreen = AppScreen.Main
            }
            AppScreen.Main -> MainScreen(
                servers = servers,
                onSettingsClick = { currentScreen = AppScreen.Settings },
                preferencesManager = preferencesManager
            )
            AppScreen.Settings -> SettingsScreen(
                appSettings = appSettings,
                onServersClick = { currentScreen = AppScreen.Servers },
                preferencesManager = preferencesManager,
                onBackClick = { currentScreen = AppScreen.Main } // ДОБАВЛЯЕМ эту строку
            )
            AppScreen.Servers -> ServersScreen(
                servers = servers,
                onBackClick = { currentScreen = AppScreen.Settings },
                onServersUpdate = { /* не используется */ },
                preferencesManager = preferencesManager
            )
        }
    }
}
sealed class AppScreen {
    object Splash : AppScreen()
    object Main : AppScreen()
    object Settings : AppScreen()
    object Servers : AppScreen()
}

@Preview(showBackground = true)
@Composable
fun ZabbixAppPreview() {
    val context = LocalContext.current
    ZabbixAppTheme(darkTheme = false) {
        ZabbixApp(preferencesManager = PreferencesManager(context))
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun ZabbixAppDarkPreview() {
    val context = LocalContext.current
    ZabbixAppTheme(darkTheme = true) {
        ZabbixApp(preferencesManager = PreferencesManager(context))
    }
}