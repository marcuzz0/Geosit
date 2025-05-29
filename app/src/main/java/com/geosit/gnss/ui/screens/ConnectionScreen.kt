package com.geosit.gnss.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.model.Device
import com.geosit.gnss.data.model.connectionInfo
import com.geosit.gnss.data.model.displayName
import com.geosit.gnss.ui.viewmodel.ConnectionViewModel
import com.geosit.gnss.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recordingState by recordingViewModel.recordingState.collectAsState()
    var showAddTcpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    "Device Connection",
                    style = MaterialTheme.typography.headlineLarge
                )
            },
            actions = {
                IconButton(
                    onClick = { viewModel.scanForDevices() },
                    enabled = !uiState.isConnecting
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Connection Status Card - Always visible
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    uiState.isConnected -> MaterialTheme.colorScheme.primaryContainer
                    uiState.isConnecting -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            val contentAlpha by animateFloatAsState(
                targetValue = if (uiState.isConnecting) 0.6f else 1f,
                label = "content_alpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .alpha(contentAlpha)
            ) {
                when {
                    uiState.isConnecting -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connecting",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                uiState.connectedDevice?.let { device ->
                                    Text(
                                        device.displayName(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    uiState.isConnected -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(end = 8.dp)
                                    )
                                    Text(
                                        "Connected",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                uiState.connectedDevice?.let { device ->
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Device name
                                    Text(
                                        device.displayName(),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Connection type with icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when (device) {
                                                is Device.Bluetooth -> Icons.Default.Bluetooth
                                                is Device.Usb -> Icons.Default.Usb
                                                is Device.Tcp -> Icons.Default.Wifi
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            when (device) {
                                                is Device.Bluetooth -> "Bluetooth • ${device.address}"
                                                is Device.Usb -> "USB Serial"
                                                is Device.Tcp -> "TCP/IP • ${device.host}:${device.port}"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }

                                    // Data rate if available
                                    if (uiState.dataRate != "0 B/s") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Speed,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                uiState.dataRate,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.disconnect() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.padding(start = 16.dp)
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Not Connected",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select a device below to connect",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Recording Status Card - Show when recording
        AnimatedVisibility(visible = recordingState.isRecording && uiState.isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recording Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Duration: ${recordingViewModel.getRecordingDuration()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Size: ${recordingViewModel.getRecordingSize()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Available Devices Section
        Text(
            "Available Devices",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (uiState.availableDevices.isEmpty() && !uiState.isConnecting) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "No devices found. Try refreshing or add a TCP device.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Device List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.availableDevices) { device ->
                val isThisDeviceConnecting = uiState.isConnecting && uiState.connectedDevice == device
                val isThisDeviceConnected = uiState.isConnected && uiState.connectedDevice == device

                DeviceCard(
                    device = device,
                    isConnecting = isThisDeviceConnecting,
                    isConnected = isThisDeviceConnected,
                    onConnect = { viewModel.connectToDevice(device) },
                    onRemove = if (device is Device.Tcp && !isThisDeviceConnected) {
                        { viewModel.removeTcpDevice(device) }
                    } else null
                )
            }

            item {
                // Add TCP Device Button
                OutlinedCard(
                    onClick = { showAddTcpDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add TCP Device",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Add TCP/IP Device")
                    }
                }
            }
        }

        // Error display
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }

    // Add TCP Device Dialog
    if (showAddTcpDialog) {
        AddTcpDeviceDialog(
            onDismiss = { showAddTcpDialog = false },
            onAdd = { host, port, name ->
                viewModel.addTcpDevice(host, port, name)
                showAddTcpDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: Device,
    isConnecting: Boolean,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isConnecting && !isConnected) onConnect() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> MaterialTheme.colorScheme.primaryContainer
                isConnecting -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (device) {
                            is Device.Bluetooth -> Icons.Default.Bluetooth
                            is Device.Usb -> Icons.Default.Usb
                            is Device.Tcp -> Icons.Default.Wifi
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp),
                        tint = when {
                            isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        device.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = when {
                            isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Text(
                    device.connectionInfo(),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isConnected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Row {
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        enabled = !isConnecting && !isConnected
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = if (!isConnecting && !isConnected) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
                        )
                    }
                }

                when {
                    isConnecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    isConnected -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddTcpDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (host: String, port: Int, name: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add TCP/IP Device") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host/IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { char -> char.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    placeholder = { Text("My GNSS Receiver") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val portNum = port.toIntOrNull() ?: 0
                    if (host.isNotBlank() && portNum > 0 && portNum <= 65535 && name.isNotBlank()) {
                        onAdd(host.trim(), portNum, name.trim())
                    }
                },
                enabled = host.isNotBlank() && port.isNotBlank() && name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}