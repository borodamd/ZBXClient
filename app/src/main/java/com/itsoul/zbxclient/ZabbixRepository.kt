package com.itsoul.zbxclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ZabbixRepository {

    suspend fun getProblemsWithHostNames(serverUrl: String, apiKey: String): List<ZabbixProblem> {
        return withContext(Dispatchers.IO) {
            try {
                val problems = getProblems(serverUrl, apiKey)
                val triggerIds = problems.map { it.objectid }.distinct()

                if (triggerIds.isEmpty()) return@withContext problems

                val hostNamesMap = getHostNamesByTriggerIds(serverUrl, apiKey, triggerIds)

                // Обновляем проблемы с именами хостов
                problems.map { problem ->
                    val hostName = hostNamesMap[problem.objectid] ?: "Host-${problem.objectid}"
                    problem.copy(hostName = hostName)
                }
            } catch (e: Exception) {
                throw Exception("Failed to fetch problems with host names: ${e.message}")
            }
        }
    }

    private suspend fun getProblems(serverUrl: String, apiKey: String): List<ZabbixProblem> {
        return withContext(Dispatchers.IO) {
            try {
                val apiService = createApiService(serverUrl, apiKey)

                // Сначала получаем проблемы
                val problemRequest = ZabbixRequest(
                    method = "problem.get",
                    params = mapOf(
                        "output" to "extend",
                        "selectAcknowledges" to "extend",
                        "selectSuppressionData" to "extend",
                        "selectTags" to "extend"
                    )
                )

                val problemResponse = apiService.makeRequest(problemRequest)

                if (problemResponse.isSuccessful) {
                    problemResponse.body()?.let { zabbixResponse ->
                        if (zabbixResponse.error != null) {
                            throw Exception("Zabbix API error: ${zabbixResponse.error.message}")
                        }

                        // Получаем список trigger IDs
                        val triggerIds = zabbixResponse.result.mapNotNull {
                            it["objectid"] as? String
                        }.distinct()

                        // Получаем данные триггеров с manual_close и comments
                        val triggersData = getTriggersData(serverUrl, apiKey, triggerIds)

                        // Парсим проблемы с данными триггеров
                        zabbixResponse.result.map { problemMap ->
                            val triggerId = problemMap["objectid"] as? String ?: ""
                            val triggerInfo = triggersData[triggerId] ?: mapOf(
                                "manual_close" to "0",
                                "comments" to ""
                            )

                            ZabbixProblem(
                                eventid = problemMap["eventid"] as? String ?: "",
                                source = problemMap["source"] as? String ?: "0",
                                objectid = triggerId,
                                clock = problemMap["clock"] as? String ?: "",
                                ns = problemMap["ns"] as? String ?: "0",
                                r_eventid = problemMap["r_eventid"] as? String,
                                r_clock = problemMap["r_clock"] as? String,
                                r_ns = problemMap["r_ns"] as? String,
                                correlationid = problemMap["correlationid"] as? String,
                                userid = problemMap["userid"] as? String,
                                name = problemMap["name"] as? String ?: "",
                                acknowledged = problemMap["acknowledged"] as? String ?: "0",
                                severity = problemMap["severity"] as? String ?: "0",
                                suppressed = problemMap["suppressed"] as? String ?: "0",
                                opdata = problemMap["opdata"] as? String,
                                tags = parseTags(problemMap["tags"]),
                                hostName = "", // Будет заполнено позже
                                manualClose = triggerInfo["manual_close"] as? String ?: "0",
                                comments = triggerInfo["comments"] as? String ?: ""
                            )
                        }
                    } ?: emptyList()
                } else {
                    throw Exception("HTTP error: ${problemResponse.code()} - ${problemResponse.message()}")
                }
            } catch (e: Exception) {
                throw Exception("Failed to fetch problems: ${e.message}")
            }
        }
    }

    private suspend fun getTriggersData(serverUrl: String, apiKey: String, triggerIds: List<String>): Map<String, Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                if (triggerIds.isEmpty()) return@withContext emptyMap()

                val apiService = createApiService(serverUrl, apiKey)

                val request = ZabbixRequest(
                    method = "trigger.get",
                    params = mapOf(
                        "output" to "extend",
                        "triggerids" to triggerIds,
                        "selectHosts" to listOf("host")
                    )
                )

                val response = apiService.makeRequestForTriggerDetails(request)

                if (response.isSuccessful) {
                    response.body()?.let { zabbixResponse ->
                        if (zabbixResponse.error != null) {
                            throw Exception("Zabbix API error: ${zabbixResponse.error.message}")
                        }

                        // Создаем мапу с данными триггеров
                        zabbixResponse.result.associate { triggerMap ->
                            val triggerId = triggerMap["triggerid"] as? String ?: ""
                            val manualClose = triggerMap["manual_close"] as? String ?: "0"
                            val comments = triggerMap["comments"] as? String ?: ""

                            triggerId to mapOf(
                                "manual_close" to manualClose,
                                "comments" to comments
                            )
                        }
                    } ?: emptyMap()
                } else {
                    println("Error fetching triggers data: HTTP ${response.code()}")
                    emptyMap()
                }
            } catch (e: Exception) {
                println("Error fetching triggers data: ${e.message}")
                emptyMap()
            }
        }
    }

    private suspend fun getHostNamesByTriggerIds(serverUrl: String, apiKey: String, triggerIds: List<String>): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val apiService = createApiService(serverUrl, apiKey)

                val request = ZabbixRequest(
                    method = "trigger.get",
                    params = mapOf(
                        "output" to "extend",
                        "triggerids" to triggerIds,
                        "selectHosts" to listOf("host")
                    )
                )

                val response = apiService.makeRequestForTriggerDetails(request)

                if (response.isSuccessful) {
                    response.body()?.let { zabbixResponse ->
                        if (zabbixResponse.error != null) {
                            throw Exception("Zabbix API error: ${zabbixResponse.error.message}")
                        }

                        // Парсим имена хостов из Map
                        zabbixResponse.result.associate { triggerMap ->
                            val triggerId = triggerMap["triggerid"] as? String ?: ""
                            val hosts = triggerMap["hosts"] as? List<Map<String, Any>> ?: emptyList()
                            val hostName = hosts.firstOrNull()?.get("host") as? String ?: "Unknown"
                            triggerId to hostName
                        }
                    } ?: emptyMap()
                } else {
                    throw Exception("HTTP error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                println("Error fetching host names: ${e.message}")
                emptyMap()
            }
        }
    }

    private fun parseTags(tagsAny: Any?): List<ZabbixTag> {
        return try {
            when (tagsAny) {
                is List<*> -> {
                    tagsAny.filterIsInstance<Map<String, Any>>().map { tagMap ->
                        ZabbixTag(
                            tag = tagMap["tag"] as? String ?: "",
                            value = tagMap["value"] as? String ?: ""
                        )
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createApiService(baseUrl: String, apiKey: String): ZabbixApiService {
        val normalizedUrl = if (baseUrl.endsWith("/api_jsonrpc.php")) {
            baseUrl.substringBeforeLast("/api_jsonrpc.php") + "/"
        } else if (!baseUrl.endsWith("/")) {
            "$baseUrl/"
        } else {
            baseUrl
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ZabbixApiService::class.java)
    }

    suspend fun closeProblem(serverUrl: String, apiKey: String, eventId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "event.acknowledge",
                    "params": {
                        "eventids": "$eventId",
                        "action": 1,
                        "message": "Closed from mobile app"
                    },
                    "id": ${System.currentTimeMillis()}
                }
                """.trimIndent()

                val mediaType = "application/json".toMediaType()
                val request = okhttp3.Request.Builder()
                    .url(serverUrl)
                    .post(requestBody.toRequestBody(mediaType))
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                println("🔄 Sending direct HTTP close request for event: $eventId")
                val response = client.newCall(request).execute()

                val result = response.isSuccessful
                println("✅ Direct HTTP close result: $result (HTTP ${response.code})")

                result
            } catch (e: Exception) {
                println("❌ Direct HTTP close exception: ${e.message}")
                false
            }
        }
    }

    suspend fun acknowledgeEvent(serverUrl: String, apiKey: String, eventId: String, isAcknowledge: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val action = if (isAcknowledge) 2 else 16
                val message = if (isAcknowledge)
                    "Event acknowledged from mobile app"
                else
                    "Event unacknowledged from mobile app"

                val requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "event.acknowledge",
                    "params": {
                        "eventids": "$eventId",
                        "action": $action,
                        "message": "$message"
                    },
                    "id": ${System.currentTimeMillis()}
                }
                """.trimIndent()

                val mediaType = "application/json".toMediaType()
                val request = okhttp3.Request.Builder()
                    .url(serverUrl)
                    .post(requestBody.toRequestBody(mediaType))
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                println("🔄 Sending direct HTTP acknowledge request for event: $eventId")
                val response = client.newCall(request).execute()

                val result = response.isSuccessful
                println("✅ Direct HTTP acknowledge result: $result (HTTP ${response.code})")

                result
            } catch (e: Exception) {
                println("❌ Direct HTTP acknowledge exception: ${e.message}")
                false
            }
        }
    }
}