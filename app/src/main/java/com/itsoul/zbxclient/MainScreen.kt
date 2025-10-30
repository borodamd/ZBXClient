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
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

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

    // Состояние для тестирования API
    var problems by remember { mutableStateOf<List<ZabbixProblem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val zabbixRepository = remember { ZabbixRepository() }

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

    // Функция для тестирования API
    fun testApiConnection() {
        if (selectedServer == null) {
            errorMessage = "Please select a server first"
            return
        }

        isLoading = true
        errorMessage = null
        problems = emptyList()

        coroutineScope.launch {
            try {
                // ВАЖНО: использовать getProblemsWithHostNames вместо getProblems
                val result = zabbixRepository.getProblemsWithHostNames(selectedServer!!.url, selectedServer!!.apiKey)
                problems = result
                errorMessage = "✅ Success! Loaded ${result.size} problems"
            } catch (e: Exception) {
                errorMessage = "❌ API Error: ${e.message}"
                problems = emptyList()
            } finally {
                isLoading = false
            }
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
                // Отладочная информация
                Text(
                    text = "Серверов: ${servers.size}, ID: ${dashboardState.selectedServerId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ack: ${dashboardState.showAcknowledged}, Maint: ${dashboardState.showInMaintenance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                // Кнопка тестирования API
                Button(
                    onClick = { testApiConnection() },
                    enabled = selectedServer != null && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Test API")
                }

                IconButton(
                    onClick = {
                        // TODO: Refresh triggers
                        saveState()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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

        // Состояние загрузки
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Ошибки/сообщения API
        errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.startsWith("✅"))
                        Color(0xFFE8F5E8)
                    else
                        Color(0xFFFFEBEE)
                )
            ) {
                Text(
                    text = message,
                    color = if (message.startsWith("✅"))
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

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

        // Список проблем из API (временный для тестирования)
        if (problems.isNotEmpty()) {
            Text(
                text = "API Problems (${problems.size}):",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProblemsList(problems = problems)
            Spacer(modifier = Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Список триггеров (существующий)
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
                    text = "Выберите сервер для отображения триггеров",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            TriggerList(
                triggers = triggers,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ProblemsList(problems: List<ZabbixProblem>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(problems) { problem ->
            ProblemItem(problem = problem)
        }
    }
}

@Composable
fun ProblemItem(problem: ZabbixProblem) {
    var showActions by remember { mutableStateOf(false) }
    val severityColor = getSeverityColor(problem.severity)

    // ЗАКОММЕНТИРОВАНО: Отладочная информация
    // val debugText = "DEBUG: Host: ${problem.hostName} (ID: ${problem.objectid})"

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
            // ЗАКОММЕНТИРОВАНО: Отладочная информация
            // Text(
            //     text = debugText,
            //     style = MaterialTheme.typography.bodySmall,
            //     color = Color.Red,
            //     modifier = Modifier.fillMaxWidth()
            // )

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

            // Длительность проблемы
            Text(
                text = problem.getDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = severityColor,
                fontWeight = FontWeight.Medium
            )

            // Кнопки действий (показываются по клику)
            if (showActions) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showActions = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) { Text("Acknowledge") }

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