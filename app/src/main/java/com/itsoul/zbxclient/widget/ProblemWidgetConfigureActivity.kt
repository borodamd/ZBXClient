package com.itsoul.zbxclient.widget

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.itsoul.zbxclient.PreferencesManager
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixServer
import com.itsoul.zbxclient.util.ServerCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class ProblemWidgetConfigureActivity : Activity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var serversSpinner: Spinner
    private lateinit var intervalEditText: EditText
    private lateinit var ackCheckBox: CheckBox
    private lateinit var maintCheckBox: CheckBox
    private lateinit var preferencesManager: PreferencesManager

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_configure)

        // Устанавливаем результат по умолчанию (отмена)
        setResult(RESULT_CANCELED)

        // Получаем appWidgetId из интента
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        preferencesManager = PreferencesManager(this)
        serversSpinner = findViewById(R.id.widget_config_server_spinner)
        intervalEditText = findViewById(R.id.widget_config_interval_edit_text)
        ackCheckBox = findViewById(R.id.widget_config_ack_checkbox)
        maintCheckBox = findViewById(R.id.widget_config_maint_checkbox)

        // Показываем прогресс или сообщение о загрузке
        showLoadingState()
        loadServersAsync()
    }

    private fun showLoadingState() {
        // Можно добавить ProgressBar или сообщение "Loading servers..."
        Toast.makeText(this, "Loading servers...", Toast.LENGTH_SHORT).show()
    }

    private fun loadServersAsync() {
        loadJob = coroutineScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) {
                    // Загружаем серверы в фоновом потоке
                    preferencesManager.getServers()
                }

                // Обрабатываем результат в основном потоке
                servers.collect { serverList ->
                    if (serverList.isEmpty()) {
                        Toast.makeText(
                            this@ProblemWidgetConfigureActivity,
                            "No servers configured. Please add servers first.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    } else {
                        setupUI(serverList)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ProblemWidgetConfigureActivity,
                    "Error loading servers: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun setupUI(servers: List<ZabbixServer>) {
        // Создаем список имен серверов для Spinner
        val serverNames = mutableListOf<String>()
        for (server in servers) {
            serverNames.add(server.name)
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            serverNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serversSpinner.adapter = adapter

        setupSaveButton()
    }

    private fun setupSaveButton() {
        val saveButton: Button = findViewById(R.id.widget_config_save_button)
        saveButton.setOnClickListener {
            val selectedServerPosition = serversSpinner.selectedItemPosition

            // Получаем серверы синхронно для сохранения
            loadJob?.cancel()
            coroutineScope.launch {
                try {
                    val servers = withContext(Dispatchers.IO) {
                        preferencesManager.getServers().first()
                    }

                    if (selectedServerPosition >= 0 && selectedServerPosition < servers.size) {
                        val selectedServer = servers[selectedServerPosition]
                        val intervalText = intervalEditText.text.toString()
                        val interval = intervalText.toIntOrNull() ?: 5

                        if (interval < 5) {
                            Toast.makeText(this@ProblemWidgetConfigureActivity, "Interval must be at least 5 minutes", Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }

                        saveWidgetConfig(selectedServer, interval, ackCheckBox.isChecked, maintCheckBox.isChecked)
                        updateWidget()

                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, resultValue)
                        finish()
                    } else {
                        Toast.makeText(this@ProblemWidgetConfigureActivity, "Please select a server", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ProblemWidgetConfigureActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveWidgetConfig(
        server: ZabbixServer,
        interval: Int,
        showAck: Boolean,
        showMaint: Boolean
    ) {
        val prefs = getSharedPreferences(ProblemWidgetService.WIDGET_PREF_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putLong("${ProblemWidgetService.PREF_SERVER_ID}$appWidgetId", server.id)
            putInt("${ProblemWidgetService.PREF_UPDATE_INTERVAL}$appWidgetId", interval)
            putBoolean("${ProblemWidgetService.PREF_SHOW_ACK}$appWidgetId", showAck)
            putBoolean("${ProblemWidgetService.PREF_SHOW_MAINT}$appWidgetId", showMaint)
            apply()
        }
    }

    private fun updateWidget() {
        // Обновляем кэш серверов в фоне
        coroutineScope.launch(Dispatchers.IO) {
            ServerCacheManager.updateServerCache(this@ProblemWidgetConfigureActivity)
        }

        // Обновляем виджет сразу
        val intent = Intent(this, ProblemWidgetService::class.java)
        intent.action = ProblemWidgetService.ACTION_UPDATE_WIDGET
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        ProblemWidgetService.enqueueWork(this, intent)

        // Настраиваем периодическое обновление
        setupPeriodicUpdates(appWidgetId)
    }

    private fun setupPeriodicUpdates(appWidgetId: Int) {
        try {
            val prefs = getSharedPreferences(ProblemWidgetService.WIDGET_PREF_NAME, Context.MODE_PRIVATE)
            val interval = prefs.getInt("${ProblemWidgetService.PREF_UPDATE_INTERVAL}$appWidgetId", 5)

            val intent = Intent(this, ProblemWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

            // Получаем все ID виджетов для обновления
            val allWidgetIds = getAppWidgetIds()
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intervalMs = interval * 60 * 1000L // минуты в миллисекунды

            // Используем AlarmManager для точного расписания
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                pendingIntent
            )
        } catch (e: Exception) {
            // Игнорируем ошибки настройки периодического обновления
        }
    }

    private fun getAppWidgetIds(): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, ProblemWidget::class.java)
        return appWidgetManager.getAppWidgetIds(componentName)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }
}