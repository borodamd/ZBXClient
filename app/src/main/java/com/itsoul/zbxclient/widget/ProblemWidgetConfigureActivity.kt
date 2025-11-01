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
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.itsoul.zbxclient.PreferencesManager
import com.itsoul.zbxclient.R
import com.itsoul.zbxclient.ZabbixProblem
import com.itsoul.zbxclient.ZabbixRepository
import com.itsoul.zbxclient.ZabbixServer
import com.itsoul.zbxclient.util.ServerCacheManager
import com.itsoul.zbxclient.util.ThemeManager // Добавьте этот импорт
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private var serversList: List<ZabbixServer> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему ДО setContentView
        applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_configure)
        Log.d("ProblemWidgetConfig", "onCreate")

        // Устанавливаем результат по умолчанию (отмена)
        setResult(RESULT_CANCELED)

        // Получаем appWidgetId из интента
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("ProblemWidgetConfig", "Invalid appWidgetId")
            finish()
            return
        }

        preferencesManager = PreferencesManager(this)
        serversSpinner = findViewById(R.id.widget_config_server_spinner)
        intervalEditText = findViewById(R.id.widget_config_interval_edit_text)
        ackCheckBox = findViewById(R.id.widget_config_ack_checkbox)
        maintCheckBox = findViewById(R.id.widget_config_maint_checkbox)

        loadServersAsync()
    }

    // Метод для применения темы
    private fun applyTheme() {
        val currentTheme = ThemeManager.getCurrentTheme(this)
        ThemeManager.applyTheme(currentTheme)

        // Используем стандартные темы Android
        when (currentTheme) {
            com.itsoul.zbxclient.AppTheme.LIGHT -> setTheme(android.R.style.Theme_Material_Light_DarkActionBar)
            com.itsoul.zbxclient.AppTheme.DARK -> setTheme(android.R.style.Theme_Material)
            com.itsoul.zbxclient.AppTheme.SYSTEM -> {
                // Для системной темы определяем текущий режим
                val isDarkTheme = when (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                    android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
                    else -> false
                }
                if (isDarkTheme) {
                    setTheme(android.R.style.Theme_Material)
                } else {
                    setTheme(android.R.style.Theme_Material_Light_DarkActionBar)
                }
            }
        }
    }
    private fun loadServersAsync() {
        loadJob = coroutineScope.launch {
            try {
                Log.d("ProblemWidgetConfig", "Loading servers...")
                serversList = withContext(Dispatchers.IO) {
                    // Используем first() чтобы получить первое значение из Flow
                    preferencesManager.getServers().first()
                }

                // Обрабатываем результат в основном потоке
                Log.d("ProblemWidgetConfig", "Loaded ${serversList.size} servers")
                if (serversList.isEmpty()) {
                    Toast.makeText(
                        this@ProblemWidgetConfigureActivity,
                        "No servers configured. Please add servers first.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    setupUI()
                }
            } catch (e: Exception) {
                Log.e("ProblemWidgetConfig", "Error loading servers", e)
                Toast.makeText(
                    this@ProblemWidgetConfigureActivity,
                    "Error loading servers: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun setupUI() {
        // Создаем список имен серверов для Spinner
        val serverNames = mutableListOf<String>()
        for (server in serversList) {
            serverNames.add(server.name)
        }

        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            serverNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serversSpinner.adapter = adapter

        // Устанавливаем первый сервер по умолчанию
        if (serverNames.isNotEmpty()) {
            serversSpinner.setSelection(0)
        }

        setupSaveButton()
        Log.d("ProblemWidgetConfig", "UI setup complete with ${serverNames.size} servers")
    }

    private fun setupSaveButton() {
        val saveButton: Button = findViewById(R.id.widget_config_save_button)
        saveButton.setOnClickListener {
            Log.d("ProblemWidgetConfig", "Save button clicked")

            val selectedServerPosition = serversSpinner.selectedItemPosition
            val intervalText = intervalEditText.text.toString()
            val interval = intervalText.toIntOrNull() ?: 5

            if (interval < 5) {
                Toast.makeText(this, "Interval must be at least 5 minutes", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (selectedServerPosition >= 0 && selectedServerPosition < serversList.size) {
                val selectedServer = serversList[selectedServerPosition]
                Log.d("ProblemWidgetConfig", "Selected server: ${selectedServer.name}, ID: ${selectedServer.id}")

                saveWidgetConfig(selectedServer, interval, ackCheckBox.isChecked, maintCheckBox.isChecked)

                // Очищаем тестовые данные и загружаем реальные
                clearTestData(selectedServer.id)
                updateWidget(selectedServer.id)

                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            } else {
                Toast.makeText(this@ProblemWidgetConfigureActivity, "Please select a server", Toast.LENGTH_SHORT).show()
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
        Log.d("ProblemWidgetConfig", "Widget config saved: serverId=${server.id}, interval=$interval, showAck=$showAck, showMaint=$showMaint")
    }

    // Очищаем тестовые данные из кэша
    private fun clearTestData(serverId: Long) {
        val cachePrefs = getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
        cachePrefs.edit().remove("problems_$serverId").apply()
        Log.d("ProblemWidgetConfig", "Cleared test data for server $serverId")
    }

    private fun updateWidget(serverId: Long) {
        Log.d("ProblemWidgetConfig", "Updating widget with serverId: $serverId")

        // Обновляем кэш серверов и загружаем реальные проблемы в фоне
        coroutineScope.launch(Dispatchers.IO) {
            ServerCacheManager.updateServerCache(this@ProblemWidgetConfigureActivity)
            loadRealProblems(serverId)
        }

        // Обновляем виджет сразу
        val intent = Intent(this, ProblemWidgetService::class.java)
        intent.action = ProblemWidgetService.ACTION_UPDATE_WIDGET
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        ProblemWidgetService.enqueueWork(this, intent)

        // Настраиваем периодическое обновление
        setupPeriodicUpdates(appWidgetId)
    }

    // Метод для загрузки реальных проблем с сервера
    private suspend fun loadRealProblems(serverId: Long) {
        try {
            Log.d("ProblemWidgetConfig", "Loading REAL problems for server $serverId")

            val server = serversList.find { it.id == serverId }
            if (server == null) {
                Log.e("ProblemWidgetConfig", "Server $serverId not found in servers list")
                return
            }

            // Используем ZabbixRepository для загрузки реальных данных
            val repository = ZabbixRepository()
            val realProblems = repository.getProblemsWithHostNames(server.url, server.apiKey)

            Log.d("ProblemWidgetConfig", "Loaded ${realProblems.size} REAL problems from server")

            // Кэшируем реальные проблемы
            cacheRealProblems(serverId, realProblems)

        } catch (e: Exception) {
            Log.e("ProblemWidgetConfig", "Error loading real problems: ${e.message}", e)
            // В случае ошибки оставляем кэш пустым - виджет покажет "No Data"
        }
    }

    // Кэшируем реальные проблемы
    private fun cacheRealProblems(serverId: Long, problems: List<ZabbixProblem>) {
        try {
            val prefs = getSharedPreferences("problems_cache", Context.MODE_PRIVATE)
            val json = Json.encodeToString(problems)
            prefs.edit().putString("problems_$serverId", json).apply()
            Log.d("ProblemWidgetConfig", "Cached ${problems.size} REAL problems for server $serverId")
        } catch (e: Exception) {
            Log.e("ProblemWidgetConfig", "Error caching real problems", e)
        }
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
            Log.d("ProblemWidgetConfig", "Periodic updates configured: interval=$interval minutes")
        } catch (e: Exception) {
            Log.e("ProblemWidgetConfig", "Error setting up periodic updates", e)
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
        Log.d("ProblemWidgetConfig", "onDestroy")
    }
}