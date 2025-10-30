package com.itsoul.zbxclient

data class ZabbixTriggerDetail(
    val triggerid: String,
    val hosts: List<ZabbixHost>?
)

// Альтернатива - создайте упрощенный класс для парсинга
data class ZabbixProblemResponse(
    val eventid: String,
    val objectid: String,
    val clock: String,
    val name: String,
    val severity: String,
    val acknowledged: String,
    val suppressed: String,
    val tags: List<ZabbixTag> = emptyList()
)

// И функция для преобразования
fun ZabbixProblemResponse.toZabbixProblem(hostName: String = ""): ZabbixProblem {
    return ZabbixProblem(
        eventid = this.eventid,
        source = "",
        objectid = this.objectid,
        clock = this.clock,
        ns = "0",
        name = this.name,
        acknowledged = this.acknowledged,
        severity = this.severity,
        suppressed = this.suppressed,
        tags = this.tags,
        hostName = hostName
    )
}


data class ZabbixHost(
    val host: String,
    val hostid: String
)

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
    val hostName: String = ""
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

data class ZabbixTag(
    val tag: String,
    val value: String
)

data class ZabbixResponse(
    val jsonrpc: String,
    val result: List<Map<String, Any>>, // Универсальный тип для парсинга
    val id: Int,
    val error: ZabbixError? = null
)
data class ZabbixError(
    val code: Int,
    val message: String,
    val data: String
)

data class ZabbixRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Map<String, Any>,
    // Убираем auth из тела запроса - он теперь в header
    val id: Int = (1..10000).random()
)