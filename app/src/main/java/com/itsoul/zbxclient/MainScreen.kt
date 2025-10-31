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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
// - Warning    val context = LocalContext.current

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

    // Функция для закрытия проблемы
    fun closeProblem(eventId: String) {
        println("🔄 closeProblem: eventId=$eventId")

        if (selectedServer == null) {
            println("❌ selectedServer is null")
            return
        }

        coroutineScope.launch {
            try {
                println("🔄 Calling closeProblem...")
                val result = zabbixRepository.closeProblem(
                    serverUrl = selectedServer!!.url,
                    apiKey = selectedServer!!.apiKey,
                    eventId = eventId
                )

                println("✅ closeProblem result: $result")

                // Если успешно, обновляем данные МГНОВЕННО с помощью forceRefreshData
                if (result) {
                    println("🔄 Calling forceRefreshData...")
                    forceRefreshData()
                } else {
                    println("❌ closeProblem returned false")
                }
            } catch (e: Exception) {
                println("❌ Close problem error: ${e.message}")
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
                    text = stringResource(R.string.zabbix_dashboard),
                    style = MaterialTheme.typography.headlineSmall
                )
                // Время последнего обновления
                lastUpdateTime?.let { time ->
                    Text(
                        text = stringResource(R.string.last_updated, time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Информация о проблемах
                if (allProblems.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.problems_count,
                            filteredProblems.size,
                            allProblems.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // FAB-стиль кнопки настроек
                FloatingActionButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
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
                    text = stringResource(R.string.ack),
                    checked = dashboardState.showAcknowledged,
                    onCheckedChange = { newValue ->
                        saveState(newShowAcknowledged = newValue)
                    }
                )
                FilterCheckbox(
                    text = stringResource(R.string.maint),
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
                    text = stringResource(R.string.no_server_selected),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (filteredProblems.isNotEmpty()) {
            ProblemsList(
                problems = filteredProblems,
                onAcknowledgeProblem = ::acknowledgeProblem,
                onCloseProblem = ::closeProblem
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
                    text = stringResource(R.string.no_problems_filter),
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
                    text = stringResource(R.string.no_active_problems),
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
    onAcknowledgeProblem: (String, Boolean) -> Unit = { _, _ -> },
    onCloseProblem: (String) -> Unit = { _ -> }
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(problems) { problem ->
            ProblemItem(
                problem = problem,
                onAcknowledge = onAcknowledgeProblem,
                onClose = onCloseProblem
            )
        }
    }
}

@Composable
fun ProblemItem(
    problem: ZabbixProblem,
    onAcknowledge: (String, Boolean) -> Unit = { _, _ -> },
    onClose: (String) -> Unit = { _ -> }
) {
    var showActions by remember { mutableStateOf(false) }
    var showAckDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    val severityColor = getSeverityColor(problem.severity)

    val isAcknowledged = problem.acknowledged == "1"
    val isManualCloseEnabled = problem.manualClose == "1"

    val dialogTitle = if (isAcknowledged)
        stringResource(R.string.unack_dialog_title)
    else
        stringResource(R.string.ack_dialog_title)

    val dialogText = if (isAcknowledged)
        stringResource(R.string.unack_dialog_text)
    else
        stringResource(R.string.ack_dialog_text)

    // Диалог подтверждения Ack/Unack
    if (showAckDialog) {
        AlertDialog(
            onDismissRequest = { showAckDialog = false },
            title = { Text(dialogTitle) },
            text = { Text(dialogText) },
            confirmButton = {
                Button(
                    onClick = {
                        onAcknowledge(problem.eventid, !isAcknowledged)
                        showAckDialog = false
                        showActions = false
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAckDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    // Диалог подтверждения закрытия проблемы
    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text(stringResource(R.string.close_dialog_title)) },
            text = { Text(stringResource(R.string.close_dialog_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClose(problem.eventid)
                        showCloseDialog = false
                        showActions = false
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    // Диалог с деталями проблемы (включая комментарии)
    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.problem_details),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    // Хост
                    Text(
                        text = "${stringResource(R.string.host)}: ${problem.hostName.ifEmpty { "Host-${problem.objectid}" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Описание проблемы
                    Text(
                        text = "${stringResource(R.string.problem)}: ${problem.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Время возникновения
                    Text(
                        text = "${stringResource(R.string.time)}: ${problem.getFormattedTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Длительность
                    Text(
                        text = "${stringResource(R.string.duration)}: ${problem.getDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Severity
                    Text(
                        text = "${stringResource(R.string.severity)}: ${getSeverityText(problem.severity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = severityColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Статусы
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.status)}: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (isAcknowledged) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.acknowledged),
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.acknowledged),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.not_acknowledged),
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.not_acknowledged),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF44336)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        if (problem.suppressed == "1") {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = stringResource(R.string.in_maintenance),
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.in_maintenance),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }

                    // Комментарии (если есть)
                    if (problem.comments.isNotEmpty()) {
                        Column {
                            Text(
                                text = "${stringResource(R.string.comments)}:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = problem.comments,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.no_comments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text(stringResource(R.string.close))
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
            // Первая строка: Хост, продолжительность и иконки статусов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Хост (слева)
                Text(
                    text = problem.hostName.ifEmpty { "Host-${problem.objectid}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                    modifier = Modifier.weight(1f)
                )

                // Правая часть: продолжительность и иконки статусов
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Продолжительность
                    Text(
                        text = problem.getDuration(),
                        style = MaterialTheme.typography.bodySmall,
                        color = severityColor,
                        fontWeight = FontWeight.Medium
                    )

                    // Иконка Ack (подтверждение) с учётом manual_close
                    if (isAcknowledged) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.acknowledged),
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isManualCloseEnabled) {
                        // Жёлтый флаг если не подтверждено, но можно закрыть
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = stringResource(R.string.not_acknowledged),
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = stringResource(R.string.not_acknowledged),
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Иконка Maint (maintenance)
                    if (problem.suppressed == "1") {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource(R.string.in_maintenance),
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.active),
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Описание проблемы
            Text(
                text = problem.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // Кнопки действий (показываются по клику)
            if (showActions) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Кнопка Acknowledge/Unacknowledge
                    Button(
                        onClick = { showAckDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAcknowledged)
                                Color(0xFFFF9800)
                            else
                                Color(0xFF2196F3)
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(if (isAcknowledged) stringResource(R.string.unack_event) else stringResource(R.string.ack_event))
                    }

                    // Кнопка Close
                    Button(
                        onClick = {
                            if (isManualCloseEnabled) {
                                showCloseDialog = true
                            }
                        },
                        enabled = isManualCloseEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isManualCloseEnabled)
                                Color(0xFF4CAF50)
                            else
                                Color(0xFF9E9E9E)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) {
                        Text(if (isManualCloseEnabled) stringResource(R.string.close) else stringResource(R.string.not_available))
                    }

                    // Кнопка Details
                    Button(
                        onClick = {
                            showDetailsDialog = true
                            showActions = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E)),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text(stringResource(R.string.details))
                    }
                }
            }
        }
    }
}

// Вспомогательная функция для текстового представления severity
@Composable
fun getSeverityText(severity: String): String {
    return when (severity) {
        "4" -> "High"
        "3" -> "Average"
        "2" -> "Warning"
        "1" -> "Information"
        else -> "Unknown"
    }
}

@Composable
fun getSeverityColor(severity: String): Color {
    return when (severity) {
        "4" -> Color(0xFFCC6633)
        "3" -> Color(0xFFFF9966)
        "2" -> Color(0xFFFFCC66)
        "1" -> Color(0xFF66CCFF)
        else -> Color.Gray
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
            value = selectedServer?.name ?: stringResource(R.string.select_server),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text(stringResource(R.string.server)) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (servers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_servers)) },
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
                text = stringResource(R.string.no_active_problems),
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