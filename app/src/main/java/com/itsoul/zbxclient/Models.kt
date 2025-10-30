// Models.kt
package com.itsoul.zbxclient

import kotlinx.serialization.Serializable

@Serializable
data class ZabbixServer(
    val id: Long,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val useApiKey: Boolean = false,
    val apiKey: String = ""
)

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "English"
)

data class ZabbixTrigger(
    val id: String,
    val description: String,
    val severity: Int,
    val host: String,
    val acknowledged: Boolean = false,
    val maintenance: Boolean = false
)

@Serializable
data class DashboardState(
    val selectedServerId: Long = 0,
    val showAcknowledged: Boolean = false,
    val showInMaintenance: Boolean = false
)