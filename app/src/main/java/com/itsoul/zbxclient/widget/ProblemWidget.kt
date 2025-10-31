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
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Запускаем сервис для обновления одного виджета
                val serviceIntent = Intent(context, ProblemWidgetService::class.java)
                serviceIntent.action = ProblemWidgetService.ACTION_UPDATE_WIDGET
                serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                ProblemWidgetService.enqueueWork(context, serviceIntent)
            }
        } else if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            // Обработка системного обновления
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (appWidgetIds != null) {
                onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.itsoul.zbxclient.ACTION_REFRESH_WIDGET"
    }
}