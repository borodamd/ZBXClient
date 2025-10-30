// GeneralSettingsScreen.kt
package com.itsoul.zbxclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun GeneralSettingsScreen(
    appSettings: AppSettings,
    preferencesManager: PreferencesManager
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Settings options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            // Language Setting
            SettingsItem(
                icon = Icons.Filled.Language,
                title = "Language",
                subtitle = appSettings.language,
                onClick = {
                    // TODO: Implement language selection
                }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Theme Setting
            ThemeSelectionSection(
                currentTheme = appSettings.theme,
                onThemeSelected = { newTheme ->
                    coroutineScope.launch {
                        preferencesManager.saveTheme(newTheme)
                        // Показываем сообщение о необходимости перезагрузки
                        Toast.makeText(context, "Restart app to apply theme", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
        }
    }
}

@Composable
fun ThemeSelectionSection(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when (currentTheme) {
                        AppTheme.LIGHT -> "Light"
                        AppTheme.DARK -> "Dark"
                        AppTheme.SYSTEM -> "System default"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Theme options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .selectableGroup()
        ) {
            ThemeOption(
                title = "Light",
                isSelected = currentTheme == AppTheme.LIGHT,
                onClick = { onThemeSelected(AppTheme.LIGHT) }
            )

            ThemeOption(
                title = "Dark",
                isSelected = currentTheme == AppTheme.DARK,
                onClick = { onThemeSelected(AppTheme.DARK) }
            )

            ThemeOption(
                title = "System default",
                isSelected = currentTheme == AppTheme.SYSTEM,
                onClick = { onThemeSelected(AppTheme.SYSTEM) }
            )
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}