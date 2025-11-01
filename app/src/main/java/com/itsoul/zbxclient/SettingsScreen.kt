// SettingsScreen.kt
package com.itsoul.zbxclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    onServersClick: () -> Unit,
    preferencesManager: PreferencesManager,
    onBackClick: () -> Unit,
    appState: AppState
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.general),
        stringResource(R.string.advanced)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Header с кнопкой Back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка Back
            IconButton(
                onClick = onBackClick
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> GeneralSettingsScreen(
                appSettings = appSettings,
                preferencesManager = preferencesManager,
                appState = appState
            )
            1 -> AdvancedSettingsScreen(
                onServersClick = onServersClick
            )
        }
    }
}

@Composable
fun GeneralSettingsScreen(
    appSettings: AppSettings,
    preferencesManager: PreferencesManager,
    appState: AppState
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Используем State для отслеживания текущей темы
    var currentTheme by remember {
        mutableStateOf(com.itsoul.zbxclient.util.ThemeManager.getCurrentTheme(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Секция настроек языка (ВОССТАНОВЛЕНА)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val languages = appState.getAvailableLanguages()
                var languageExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = !languageExpanded }
                ) {
                    TextField(
                        value = LocaleManager.getDisplayName(appState.currentLanguage, context),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        languages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(LocaleManager.getDisplayName(language, context)) },
                                onClick = {
                                    appState.setLanguage(language)
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Секция темы
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var themeExpanded by remember { mutableStateOf(false) }

                val currentThemeName = when (currentTheme) {
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                }

                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = !themeExpanded }
                ) {
                    TextField(
                        value = currentThemeName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_light)) },
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.saveTheme(AppTheme.LIGHT)
                                    currentTheme = AppTheme.LIGHT
                                }
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_dark)) },
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.saveTheme(AppTheme.DARK)
                                    currentTheme = AppTheme.DARK
                                }
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_system)) },
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.saveTheme(AppTheme.SYSTEM)
                                    currentTheme = AppTheme.SYSTEM
                                }
                                themeExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun AdvancedSettingsScreen(
    onServersClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Список настроек
        LazyColumn {
            item {
                SettingsItem(
                    title = stringResource(R.string.servers),
                    subtitle = stringResource(R.string.manage_zabbix_servers),
                    onClick = onServersClick
                )
            }

            item {
                SettingsItem(
                    title = stringResource(R.string.widget_settings),
                    subtitle = stringResource(R.string.configure_widget_appearance),
                    onClick = { /* TODO: Widget settings */ }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = stringResource(R.string.navigate),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    val context = LocalContext.current
    ZabbixAppTheme(
        darkTheme = false
    ) {
        SettingsScreen(
            appSettings = AppSettings(),
            onServersClick = {},
            preferencesManager = PreferencesManager(context),
            onBackClick = {},
            appState = rememberAppState(context = context, preferencesManager = PreferencesManager(context))
        )
    }
}