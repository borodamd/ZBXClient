// Models.kt
package com.itsoul.zbxclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

// Настройки приложения
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

@Serializable
data class DashboardState(
    val selectedServerId: Long = 0,
    val showAcknowledged: Boolean = false,
    val showInMaintenance: Boolean = false
)

// Zabbix API модели
@Serializable
data class ZabbixProblem(
    val eventid: String,
    val source: String,
    val objectid: String,
    val clock: String,
    val ns: String,
    val r_eventid: String? = null,
    val r_clock: String? = null,
    val r_ns: String? = null,
    val correlationid: String? = null,
    val userid: String? = null,
    val name: String,
    val acknowledged: String,
    val severity: String,
    val suppressed: String,
    val opdata: String? = null,
    val tags: List<ZabbixTag> = emptyList(),
    val hostName: String = "",
    // Добавляем новые поля для trigger информации
    val manualClose: String = "0",
    val comments: String = ""
) {
    fun getFormattedTime(): String {
        try {
            val timestamp = clock.toLong() * 1000
            val date = java.util.Date(timestamp)
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return formatter.format(date)
        } catch (e: Exception) {
            return "N/A"
        }
    }

    fun getDuration(): String {
        try {
            val problemTime = clock.toLong() * 1000
            val currentTime = System.currentTimeMillis()
            val duration = currentTime - problemTime

            return formatDuration(duration)
        } catch (e: Exception) {
            return "N/A"
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30

        return when {
            months > 0 -> "${months}M ${weeks % 4}w ${days % 30}d ${hours % 24}h ${minutes % 60}m"
            weeks > 0 -> "${weeks}w ${days % 7}d ${hours % 24}h ${minutes % 60}m"
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}

@Serializable
data class ZabbixTag(
    val tag: String,
    val value: String
)

@Serializable
data class ZabbixHost(
    val host: String,
    val hostid: String
)

// Восстанавливаем ZabbixTrigger который используется в MainScreen
@Serializable
data class ZabbixTrigger(
    val id: String,
    val description: String,
    val severity: Int,
    val host: String,
    val acknowledged: Boolean = false,
    val maintenance: Boolean = false
)


@Serializable
data class ZabbixError(
    val code: Int,
    val message: String,
    val data: String
)

@Serializable
data class ZabbixRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Map<String, @Contextual Any>,
    val id: Int = (1..10000).random(),
    val auth: String? = null  // Добавьте это поле
)

@Serializable
data class ZabbixResponse(
    val jsonrpc: String,
    val result: @Contextual Any?, // Изменили на Any? для поддержки разных типов
    val id: Int,
    val error: ZabbixError? = null
)