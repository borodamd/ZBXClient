package com.itsoul.zbxclient.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.itsoul.zbxclient.ZabbixProblem
import com.itsoul.zbxclient.R // Добавляем импорт для ресурсов
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProblemWidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var problems: List<ZabbixProblem> = emptyList()
    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val serverId: Long = intent.getLongExtra(ProblemWidgetService.EXTRA_SERVER_ID, -1)

    override fun onCreate() {
        Log.d("ProblemWidgetRemoteViewsFactory", "onCreate for widget: $appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d("ProblemWidgetRemoteViewsFactory", "=== onDataSetChanged START ===")
        Log.d("ProblemWidgetRemoteViewsFactory", "Widget: $appWidgetId, Server: $serverId")

        try {
            problems = loadProblems()
            Log.d("ProblemWidgetRemoteViewsFactory", "Successfully loaded ${problems.size} problems")

            // Логируем первые 3 проблемы для отладки
            problems.take(3).forEachIndexed { index, problem ->
                Log.d("ProblemWidgetRemoteViewsFactory", "Problem $index: ${problem.hostName} - ${problem.name} (severity: ${problem.severity})")
            }
        } catch (e: Exception) {
            Log.e("ProblemWidgetRemoteViewsFactory", "Error loading problems", e)
            problems = emptyList()
        }

        Log.d("ProblemWidgetRemoteViewsFactory", "=== onDataSetChanged END ===")
    }

    override fun onDestroy() {
        Log.d("ProblemWidgetRemoteViewsFactory", "onDestroy for widget: $appWidgetId")
    }

    override fun getCount(): Int {
        val count = problems.size
        Log.d("ProblemWidgetRemoteViewsFactory", "getCount: $count")
        return count
    }

    override fun getViewAt(position: Int): RemoteViews {
        Log.d("ProblemWidgetRemoteViewsFactory", "getViewAt position: $position")

        if (position >= problems.size) {
            Log.e("ProblemWidgetRemoteViewsFactory", "Position $position out of bounds for ${problems.size} problems")
            return createEmptyView()
        }

        val problem = problems[position]
        Log.d("ProblemWidgetRemoteViewsFactory", "Creating view for: ${problem.hostName} - ${problem.name}")

        return try {
            val remoteViews = createProblemView(problem)
            Log.d("ProblemWidgetRemoteViewsFactory", "Successfully created RemoteViews for position $position")
            remoteViews
        } catch (e: Exception) {
            Log.e("ProblemWidgetRemoteViewsFactory", "Error creating RemoteViews for position $position", e)
            createEmptyView()
        }
    }

    override fun getLoadingView(): RemoteViews {
        Log.d("ProblemWidgetRemoteViewsFactory", "getLoadingView")
        return createEmptyView()
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    private fun loadProblems(): List<ZabbixProblem> {
        return runBlocking {
            try {
                Log.d("ProblemWidgetRemoteViewsFactory", "Loading problems for server: $serverId")
                val cachePrefs =
                    context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
                val problemsJson = cachePrefs.getString("problems_$serverId", null)

                if (problemsJson != null) {
                    val allProblems = Json.decodeFromString<List<ZabbixProblem>>(problemsJson)
                    Log.d(
                        "ProblemWidgetRemoteViewsFactory",
                        "Found ${allProblems.size} problems in cache"
                    )

                    // Применяем фильтры виджета
                    val widgetPrefs = context.getSharedPreferences(
                        ProblemWidgetService.WIDGET_PREF_NAME,
                        Context.MODE_PRIVATE
                    )
                    val showAck = widgetPrefs.getBoolean(
                        "${ProblemWidgetService.PREF_SHOW_ACK}$appWidgetId",
                        false
                    )
                    val showMaint = widgetPrefs.getBoolean(
                        "${ProblemWidgetService.PREF_SHOW_MAINT}$appWidgetId",
                        false
                    )

                    Log.d(
                        "ProblemWidgetRemoteViewsFactory",
                        "Filters - showAck: $showAck, showMaint: $showMaint"
                    )

                    val filteredProblems = allProblems.filter { problem ->
                        val ackFilter = showAck || problem.acknowledged != "1"
                        val maintFilter = showMaint || problem.suppressed != "1"
                        ackFilter && maintFilter
                    }

                    Log.d(
                        "ProblemWidgetRemoteViewsFactory",
                        "After filtering: ${filteredProblems.size} problems"
                    )
                    filteredProblems
                } else {
                    Log.d("ProblemWidgetRemoteViewsFactory", "No cached problems found")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProblemWidgetRemoteViewsFactory", "Error loading problems from cache", e)
                emptyList()
            }
        }
    }

    private fun createProblemView(problem: ZabbixProblem): RemoteViews {
        val widgetTheme = com.itsoul.zbxclient.util.ThemeManager.getWidgetTheme(context)
        val layoutRes = when (widgetTheme) {
            com.itsoul.zbxclient.util.WidgetTheme.DARK -> R.layout.widget_problem_item_dark
            com.itsoul.zbxclient.util.WidgetTheme.LIGHT -> R.layout.item_widget_problem
            else -> R.layout.item_widget_problem
        }

        Log.d("ProblemWidgetRemoteViewsFactory", "Creating view with layout: $layoutRes")

        return RemoteViews(context.packageName, layoutRes).apply {
            // Устанавливаем основные данные проблемы
            setTextViewText(R.id.item_problem_name, problem.name)
            setTextViewText(R.id.item_problem_host, problem.hostName)

            // Устанавливаем текст severity
            val severityText = getSeverityText(problem.severity)
            setTextViewText(R.id.item_problem_severity, severityText)

            // Устанавливаем цвет severity
            val severityColor = getSeverityColor(problem.severity)
            setInt(R.id.item_problem_severity, "setBackgroundColor", severityColor)

            // Для темной темы устанавливаем дополнительные поля
            if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK) {
                // Устанавливаем время (если есть в данных)
                val timeText = formatTime(problem.clock)
                setTextViewText(R.id.widget_item_time, timeText)

                // Устанавливаем длительность (можно вычислить)
                val durationText = calculateDuration(problem.clock)
                setTextViewText(R.id.widget_item_duration, durationText)
            }

            Log.d(
                "ProblemWidgetRemoteViewsFactory",
                "Created view for: ${problem.hostName} - ${problem.name}"
            )
        }
    }

    private fun createEmptyView(): RemoteViews {
        val widgetTheme = com.itsoul.zbxclient.util.ThemeManager.getWidgetTheme(context)
        val layoutRes = when (widgetTheme) {
            com.itsoul.zbxclient.util.WidgetTheme.DARK -> R.layout.widget_problem_item_dark
            com.itsoul.zbxclient.util.WidgetTheme.LIGHT -> R.layout.item_widget_problem
            else -> R.layout.item_widget_problem
        }

        return RemoteViews(context.packageName, layoutRes).apply {
            setTextViewText(R.id.item_problem_name, "No active problems")
            setTextViewText(R.id.item_problem_host, "All systems operational")
            setTextViewText(R.id.item_problem_severity, "OK")
            setInt(
                R.id.item_problem_severity, "setBackgroundColor",
                context.resources.getColor(android.R.color.holo_green_dark, null)
            )

            if (widgetTheme == com.itsoul.zbxclient.util.WidgetTheme.DARK) {
                setTextViewText(R.id.widget_item_time, "")
                setTextViewText(R.id.widget_item_duration, "")
            }
        }
    }

    private fun getSeverityText(severity: String): String {
        return when (severity) {
            "0" -> "Not classified"
            "1" -> "Information"
            "2" -> "Warning"
            "3" -> "Average"
            "4" -> "High"
            "5" -> "Disaster"
            else -> "Unknown"
        }
    }

    private fun getSeverityColor(severity: String): Int {
        return when (severity) {
            "0" -> context.resources.getColor(
                android.R.color.darker_gray,
                null
            )    // Not classified
            "1" -> context.resources.getColor(android.R.color.holo_green_light, null) // Information
            "2" -> context.resources.getColor(android.R.color.holo_blue_light, null)  // Warning
            "3" -> context.resources.getColor(android.R.color.holo_orange_light, null) // Average
            "4" -> context.resources.getColor(android.R.color.holo_red_light, null)   // High
            "5" -> context.resources.getColor(android.R.color.holo_red_dark, null)    // Disaster
            else -> context.resources.getColor(android.R.color.darker_gray, null)
        }
    }

    private fun formatTime(clock: String?): String {
        return if (!clock.isNullOrEmpty()) {
            try {
                val timestamp = clock.toLong() * 1000
                val date = Date(timestamp)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                "--:--"
            }
        } else {
            "--:--"
        }
    }

    private fun calculateDuration(clock: String?): String {
        return if (!clock.isNullOrEmpty()) {
            try {
                val problemTime = clock.toLong() * 1000
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - problemTime

                when {
                    duration < 60000 -> "${duration / 1000}s" // seconds
                    duration < 3600000 -> "${duration / 60000}m" // minutes
                    duration < 86400000 -> "${duration / 3600000}h" // hours
                    else -> "${duration / 86400000}d" // days
                }
            } catch (e: Exception) {
                "?"
            }
        } else {
            "?"
        }
    }
}