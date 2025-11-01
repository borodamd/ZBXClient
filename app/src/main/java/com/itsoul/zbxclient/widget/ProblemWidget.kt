package com.itsoul.zbxclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.itsoul.zbxclient.R

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
            else -> {
                // существующая логика
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.itsoul.zbxclient.ACTION_REFRESH_WIDGET"
    }
}