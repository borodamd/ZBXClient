package com.itsoul.zbxclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.JobIntentService
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixProblem
import com.itsoul.zbxclient.util.ServerCacheManager
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProblemWidgetService : JobIntentService() {

    companion object {
        const val ACTION_UPDATE_ALL_WIDGETS = "update_all_widgets"
        const val ACTION_UPDATE_WIDGET = "update_widget"
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
        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateAppWidget(this, appWidgetId)
                }
            }
            ACTION_UPDATE_ALL_WIDGETS -> {
                updateAllAppWidgets(this)
            }
        }
    }

    private fun updateAllAppWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = getAppWidgetIds(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetId, appWidgetManager)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager? = null
    ) {
        val manager = appWidgetManager ?: AppWidgetManager.getInstance(context)

        // 1. Получаем настройки для этого appWidgetId
        val prefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        val serverId = prefs.getLong("$PREF_SERVER_ID$appWidgetId", -1)
        val showAck = prefs.getBoolean("$PREF_SHOW_ACK$appWidgetId", false)
        val showMaint = prefs.getBoolean("$PREF_SHOW_MAINT$appWidgetId", false)

        if (serverId == -1L) {
            // Виджет не сконфигурирован, показываем заглушку
            val views = getEmptyRemoteViews(context, appWidgetId)
            manager.updateAppWidget(appWidgetId, views)
            return
        }

        // 2. Загружаем данные из кеша для этого сервера
        val problems = loadProblemsFromCache(context, serverId, appWidgetId)

        // 3. Строим RemoteViews и обновляем виджет
        val views = getRemoteViews(context, appWidgetId, serverId, problems, showAck, showMaint)
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun loadProblemsFromCache(context: Context, serverId: Long, appWidgetId: Int): List<ZabbixProblem> {
        return try {
            val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_NAME, Context.MODE_PRIVATE)
            val showAck = widgetPrefs.getBoolean("$PREF_SHOW_ACK$appWidgetId", false)
            val showMaint = widgetPrefs.getBoolean("$PREF_SHOW_MAINT$appWidgetId", false)

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

    private fun getRemoteViews(
        context: Context,
        appWidgetId: Int,
        serverId: Long,
        problems: List<ZabbixProblem>,
        showAck: Boolean,
        showMaint: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_problem).apply {
            // Заголовок
            val serverName = getServerName(context, serverId)
            setTextViewText(R.id.widget_title, serverName ?: "Unknown Server")
            setTextViewText(R.id.widget_last_update, "Last update: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")

            // Вместо CheckBox используем TextView для отображения состояния фильтров
            setTextViewText(R.id.widget_ack_status, if (showAck) "Ack: ON" else "Ack: OFF")
            setTextViewText(R.id.widget_maint_status, if (showMaint) "Maint: ON" else "Maint: OFF")

            // Обработчики нажатий на кнопку Refresh
            val refreshIntent = Intent(context, ProblemWidget::class.java).apply {
                action = ProblemWidget.ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

            // Список проблем (используем RemoteViewsService для списка)
            val adapterIntent = Intent(context, ProblemWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_SERVER_ID, serverId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            setRemoteAdapter(R.id.widget_problems_list, adapterIntent)

            // Устанавливаем пустое view
            setEmptyView(R.id.widget_problems_list, R.id.widget_empty_view)
        }
    }

    private fun getEmptyRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_problem_empty).apply {
            // Настройка Intent для запуска конфигурационной активности при нажатии на "пустой" виджет
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
            "Server $serverId"
        }
    }

    private fun getAppWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ProblemWidget::class.java)
        return appWidgetManager.getAppWidgetIds(componentName)
    }
}