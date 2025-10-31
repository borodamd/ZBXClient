package com.itsoul.zbxclient.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixProblem
import kotlinx.serialization.json.Json

class ProblemWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val serverId = intent.getLongExtra(ProblemWidgetService.EXTRA_SERVER_ID, -1)

        return ProblemRemoteViewsFactory(applicationContext, appWidgetId, serverId)
    }
}

class ProblemRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int,
    private val serverId: Long
) : RemoteViewsService.RemoteViewsFactory {

    private var problems: List<ZabbixProblem> = emptyList()

    override fun onCreate() {
        // Инициализация
    }

    override fun onDataSetChanged() {
        // Загружаем данные из SharedPreferences (кеша) когда виджет обновляется
        problems = loadProblemsFromCache(context, serverId, appWidgetId)
    }

    override fun getViewAt(position: Int): RemoteViews {
        val problem = problems[position]

        val remoteViews = RemoteViews(context.packageName, R.layout.item_widget_problem)

        // Устанавливаем текст
        remoteViews.setTextViewText(R.id.item_problem_name, problem.name ?: "Unknown problem")
        remoteViews.setTextViewText(R.id.item_problem_host, problem.hostName ?: "Unknown host")

        // Преобразуем числовой severity в текстовое название и устанавливаем
        val severityText = getSeverityText(problem.severity)
        remoteViews.setTextViewText(R.id.item_problem_severity, severityText)

        // Настраиваем цвет в зависимости от severity
        val severityBg = getSeverityBackground(problem.severity)
        remoteViews.setInt(R.id.item_problem_severity, "setBackgroundResource", severityBg)

        return remoteViews
    }

    override fun getCount(): Int = problems.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { }
    override fun getLoadingView(): RemoteViews? = null

    // Функция для преобразования числового severity в текстовое название
    private fun getSeverityText(severity: String?): String {
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

    // Функция для получения фона в зависимости от severity
    private fun getSeverityBackground(severity: String?): Int {
        return when (severity) {
            "2" -> R.drawable.severity_warning_bg      // Warning
            "3" -> R.drawable.severity_average_bg      // Average
            "4" -> R.drawable.severity_high_bg         // High
            "5" -> R.drawable.severity_disaster_bg     // Disaster
            else -> R.drawable.severity_information_bg // Information/Not classified
        }
    }
}

// Функция для загрузки проблем из кеша
private fun loadProblemsFromCache(context: Context, serverId: Long, appWidgetId: Int): List<ZabbixProblem> {
    return try {
        // Получаем настройки фильтров для этого виджета
        val widgetPrefs = context.getSharedPreferences(ProblemWidgetService.WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val showAck = widgetPrefs.getBoolean("${ProblemWidgetService.PREF_SHOW_ACK}$appWidgetId", false)
        val showMaint = widgetPrefs.getBoolean("${ProblemWidgetService.PREF_SHOW_MAINT}$appWidgetId", false)

        // Загружаем кеш проблем из SharedPreferences
        val cachePrefs = context.getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
        val problemsJson = cachePrefs.getString("problems_$serverId", null)

        if (problemsJson != null) {
            val allProblems = Json.decodeFromString<List<ZabbixProblem>>(problemsJson)

            // Фильтруем проблемы согласно настройкам виджета
            allProblems.filter { problem ->
                val ackFilter = showAck || problem.acknowledged != "1"
                val maintFilter = showMaint || problem.suppressed != "1"
                ackFilter && maintFilter
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}