@file:OptIn(ExperimentalMaterial3Api::class)

package com.geosit.gnss.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.recording.RecordingRepository
import com.geosit.gnss.ui.viewmodel.RecordingViewModel
import kotlinx.coroutines.launch

@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val gnssPosition by viewModel.gnssPosition.collectAsState()

    // Stati locali UI
    var selectedMode by remember { mutableStateOf(RecordingMode.STATIC) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mostra errori
    LaunchedEffect(recordingState.error) {
        recordingState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                "Recording",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Connection Warning
            AnimatedVisibility(
                visible = !connectionState.isConnected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
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
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "No GNSS device connected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Mode Selection - Solo quando non registra
            AnimatedVisibility(
                visible = !recordingState.isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModeSelectionCard(
                    selectedMode = selectedMode,
                    onModeChange = { selectedMode = it }
                )
            }

            // Recording Status - Durante la registrazione
            AnimatedVisibility(
                visible = recordingState.isRecording,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                RecordingStatusCard(
                    recordingState = recordingState,
                    viewModel = viewModel
                )
            }

            // Stop&Go Controls
            AnimatedVisibility(
                visible = recordingState.isRecording &&
                        recordingState.recordingMode == RecordingMode.STOP_AND_GO
            ) {
                StopGoControlsCard(
                    viewModel = viewModel,
                    stopAndGoPoints = recordingState.stopAndGoPoints
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Main Recording Button
            RecordingButton(
                isRecording = recordingState.isRecording,
                isConnected = connectionState.isConnected,
                onStartClick = {
                    if (connectionState.isConnected) {
                        showSettingsDialog = true
                    }
                },
                onStopClick = {
                    viewModel.stopRecording()
                }
            )
        }
    }

    // Recording Settings Dialog
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
fun ModeSelectionCard(
    selectedMode: RecordingMode,
    onModeChange: (RecordingMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Recording Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RecordingModeChip(
                    selected = selectedMode == RecordingMode.STATIC,
                    onClick = { onModeChange(RecordingMode.STATIC) },
                    label = "Static",
                    icon = Icons.Default.LocationOn
                )
                RecordingModeChip(
                    selected = selectedMode == RecordingMode.KINEMATIC,
                    onClick = { onModeChange(RecordingMode.KINEMATIC) },
                    label = "Kinematic",
                    icon = Icons.Default.DirectionsRun
                )
                RecordingModeChip(
                    selected = selectedMode == RecordingMode.STOP_AND_GO,
                    onClick = { onModeChange(RecordingMode.STOP_AND_GO) },
                    label = "Stop&Go",
                    icon = Icons.Default.PauseCircleOutline
                )
            }

            // Mode description
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (selectedMode) {
                    RecordingMode.STATIC -> "Record a single point for a fixed duration"
                    RecordingMode.KINEMATIC -> "Continuous recording while moving"
                    RecordingMode.STOP_AND_GO -> "Record multiple points with start/stop control"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RecordingStatusCard(
    recordingState: RecordingRepository.RecordingState,
    viewModel: RecordingViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                PulsingRecordIcon()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recording metrics in real-time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RecordingMetric(
                    label = "Duration",
                    value = viewModel.getRecordingDuration()
                )
                RecordingMetric(
                    label = "Size",
                    value = viewModel.getRecordingSize()
                )
                RecordingMetric(
                    label = "Data Rate",
                    value = viewModel.getDataRate()
                )
            }

            // Session info
            recordingState.currentSession?.let { session ->
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoRow("Mode", session.mode.name.replace('_', ' '))
                    if (session.pointName.isNotEmpty()) {
                        InfoRow("Name", session.pointName)
                    }
                    if (session.instrumentHeight > 0) {
                        InfoRow("Height", "${session.instrumentHeight} m")
                    }
                }
            }
        }
    }
}

@Composable
fun StopGoControlsCard(
    viewModel: RecordingViewModel,
    stopAndGoPoints: List<com.geosit.gnss.data.model.StopAndGoPoint>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Stop & Go Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { viewModel.addStopPoint() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }

                Button(
                    onClick = { viewModel.addGoPoint() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Go")
                }
            }

            // Recent points
            if (stopAndGoPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Recent Points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stopAndGoPoints.takeLast(3).forEach { point ->
                    Text(
                        "${point.name} - ${point.action}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(label)
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun PulsingRecordIcon() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    Icon(
        Icons.Default.FiberManualRecord,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun RecordingMetric(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun RecordingButton(
    isRecording: Boolean,
    isConnected: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Button(
        onClick = {
            if (isRecording) {
                onStopClick()
            } else {
                onStartClick()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = isConnected,
        colors = if (isRecording) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Icon(
            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (isRecording) "Stop Recording" else "Start Recording",
            style = MaterialTheme.typography.titleMedium
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
                    placeholder = { Text("0.0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (mode == RecordingMode.STATIC) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = staticDuration,
                        onValueChange = { staticDuration = it.filter { char -> char.isDigit() } },
                        label = { Text("Duration (seconds)") },
                        placeholder = { Text("60") },
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