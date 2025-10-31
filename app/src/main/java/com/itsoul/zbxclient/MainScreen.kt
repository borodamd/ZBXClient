// MainScreen.kt
package com.itsoul.zbxclient

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import java.text.SimpleDateFormat
import java.util.*

// Иконки:
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    servers: List<ZabbixServer>,
    onSettingsClick: () -> Unit,
    preferencesManager: PreferencesManager
) {
    val coroutineScope = rememberCoroutineScope()

    // Загружаем сохраненное состояние
    val dashboardState by preferencesManager.getDashboardState().collectAsState(initial = DashboardState())

    // Локальное состояние
    var selectedServer by remember { mutableStateOf<ZabbixServer?>(null) }
    var triggers by remember { mutableStateOf(emptyList<ZabbixTrigger>()) }

    // Состояние для API
    var allProblems by remember { mutableStateOf<List<ZabbixProblem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableStateOf<String?>(null) }
    val zabbixRepository = remember { ZabbixRepository() }

    // Функция для получения текущего времени
    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    // Функция для обновления данных
    fun refreshData() {
        if (selectedServer == null) return

        isLoading = true
        allProblems = emptyList()

        coroutineScope.launch {
            try {
                val result = zabbixRepository.getProblemsWithHostNames(selectedServer!!.url, selectedServer!!.apiKey)
                allProblems = result
                lastUpdateTime = getCurrentTime()
            } catch (e: Exception) {
                // Можно добавить обработку ошибок, если нужно
                println("API Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Функция для мгновенного обновления данных (без ожидания 30 секунд)
    fun forceRefreshData() {
        println("🔄 forceRefreshData called")

        if (selectedServer == null) {
            println("❌ selectedServer is null")
            return
        }

        coroutineScope.launch {
            isLoading = true
            try {
                println("🔄 Fetching fresh data...")
                val result = zabbixRepository.getProblemsWithHostNames(
                    selectedServer!!.url,
                    selectedServer!!.apiKey
                )
                println("✅ forceRefreshData: received ${result.size} problems")
                allProblems = result
                lastUpdateTime = getCurrentTime()
                println("✅ forceRefreshData: UI should update now")
            } catch (e: Exception) {
                println("❌ Force refresh error: ${e.message}")
            } finally {
                isLoading = false
                println("✅ forceRefreshData: completed")
            }
        }
    }

    // Функция для подтверждения проблемы
    fun acknowledgeProblem(eventId: String, isAcknowledge: Boolean) {
        println("🔄 acknowledgeProblem: eventId=$eventId, isAcknowledge=$isAcknowledge")

        if (selectedServer == null) {
            println("❌ selectedServer is null")
            return
        }

        coroutineScope.launch {
            try {
                println("🔄 Calling acknowledgeEvent...")
                val result = zabbixRepository.acknowledgeEvent(
                    serverUrl = selectedServer!!.url,
                    apiKey = selectedServer!!.apiKey,
                    eventId = eventId,
                    isAcknowledge = isAcknowledge
                )

                println("✅ acknowledgeEvent result: $result")

                // Если успешно, обновляем данные МГНОВЕННО с помощью forceRefreshData
                if (result) {
                    println("🔄 Calling forceRefreshData...")
                    forceRefreshData()
                } else {
                    println("❌ acknowledgeEvent returned false")
                }
            } catch (e: Exception) {
                println("❌ Acknowledge error: ${e.message}")
            }
        }
    }



    // Автоматическое обновление каждые 30 секунд
    LaunchedEffect(selectedServer) {
        while (true) {
            if (selectedServer != null) {
                refreshData()
            }
            delay(30000) // 30 секунд
        }
    }

    // Обновляем при изменении выбранного сервера
    LaunchedEffect(selectedServer) {
        if (selectedServer != null) {
            refreshData()
        }
    }

    // Фильтруем проблемы в зависимости от состояния чекбоксов
    val filteredProblems = remember(allProblems, dashboardState.showAcknowledged, dashboardState.showInMaintenance) {
        allProblems.filter { problem ->
            val showAcknowledged = dashboardState.showAcknowledged
            val showInMaintenance = dashboardState.showInMaintenance

            val acknowledgedFilter = if (showAcknowledged) {
                true
            } else {
                problem.acknowledged != "1"
            }

            val maintenanceFilter = if (showInMaintenance) {
                true
            } else {
                problem.suppressed != "1"
            }

            acknowledgedFilter && maintenanceFilter
        }
    }

    // Эффект для обновления выбранного сервера
    LaunchedEffect(servers, dashboardState.selectedServerId) {
        if (servers.isNotEmpty() && dashboardState.selectedServerId != 0L) {
            val server = servers.find { it.id == dashboardState.selectedServerId }
            if (server != null && selectedServer?.id != server.id) {
                selectedServer = server
            }
        }
    }

    // Функция для сохранения состояния
    fun saveState(
        newSelectedServer: ZabbixServer? = selectedServer,
        newShowAcknowledged: Boolean? = null,
        newShowInMaintenance: Boolean? = null
    ) {
        coroutineScope.launch {
            preferencesManager.saveDashboardState(
                DashboardState(
                    selectedServerId = newSelectedServer?.id ?: 0,
                    showAcknowledged = newShowAcknowledged ?: dashboardState.showAcknowledged,
                    showInMaintenance = newShowInMaintenance ?: dashboardState.showInMaintenance
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Zabbix Dashboard",
                    style = MaterialTheme.typography.headlineSmall
                )
                // Время последнего обновления
                lastUpdateTime?.let { time ->
                    Text(
                        text = "Обновлено: $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Информация о проблемах
                if (allProblems.isNotEmpty()) {
                    Text(
                        text = "Проблемы: ${filteredProblems.size} из ${allProblems.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                // Кнопка обновления
                IconButton(
                    onClick = { refreshData() },
                    enabled = selectedServer != null && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Settings")
                }
            }
        }

        // Разделитель
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Панель фильтров
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Комбо-бокс выбора сервера
            ServerDropdown(
                servers = servers,
                selectedServer = selectedServer,
                onServerSelected = { server ->
                    selectedServer = server
                    saveState(newSelectedServer = server)
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Чек-боксы фильтров
            Column {
                FilterCheckbox(
                    text = "Ack",
                    checked = dashboardState.showAcknowledged,
                    onCheckedChange = { newValue ->
                        saveState(newShowAcknowledged = newValue)
                    }
                )
                FilterCheckbox(
                    text = "Maint",
                    checked = dashboardState.showInMaintenance,
                    onCheckedChange = { newValue ->
                        saveState(newShowInMaintenance = newValue)
                    }
                )
            }
        }

        // Разделитель
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Список проблем из API с фильтрацией
        if (selectedServer == null) {
            // Сообщение при отсутствии выбранного сервера
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Выберите сервер для отображения проблем",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (filteredProblems.isNotEmpty()) {
            ProblemsList(
                problems = filteredProblems,
                onAcknowledgeProblem = ::acknowledgeProblem
            )
        } else if (allProblems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Нет проблем, соответствующих фильтрам",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Нет активных проблем",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Список триггеров (существующий) - можно оставить или убрать
        TriggerList(
            triggers = triggers,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProblemsList(
    problems: List<ZabbixProblem>,
    onAcknowledgeProblem: (String, Boolean) -> Unit = { _, _ -> } // Добавляем параметр isAcknowledge
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(problems) { problem ->
            ProblemItem(
                problem = problem,
                onAcknowledge = onAcknowledgeProblem
            )
        }
    }
}

@Composable
fun ProblemItem(
    problem: ZabbixProblem,
    onAcknowledge: (String, Boolean) -> Unit = { _, _ -> }
) {
    var showActions by remember { mutableStateOf(false) }
    var showAckDialog by remember { mutableStateOf(false) }
    val severityColor = getSeverityColor(problem.severity)

    val isAcknowledged = problem.acknowledged == "1"
    val dialogTitle = if (isAcknowledged) "Unacknowledge Event?" else "Ack Event?"
    val dialogText = if (isAcknowledged)
        "Acknowledge this event?"
    else
        "Unacknowledge this event??"

    // Диалог подтверждения Ack/Unack
    if (showAckDialog) {
        AlertDialog(
            onDismissRequest = { showAckDialog = false },
            title = { Text(dialogTitle) },
            text = { Text(dialogText) },
            confirmButton = {
                Button(
                    onClick = {
                        // Отправляем запрос и закрываем диалог
                        onAcknowledge(problem.eventid, !isAcknowledged)
                        showAckDialog = false
                        showActions = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAckDialog = false }) {
                    Text("No")
                }
            }
        )
    }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { showActions = !showActions },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(2.dp, severityColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Первая строка: Хост и время
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = problem.hostName.ifEmpty { "Host-${problem.objectid}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )

                Text(
                    text = problem.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Описание проблемы
            Text(
                text = problem.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Нижняя строка: Длительность и иконки статусов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Длительность проблемы
                Text(
                    text = problem.getDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = severityColor,
                    fontWeight = FontWeight.Medium
                )

                // Иконки статусов
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Иконка Ack (подтверждение)
                    if (isAcknowledged) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Acknowledged",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Not Acknowledged",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Иконка Maint (maintenance)
                    if (problem.suppressed == "1") {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "In Maintenance",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Active",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Кнопки действий (показываются по клику)
            if (showActions) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Кнопка Acknowledge/Unacknowledge - всегда показываем
                    Button(
                        onClick = { showAckDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAcknowledged)
                                Color(0xFFFF9800) // Оранжевый для Unacknowledge
                            else
                                Color(0xFF2196F3) // Синий для Acknowledge
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(if (isAcknowledged) "UnAck Event" else "Ack Event")
                    }

                    Button(
                        onClick = { showActions = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("Close") }

                    Button(
                        onClick = { showActions = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E)),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) { Text("Details") }
                }
            }
        }
    }
}
@Composable
fun getSeverityColor(severity: String): Color {
    return when (severity) {
        "4" -> Color(0xFFCC6633) // Тёмно-оранжевый
        "3" -> Color(0xFFFF9966) // Светло-оранжевый
        "2" -> Color(0xFFFFCC66) // Светло-жёлтый
        "1" -> Color(0xFF66CCFF) // Голубой
        else -> Color.Gray // Для других значений
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDropdown(
    servers: List<ZabbixServer>,
    selectedServer: ZabbixServer?,
    onServerSelected: (ZabbixServer?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedServer?.name ?: "Выберите сервер",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("Сервер") }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (servers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Нет серверов") },
                    onClick = { expanded = false }
                )
            } else {
                servers.forEach { server ->
                    DropdownMenuItem(
                        text = { Text(server.name) },
                        onClick = {
                            onServerSelected(server)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TriggerList(
    triggers: List<ZabbixTrigger>,
    modifier: Modifier = Modifier
) {
    if (triggers.isEmpty()) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Нет активных триггеров",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(triggers) { trigger ->
                TriggerItem(trigger = trigger)
                Divider()
            }
        }
    }
}

@Composable
fun TriggerItem(trigger: ZabbixTrigger) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = trigger.description,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Severity: ${trigger.severity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Host: ${trigger.host}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}