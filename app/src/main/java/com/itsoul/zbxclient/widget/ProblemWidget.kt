package com.itsoul.zbxclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.itsoul.zbxclient.R
import android.util.Log

class ProblemWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Запускаем сервис для обновления всех экземпляров виджета
        val intent = Intent(context, ProblemWidgetService::class.java)
        intent.action = ProblemWidgetService.ACTION_UPDATE_ALL_WIDGETS
        ProblemWidgetService.enqueueWork(context, intent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                // существующая логика
            }
            "com.itsoul.zbxclient.LANGUAGE_CHANGED" -> {
                // Принудительно обновляем все виджеты при смене языка
                val serviceIntent = Intent(context, ProblemWidgetService::class.java)
                serviceIntent.action = ProblemWidgetService.ACTION_UPDATE_ALL_WIDGETS
                ProblemWidgetService.enqueueWork(context, serviceIntent)
            }
            ACTION_TOGGLE_ACK -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Log.d("ProblemWidget", "ACTION_TOGGLE_ACK received for widget: $appWidgetId")
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val serviceIntent = Intent(context, ProblemWidgetService::class.java)
                    serviceIntent.action = ProblemWidgetService.ACTION_TOGGLE_ACK
                    serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    ProblemWidgetService.enqueueWork(context, serviceIntent)
                }
            }
            ACTION_TOGGLE_MAINT -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Log.d("ProblemWidget", "ACTION_TOGGLE_MAINT received for widget: $appWidgetId")
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val serviceIntent = Intent(context, ProblemWidgetService::class.java)
                    serviceIntent.action = ProblemWidgetService.ACTION_TOGGLE_MAINT
                    serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    ProblemWidgetService.enqueueWork(context, serviceIntent)
                }
            }
            ACTION_FORCE_REFRESH -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Log.d("ProblemWidget", "ACTION_FORCE_REFRESH received for widget: $appWidgetId")
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val serviceIntent = Intent(context, ProblemWidgetService::class.java)
                    serviceIntent.action = ProblemWidgetService.ACTION_FORCE_REFRESH
                    serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    ProblemWidgetService.enqueueWork(context, serviceIntent)
                }
            }
            else -> {
                // существующая логика
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.itsoul.zbxclient.ACTION_REFRESH_WIDGET"
        const val ACTION_TOGGLE_ACK = "com.itsoul.zbxclient.ACTION_TOGGLE_ACK"
        const val ACTION_TOGGLE_MAINT = "com.itsoul.zbxclient.ACTION_TOGGLE_MAINT"
        const val ACTION_FORCE_REFRESH = "com.itsoul.zbxclient.ACTION_FORCE_REFRESH"
    }
}