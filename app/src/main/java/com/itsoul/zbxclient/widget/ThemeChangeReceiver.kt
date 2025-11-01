package com.itsoul.zbxclient.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ThemeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.itsoul.zbxclient.THEME_CHANGED") {
            Log.d("ThemeChangeReceiver", "Received theme change broadcast - updating all widgets")

            // Обновляем все виджеты
            val updateIntent = Intent(context, ProblemWidgetService::class.java)
            updateIntent.action = ProblemWidgetService.ACTION_UPDATE_ALL_WIDGETS
            ProblemWidgetService.enqueueWork(context, updateIntent)
        }
    }
}