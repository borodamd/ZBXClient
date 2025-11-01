package com.itsoul.zbxclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.JobIntentService
import com.itsoul.zbxclient.PreferencesManager
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixProblem
import com.itsoul.zbxclient.ZabbixRepository
import com.itsoul.zbxclient.ZabbixServer
import com.itsoul.zbxclient.util.ServerCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProblemWidgetService : JobIntentService() {

    companion object {
        const val ACTION_UPDATE_ALL_WIDGETS = "update_all_widgets"
        const val ACTION_UPDATE_WIDGET = "update_widget"
        const val ACTION_FORCE_REFRESH = "force_refresh"
        const val ACTION_TOGGLE_ACK = "toggle_ack"
        const val ACTION_TOGGLE_MAINT = "toggle_maint"
        const val EXTRA_SERVER_ID = "server_id"
        const val WIDGET_PREF_NAME = "problem_widget_prefs"
        const val PREF_SERVER_ID = "server_id_"
        const val PREF_UPDATE_INTERVAL = "update_interval_"
        const val PREF_SHOW_ACK = "show_ack_"
        const val PREF_SHOW_MAINT = "show_maint_"

        private const val JOB_ID = 1000

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, ProblemWidgetService::class.java, JOB_ID, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
        Log.d("ProblemWidgetService", "onHandleWork: ${intent.action}")

        runBlocking {
            when (intent.action) {
                ACTION_UPDATE_WIDGET -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    Log.d("ProblemWidgetService", "ACTION_UPDATE_WIDGET for id: $appWidgetId")
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        updateAppWidget(this@ProblemWidgetService, appWidgetId)
                    }
                    // Добавляем Unit в конец
                    Unit
                }
                ACTION_FORCE_REFRESH -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    Log.d("ProblemWidgetService", "ACTION_FORCE_REFRESH for id: $appWidgetId")
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        forceRefreshWidget(this@ProblemWidgetService, appWidgetId)
                    }
                    Unit
                }
                ACTION_UPDATE_ALL_WIDGETS -> {
                    Log.d("ProblemWidgetService", "ACTION_UPDATE_ALL_WIDGETS")
                    updateAllAppWidgets(this@ProblemWidgetService)
                    Unit
                }
                ACTION_TOGGLE_ACK -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    Log.d("ProblemWidgetService", "ACTION_TOGGLE_ACK for id: $appWidgetId")
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        toggleAckFilter(this@ProblemWidgetService, appWidgetId)
                    }
                    Unit
                }
                ACTION_TOGGLE_MAINT -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    Log.d("ProblemWidgetService", "ACTION_TOGGLE_MAINT for id: $appWidgetId")
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        toggleMaintFilter(this@ProblemWidgetService, appWidgetId)
                    }
                    Unit
                }
                else -> {
                    Log.d("ProblemWidgetService", "Unknown action: ${intent.action}")
                    Unit
                }
            }
        }
    }

    private suspend fun toggleAckFilter(context: Context, appWidgetId: Int) {
        Log.d("ProblemWidgetService", "toggleAckFilter for widget: $appWidgetId")

        val prefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val currentAck = prefs.getBoolean("$PREF_SHOW_ACK$appWidgetId", false)
        val newAck = !currentAck
        prefs.edit().putBoolean("$PREF_SHOW_ACK$appWidgetId", newAck).apply()

        Log.d("ProblemWidgetService", "Ack filter changed from $currentAck to $newAck for widget $appWidgetId")

        // Принудительно обновляем виджет
        updateAppWidget(context, appWidgetId)
    }

    private suspend fun toggleMaintFilter(context: Context, appWidgetId: Int) {
        Log.d("ProblemWidgetService", "toggleMaintFilter for widget: $appWidgetId")

        val prefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val currentMaint = prefs.getBoolean("$PREF_SHOW_MAINT$appWidgetId", false)
        val newMaint = !currentMaint
        prefs.edit().putBoolean("$PREF_SHOW_MAINT$appWidgetId", newMaint).apply()

        Log.d("ProblemWidgetService", "Maint filter changed from $currentMaint to $newMaint for widget $appWidgetId")

        // Принудительно обновляем виджет
        updateAppWidget(context, appWidgetId)
    }

    private suspend fun forceRefreshWidget(context: Context, appWidgetId: Int) {
        Log.d("ProblemWidgetService", "Force refreshing widget $appWidgetId")

        val prefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val serverId = prefs.getLong("$PREF_SERVER_ID$appWidgetId", -1)

        if (serverId != -1L) {
            clearCacheForServer(context, serverId)
            Log.d("ProblemWidgetService", "Cleared cache for server $serverId")
        }

        updateAppWidget(context, appWidgetId)
    }

    private fun clearCacheForServer(context: Context, serverId: Long) {
        try {
            val cachePrefs = context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
            cachePrefs.edit().remove("problems_$serverId").apply()
            Log.d("ProblemWidgetService", "Cache cleared for server $serverId")
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error clearing cache for server $serverId", e)
        }
    }

    private suspend fun updateAllAppWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = getAppWidgetIds(context)
        Log.d("ProblemWidgetService", "Updating all widgets: ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetId, appWidgetManager)
        }
    }

    private suspend fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager? = null
    ) {
        val manager = appWidgetManager ?: AppWidgetManager.getInstance(context)
        Log.d("ProblemWidgetService", "Updating widget $appWidgetId")

        val prefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val serverId = prefs.getLong("$PREF_SERVER_ID$appWidgetId", -1)
        val showAck = prefs.getBoolean("$PREF_SHOW_ACK$appWidgetId", false)
        val showMaint = prefs.getBoolean("$PREF_SHOW_MAINT$appWidgetId", false)

        Log.d("ProblemWidgetService", "Widget config - serverId: $serverId, showAck: $showAck, showMaint: $showMaint")

        if (serverId == -1L) {
            Log.d("ProblemWidgetService", "Widget $appWidgetId not configured, showing empty")
            val views = getEmptyRemoteViews(context, appWidgetId)
            manager.updateAppWidget(appWidgetId, views)
            return
        }

        val problems = loadProblemsFromCache(context, serverId, appWidgetId)
        Log.d("ProblemWidgetService", "Loaded ${problems.size} problems for server $serverId")

        val views = getRemoteViews(context, appWidgetId, serverId, problems, showAck, showMaint)
        manager.updateAppWidget(appWidgetId, views)
        Log.d("ProblemWidgetService", "Widget $appWidgetId updated successfully")
    }
    private suspend fun loadProblemsFromCache(context: Context, serverId: Long, appWidgetId: Int): List<ZabbixProblem> {
        return try {
            val cachePrefs = context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
            val problemsJson = cachePrefs.getString("problems_$serverId", null)

            val problems = if (problemsJson != null) {
                Log.d("ProblemWidgetService", "Found cached problems for server $serverId")
                val cachedProblems = Json.decodeFromString<List<ZabbixProblem>>(problemsJson)

                if (isTestData(cachedProblems)) {
                    Log.d("ProblemWidgetService", "Found test data in cache, loading from API")
                    loadProblemsFromApi(context, serverId)
                } else {
                    cachedProblems
                }
            } else {
                Log.d("ProblemWidgetService", "No cached problems, loading from API")
                loadProblemsFromApi(context, serverId)
            }

            val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
            val showAck = widgetPrefs.getBoolean("$PREF_SHOW_ACK$appWidgetId", false)
            val showMaint = widgetPrefs.getBoolean("$PREF_SHOW_MAINT$appWidgetId", false)

            problems.filter { problem ->
                val ackFilter = showAck || problem.acknowledged != "1"
                val maintFilter = showMaint || problem.suppressed != "1"
                ackFilter && maintFilter
            }
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error loading problems", e)
            emptyList()
        }
    }

    private fun isTestData(problems: List<ZabbixProblem>): Boolean {
        return problems.any { problem ->
            problem.name.contains("Test Problem", ignoreCase = true) ||
                    problem.hostName.contains("Test Host", ignoreCase = true) ||
                    problem.eventid.startsWith("test_") ||
                    problem.hostName.contains("Server-0") ||
                    problem.name.contains("High CPU Usage on Server-01") ||
                    problem.name.contains("Low Disk Space on Server-02") ||
                    problem.name.contains("Network Latency Issue")
        }
    }

    private suspend fun loadProblemsFromApi(context: Context, serverId: Long): List<ZabbixProblem> {
        return try {
            Log.d("ProblemWidgetService", "Loading REAL problems from API for server $serverId")

            val preferencesManager = PreferencesManager(context)
            val servers = withContext(Dispatchers.IO) {
                preferencesManager.getServers().first()
            }

            val server = servers.find { it.id == serverId }
            if (server == null) {
                Log.e("ProblemWidgetService", "Server $serverId not found")
                return emptyList()
            }

            val problems = loadRealProblemsUsingRepository(server)
            Log.d("ProblemWidgetService", "Loaded ${problems.size} REAL problems from API")

            cacheProblems(context, serverId, problems)
            problems
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error loading REAL problems from API", e)
            emptyList()
        }
    }

    private suspend fun loadRealProblemsUsingRepository(server: ZabbixServer): List<ZabbixProblem> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ProblemWidgetService", "Using ZabbixRepository for server: ${server.url}")

                val repository = ZabbixRepository()
                val problems = repository.getProblemsWithHostNames(server.url, server.apiKey)

                Log.d("ProblemWidgetService", "Successfully loaded ${problems.size} problems using ZabbixRepository")
                problems
            } catch (e: Exception) {
                Log.e("ProblemWidgetService", "Failed to load problems using ZabbixRepository: ${e.message}", e)
                throw e
            }
        }
    }

    private fun cacheProblems(context: Context, serverId: Long, problems: List<ZabbixProblem>) {
        try {
            val prefs = context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
            val json = Json.encodeToString<List<ZabbixProblem>>(problems)
            prefs.edit().putString("problems_$serverId", json).apply()
            Log.d("ProblemWidgetService", "Cached ${problems.size} problems for server $serverId")
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error caching problems", e)
        }
    }


    private suspend fun getRemoteViews(
        context: Context,
        appWidgetId: Int,
        serverId: Long,
        problems: List<ZabbixProblem>,
        showAck: Boolean,
        showMaint: Boolean
    ): RemoteViews {
        val widgetTheme = com.itsoul.zbxclient.util.ThemeManager.getWidgetTheme(context)
        val layoutRes = when (widgetTheme) {
            com.itsoul.zbxclient.util.WidgetTheme.DARK -> R.layout.widget_problem_dark
            com.itsoul.zbxclient.util.WidgetTheme.LIGHT -> R.layout.widget_problem
            else -> R.layout.widget_problem
        }

        Log.d("ProblemWidgetService", "Using layout: $layoutRes for widget $appWidgetId")

        // Получаем общее количество проблем (до фильтрации)
        val totalProblemsCount = getTotalProblemsCount(context, serverId)
        val filteredProblemsCount = problems.size

        return RemoteViews(context.packageName, layoutRes).apply {
            val serverName = getServerName(context, serverId)

            // Используем локализованные строки для виджетов
            val noDataText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "no_active_problems")
            val lastUpdateText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "widget_last_update")
            val problemsText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "widget_problems_count")
            val unknownServerText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "unknown_server")

            // Получаем локаль для форматирования даты
            val locale = com.itsoul.zbxclient.util.WidgetLocaleManager.getWidgetLocale(context)
            val timeText = SimpleDateFormat("HH:mm", locale).format(Date())

            // Форматируем текст в зависимости от наличия проблем
            if (problems.isEmpty()) {
                if (totalProblemsCount == 0) {
                    // Нет проблем вообще
                    setTextViewText(R.id.widget_title, "${serverName ?: "Server"} - $noDataText")
                    setTextViewText(R.id.widget_last_update, "$lastUpdateText $timeText")
                } else {
                    // Есть проблемы, но все отфильтрованы
                    setTextViewText(R.id.widget_title, "${serverName ?: "Server"} - 0 из $totalProblemsCount")
                    setTextViewText(R.id.widget_last_update, "$lastUpdateText $timeText - все проблемы скрыты фильтрами")
                }
            } else {
                // Есть отфильтрованные проблемы
                if (filteredProblemsCount == totalProblemsCount) {
                    // Все проблемы видны (фильтры не активны)
                    setTextViewText(R.id.widget_title, serverName ?: unknownServerText)
                    setTextViewText(R.id.widget_last_update, "$lastUpdateText $timeText - $filteredProblemsCount $problemsText")
                } else {
                    // Часть проблем скрыта фильтрами
                    setTextViewText(R.id.widget_title, "${serverName ?: unknownServerText} - $filteredProblemsCount из $totalProblemsCount")
                    setTextViewText(R.id.widget_last_update, "$lastUpdateText $timeText")
                }
            }

            // Обработчики для иконок фильтров - используем BROADCAST
            val ackIntent = Intent(context, ProblemWidget::class.java).apply {
                action = "com.itsoul.zbxclient.ACTION_TOGGLE_ACK"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val ackPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1, // Уникальный requestCode
                ackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_ack_btn, ackPendingIntent)

            val maintIntent = Intent(context, ProblemWidget::class.java).apply {
                action = "com.itsoul.zbxclient.ACTION_TOGGLE_MAINT"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val maintPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2, // Уникальный requestCode
                maintIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_maint_btn, maintPendingIntent)

            // Обновляем текст и внешний вид кнопок с учетом состояния
            val ackText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "ack")
            val maintText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "maint")

            setTextViewText(R.id.widget_ack_btn, "$ackText: ${if (showAck) "ON" else "OFF"}")
            setTextViewText(R.id.widget_maint_btn, "$maintText: ${if (showMaint) "ON" else "OFF"}")

            // Визуальное отображение состояния фильтров для TextView
            val ackColor = if (showAck) {
                if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK)
                    context.resources.getColor(R.color.widget_accent_dark, null)
                else
                    context.resources.getColor(R.color.purple_500, null)
            } else {
                if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK)
                    context.resources.getColor(R.color.widget_text_secondary_dark, null)
                else
                    context.resources.getColor(android.R.color.darker_gray, null)
            }

            val maintColor = if (showMaint) {
                if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK)
                    context.resources.getColor(R.color.widget_accent_dark, null)
                else
                    context.resources.getColor(R.color.purple_500, null)
            } else {
                if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK)
                    context.resources.getColor(R.color.widget_text_secondary_dark, null)
                else
                    context.resources.getColor(android.R.color.darker_gray, null)
            }

            // Устанавливаем цвета для TextView
            setTextColor(R.id.widget_ack_btn, ackColor)
            setTextColor(R.id.widget_maint_btn, maintColor)

            // Обновляем обработчик кнопки обновления для принудительного обновления
            val refreshIntent = Intent(context, ProblemWidget::class.java).apply {
                action = "com.itsoul.zbxclient.ACTION_FORCE_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

            // Принудительно обновляем адаптер списка
            val adapterIntent = Intent(context, ProblemWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_SERVER_ID, serverId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            setRemoteAdapter(R.id.widget_problems_list, adapterIntent)

            // Устанавливаем пустое view
            setEmptyView(R.id.widget_problems_list, R.id.widget_empty_view)

            // Принудительно уведомляем об изменении данных
            notifyAppWidgetViewDataChanged(context, appWidgetId, R.id.widget_problems_list)

            Log.d("ProblemWidgetService", "RemoteViews setup complete for widget $appWidgetId")
        }
    }


    private suspend fun getTotalProblemsCount(context: Context, serverId: Long): Int {
        return try {
            val cachePrefs = context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
            val problemsJson = cachePrefs.getString("problems_$serverId", null)

            if (problemsJson != null) {
                val allProblems = Json.decodeFromString<List<ZabbixProblem>>(problemsJson)
                allProblems.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error getting total problems count", e)
            0
        }
    }


    private fun notifyAppWidgetViewDataChanged(context: Context, appWidgetId: Int, viewId: Int) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, viewId)
            Log.d("ProblemWidgetService", "Notified view data changed for widget $appWidgetId, view $viewId")
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error notifying view data changed", e)
        }
    }

    private fun getEmptyRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val widgetTheme = com.itsoul.zbxclient.util.ThemeManager.getWidgetTheme(context)
        val layoutRes = when (widgetTheme) {
            com.itsoul.zbxclient.util.WidgetTheme.DARK -> R.layout.widget_problem_empty_dark
            com.itsoul.zbxclient.util.WidgetTheme.LIGHT -> R.layout.widget_problem_empty
            else -> R.layout.widget_problem_empty
        }

        return RemoteViews(context.packageName, layoutRes).apply {
            val configureText = com.itsoul.zbxclient.util.WidgetLocaleManager.getLocalizedString(context, "configure_widget")

            val configIntent = Intent(context, ProblemWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_empty_container, configPendingIntent)
        }
    }

    private fun getServerName(context: Context, serverId: Long): String? {
        return try {
            ServerCacheManager.getServerName(context, serverId) ?: "Server $serverId"
        } catch (e: Exception) {
            Log.e("ProblemWidgetService", "Error getting server name", e)
            "Server $serverId"
        }
    }

    private fun getAppWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ProblemWidget::class.java)
        return appWidgetManager.getAppWidgetIds(componentName)
    }
}