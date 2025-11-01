package com.itsoul.zbxclient.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixProblem
import kotlinx.serialization.json.Json
import com.itsoul.zbxclient.util.ThemeManager
import com.itsoul.zbxclient.util.WidgetTheme
import android.util.Log



class ProblemWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d("ProblemWidgetRemoteViewsService", "onGetViewFactory called")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val serverId = intent.getLongExtra(ProblemWidgetService.EXTRA_SERVER_ID, -1)
        Log.d("ProblemWidgetRemoteViewsService", "Creating factory for widget: $appWidgetId, server: $serverId")
        return ProblemWidgetRemoteViewsFactory(this.applicationContext, intent)
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

    override fun getViewAt(position: Int): RemoteViews? {
        return try {
            val problem = problems[position]
            val widgetTheme = com.itsoul.zbxclient.util.ThemeManager.getWidgetTheme(context)
            val layoutRes = when (widgetTheme) {
                com.itsoul.zbxclient.util.WidgetTheme.DARK -> R.layout.widget_problem_item_dark
                com.itsoul.zbxclient.util.WidgetTheme.LIGHT -> R.layout.item_widget_problem // Исправлено на правильное имя
                else -> R.layout.item_widget_problem // Добавляем else branch
            }

            RemoteViews(context.packageName, layoutRes).apply {
                setTextViewText(R.id.item_problem_name, problem.name)
                setTextViewText(R.id.item_problem_host, problem.hostName)

                // Локализация severity если нужно
                val severityText = getLocalizedSeverity(problem.severity)
                setTextViewText(R.id.item_problem_severity, severityText)
            }
        } catch (e: Exception) {
            null
        }
    }
    private fun getLocalizedSeverity(severity: String): String {
        // Пока возвращаем как есть, можно добавить локализацию позже
        return severity
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

    // Функция для получения цвета severity (для динамического создания drawable)
    private fun getSeverityColor(severity: String?): Int {
        return when (severity) {
            "0" -> Color.parseColor("#97AAB3") // Not classified - серый
            "1" -> Color.parseColor("#7499FF") // Information - синий
            "2" -> Color.parseColor("#FFC859") // Warning - желтый
            "3" -> Color.parseColor("#FFA059") // Average - оранжевый
            "4" -> Color.parseColor("#E97659") // High - красно-оранжевый
            "5" -> Color.parseColor("#E45959") // Disaster - красный
            else -> Color.parseColor("#97AAB3") // По умолчанию - серый
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