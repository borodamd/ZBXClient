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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    onServersClick: () -> Unit,
    preferencesManager: PreferencesManager
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("General", "Advanced")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
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
                preferencesManager = preferencesManager
            )
            1 -> AdvancedSettingsScreen(
                onServersClick = onServersClick
            )
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
                    title = "Servers",
                    subtitle = "Manage Zabbix servers",
                    onClick = onServersClick
                )
            }

            item {
                SettingsItem(
                    title = "Widget Settings",
                    subtitle = "Configure widget appearance",
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
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ZabbixAppTheme(
        darkTheme = false
    ) {
        SettingsScreen(
            appSettings = AppSettings(),
            onServersClick = {},
            preferencesManager = PreferencesManager(androidx.compose.ui.platform.LocalContext.current)
        )
    }
}