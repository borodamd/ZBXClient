package com.itsoul.zbxclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(
    servers: List<ZabbixServer>,
    onBackClick: () -> Unit,
    onServersUpdate: (List<ZabbixServer>) -> Unit, // Этот параметр теперь не используется
    preferencesManager: PreferencesManager
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ZabbixServer?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Функция для сохранения серверов
    fun saveServers(updatedServers: List<ZabbixServer>) {
        // Сохраняем в SharedPreferences
        coroutineScope.launch {
            preferencesManager.saveServers(updatedServers)
        }
        // НЕ вызываем onServersUpdate - данные обновятся через Flow автоматически
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back and add buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Servers",
                style = MaterialTheme.typography.headlineSmall
            )

            Row {
                Button(onClick = onBackClick) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Server")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (servers.isEmpty()) {
            // Message when no servers
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No servers configured",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap + to add your first Zabbix server",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Servers list
            LazyColumn {
                items(servers.size) { index ->
                    ServerItem(
                        server = servers[index],
                        onEdit = { server ->
                            editingServer = server
                        },
                        onDelete = { server ->
                            val updatedServers = servers.filter { it.id != server.id }
                            saveServers(updatedServers) // Используем новую функцию
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Add server dialog
    if (showAddDialog) {
        AddEditServerDialog(
            server = null,
            onDismiss = { showAddDialog = false },
            onSave = { newServer ->
                val newId = if (servers.isEmpty()) 1 else servers.maxOf { it.id } + 1
                val updatedServers = servers + newServer.copy(id = newId)
                saveServers(updatedServers) // Используем новую функцию
                showAddDialog = false
            }
        )
    }

    // Edit server dialog
    if (editingServer != null) {
        AddEditServerDialog(
            server = editingServer,
            onDismiss = { editingServer = null },
            onSave = { updatedServer ->
                val updatedServers = servers.map { if (it.id == updatedServer.id) updatedServer else it }
                saveServers(updatedServers) // Используем новую функцию
                editingServer = null
            }
        )
    }
}

@Composable
fun ServerItem(
    server: ZabbixServer,
    onEdit: (ZabbixServer) -> Unit,
    onDelete: (ZabbixServer) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEdit(server) }
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "User: ${server.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (server.useApiKey) {
                        Text(
                            text = "Auth: API Key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { onEdit(server) }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete \"${server.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(server)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddEditServerDialog(
    server: ZabbixServer?,
    onDismiss: () -> Unit,
    onSave: (ZabbixServer) -> Unit
) {
    var serverName by remember { mutableStateOf(server?.name ?: "") }
    var serverUrl by remember { mutableStateOf(server?.url ?: "https://zbx.zserver.com/api_jsonrpc.php") }
    var username by remember { mutableStateOf(server?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var useApiKey by remember { mutableStateOf(server?.useApiKey ?: false) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "Add Zabbix Server" else "Edit Zabbix Server") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useApiKey,
                        onCheckedChange = { useApiKey = it }
                    )
                    Text("Use API Key")
                }

                if (!useApiKey) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        showPlaceholder = server != null && server.password.isNotEmpty()
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "API Key",
                        showPlaceholder = server != null && server.apiKey.isNotEmpty()
                    )
                }

                // Hint that password/key is saved
                if (server != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (useApiKey) "API key is saved (enter new value to change)" else "Password is saved (enter new value to change)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedServer = ZabbixServer(
                        id = server?.id ?: 0,
                        name = serverName,
                        url = serverUrl,
                        username = username,
                        password = password.ifEmpty { server?.password ?: "" },
                        useApiKey = useApiKey,
                        apiKey = apiKey.ifEmpty { server?.apiKey ?: "" }
                    )
                    onSave(updatedServer)
                },
                enabled = serverName.isNotEmpty() && serverUrl.isNotEmpty() &&
                        ((!useApiKey && username.isNotEmpty()) || (useApiKey && (apiKey.isNotEmpty() || server?.apiKey?.isNotEmpty() == true)))
            ) {
                Text(if (server == null) "Save" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Simple password field with visibility toggle
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPlaceholder: Boolean = false
) {
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(
                onClick = { isVisible = !isVisible }
            ) {
                Text(if (isVisible) "Hide" else "Show")
            }
        },
        placeholder = {
            if (showPlaceholder && value.isEmpty()) {
                Text("●●●●●●●●")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ServersScreenPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    ZabbixAppTheme(darkTheme = false) {
        ServersScreen(
            servers = emptyList(),
            onBackClick = {},
            onServersUpdate = {},
            preferencesManager = PreferencesManager(context)
        )
    }
}