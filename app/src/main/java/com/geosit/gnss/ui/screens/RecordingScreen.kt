package com.geosit.gnss.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.StopGoAction
import com.geosit.gnss.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val gnssPosition by viewModel.gnssPosition.collectAsState()
    
    var selectedMode by remember { mutableStateOf(RecordingMode.STATIC) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = { 
                Text(
                    "Recording",
                    style = MaterialTheme.typography.headlineLarge
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        
        // Connection Status
        if (!connectionState.isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "No device connected",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        // Recording Mode Selection
        AnimatedVisibility(visible = !recordingState.isRecording) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Recording Mode",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = selectedMode == RecordingMode.STATIC,
                            onClick = { selectedMode = RecordingMode.STATIC },
                            label = { Text("Static") }
                        )
                        FilterChip(
                            selected = selectedMode == RecordingMode.KINEMATIC,
                            onClick = { selectedMode = RecordingMode.KINEMATIC },
                            label = { Text("Kinematic") }
                        )
                        FilterChip(
                            selected = selectedMode == RecordingMode.STOP_AND_GO,
                            onClick = { selectedMode = RecordingMode.STOP_AND_GO },
                            label = { Text("Stop&Go") }
                        )
                    }
                }
            }
        }
        
        // Recording Status
        AnimatedVisibility(visible = recordingState.isRecording) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                            "Recording",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                viewModel.getRecordingDuration(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                viewModel.getRecordingSize(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    recordingState.currentSession?.let { session ->
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Text(
                            "Mode: ${session.mode.name.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (session.pointName.isNotEmpty()) {
                            Text(
                                "Name: ${session.pointName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        
        // Stop&Go Controls
        AnimatedVisibility(
            visible = recordingState.isRecording && 
                     recordingState.recordingMode == RecordingMode.STOP_AND_GO
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Stop & Go Controls",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.addStopPoint() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop")
                        }
                        
                        Button(
                            onClick = { viewModel.addGoPoint() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Go")
                        }
                    }
                    
                    // Show points
                    recordingState.stopAndGoPoints.takeLast(3).forEach { point ->
                        Text(
                            "${point.name} - ${point.action}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Main Recording Button
        if (!recordingState.isRecording) {
            Button(
                onClick = { 
                    if (connectionState.isConnected) {
                        showSettingsDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = connectionState.isConnected
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording")
            }
        } else {
            Button(
                onClick = { viewModel.stopRecording() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Recording")
            }
        }
        
        // Error display
        recordingState.error?.let { error ->
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
    
    // Settings Dialog
    if (showSettingsDialog) {
        RecordingSettingsDialog(
            mode = selectedMode,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { pointName, instrumentHeight, staticDuration ->
                viewModel.startRecording(
                    mode = selectedMode,
                    pointName = pointName,
                    instrumentHeight = instrumentHeight,
                    staticDuration = staticDuration
                )
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun RecordingSettingsDialog(
    mode: RecordingMode,
    onDismiss: () -> Unit,
    onConfirm: (pointName: String, instrumentHeight: Double, staticDuration: Int) -> Unit
) {
    var pointName by remember { mutableStateOf("") }
    var instrumentHeight by remember { mutableStateOf("") }
    var staticDuration by remember { mutableStateOf("60") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                when (mode) {
                    RecordingMode.STATIC -> "Static Recording Settings"
                    RecordingMode.KINEMATIC -> "Kinematic Recording Settings"
                    RecordingMode.STOP_AND_GO -> "Stop & Go Settings"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pointName,
                    onValueChange = { pointName = it },
                    label = { 
                        Text(
                            when (mode) {
                                RecordingMode.STATIC -> "Point Name"
                                RecordingMode.KINEMATIC -> "Track Name"
                                RecordingMode.STOP_AND_GO -> "Session Name"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = instrumentHeight,
                    onValueChange = { instrumentHeight = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Instrument Height (m)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (mode == RecordingMode.STATIC) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = staticDuration,
                        onValueChange = { staticDuration = it.filter { char -> char.isDigit() } },
                        label = { Text("Duration (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val height = instrumentHeight.toDoubleOrNull() ?: 0.0
                    val duration = staticDuration.toIntOrNull() ?: 60
                    onConfirm(pointName, height, duration)
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
