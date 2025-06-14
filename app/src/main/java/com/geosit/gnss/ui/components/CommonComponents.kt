package com.geosit.gnss.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.geosit.gnss.data.model.RecordingMode

@Composable
fun RecordingModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector
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
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.height(32.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsDialog(
    mode: RecordingMode,
    onDismiss: () -> Unit,
    onConfirm: (pointName: String, instrumentHeight: Double, staticDuration: Int) -> Unit
) {
    var pointName by remember { mutableStateOf("") }
    var instrumentHeight by remember { mutableStateOf("") }
    var staticDuration by remember { mutableStateOf(if (mode == RecordingMode.STOP_AND_GO) "30" else "60") }

    // Validation states
    var pointNameError by remember { mutableStateOf(false) }
    var instrumentHeightError by remember { mutableStateOf(false) }
    var staticDurationError by remember { mutableStateOf(false) }

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
                    onValueChange = {
                        pointName = it
                        pointNameError = false
                    },
                    label = {
                        Text(
                            when (mode) {
                                RecordingMode.STATIC -> "Point Name *"
                                RecordingMode.KINEMATIC -> "Track Name *"
                                RecordingMode.STOP_AND_GO -> "Session Name *"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = pointNameError,
                    supportingText = if (pointNameError) {
                        { Text("This field is required") }
                    } else null
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instrumentHeight,
                    onValueChange = {
                        instrumentHeight = it.filter { char -> char.isDigit() || char == '.' }
                        instrumentHeightError = false
                    },
                    label = { Text("Instrument Height (m) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = instrumentHeightError,
                    supportingText = if (instrumentHeightError) {
                        { Text("Please enter a valid number") }
                    } else null
                )

                if (mode == RecordingMode.STATIC) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = staticDuration,
                        onValueChange = {
                            staticDuration = it.filter { char -> char.isDigit() }
                            staticDurationError = false
                        },
                        label = { Text("Duration (seconds) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = staticDurationError,
                        supportingText = if (staticDurationError) {
                            { Text("Minimum 10 seconds") }
                        } else {
                            { Text("Recommended: 60+ seconds") }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    when (mode) {
                        RecordingMode.STATIC -> "The receiver will record for the specified duration"
                        RecordingMode.KINEMATIC -> "Continuous recording while moving"
                        RecordingMode.STOP_AND_GO -> "Record points with manual start/stop control"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate inputs
                    var hasError = false

                    if (pointName.isBlank()) {
                        pointNameError = true
                        hasError = true
                    }

                    val height = instrumentHeight.toDoubleOrNull()
                    if (height == null || height < 0) {
                        instrumentHeightError = true
                        hasError = true
                    }

                    if (mode == RecordingMode.STATIC) {
                        val duration = staticDuration.toIntOrNull()
                        if (duration == null || duration < 10) {
                            staticDurationError = true
                            hasError = true
                        }
                    }

                    if (!hasError) {
                        onConfirm(
                            pointName.trim(),
                            height ?: 0.0,
                            staticDuration.toIntOrNull() ?: 60
                        )
                    }
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