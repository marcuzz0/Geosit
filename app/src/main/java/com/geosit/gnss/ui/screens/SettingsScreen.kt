@file:OptIn(ExperimentalMaterial3Api::class)

package com.geosit.gnss.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val recordingSettings by viewModel.recordingSettings.collectAsState()
    val gnssSettings by viewModel.gnssSettings.collectAsState()
    val notificationSettings by viewModel.notificationSettings.collectAsState()

    val scrollState = rememberScrollState()

    // Dialogs
    var showStaticDurationDialog by remember { mutableStateOf(false) }
    var showStopGoDurationDialog by remember { mutableStateOf(false) }
    var showAutoSaveDialog by remember { mutableStateOf(false) }
    var showNavigationRateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingSwitch(
                    title = "Enable Notifications",
                    subtitle = "Show recording status in notification bar",
                    checked = notificationSettings.enableNotifications,
                    onCheckedChange = viewModel::updateNotifications
                )

                SettingSwitch(
                    title = "Sound Alerts",
                    subtitle = "Play sound for important events",
                    checked = notificationSettings.enableSound,
                    onCheckedChange = viewModel::updateNotificationSound,
                    enabled = notificationSettings.enableNotifications
                )

                SettingSwitch(
                    title = "Vibration",
                    subtitle = "Vibrate for alerts",
                    checked = notificationSettings.enableVibration,
                    onCheckedChange = viewModel::updateNotificationVibration,
                    enabled = notificationSettings.enableNotifications
                )
            }
        }

        // Recording Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Recording",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingItem(
                    title = "Default Static Duration",
                    subtitle = "${recordingSettings.staticDuration} seconds",
                    onClick = { showStaticDurationDialog = true }
                )

                SettingItem(
                    title = "Default Stop & Go Duration",
                    subtitle = "${recordingSettings.stopGoDuration} seconds",
                    onClick = { showStopGoDurationDialog = true }
                )

                SettingItem(
                    title = "Navigation Rate",
                    subtitle = "${recordingSettings.navigationRate} Hz",
                    onClick = { showNavigationRateDialog = true }
                )

                SettingItem(
                    title = "Auto-save Interval",
                    subtitle = if (recordingSettings.autoSaveInterval > 0) "Every ${recordingSettings.autoSaveInterval} minutes" else "Disabled",
                    onClick = { showAutoSaveDialog = true }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                SettingSwitch(
                    title = "Enable Raw Data (UBX)",
                    subtitle = "Record raw UBX messages for post-processing",
                    checked = recordingSettings.enableRawData,
                    onCheckedChange = viewModel::updateEnableRawData
                )

                SettingSwitch(
                    title = "High Precision Mode",
                    subtitle = "Enable RTK/PPP messages if available",
                    checked = recordingSettings.enableHighPrecision,
                    onCheckedChange = viewModel::updateEnableHighPrecision
                )
            }
        }

        // GNSS Configuration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "GNSS Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Satellite Systems",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                SettingSwitch(
                    title = "GPS",
                    subtitle = "USA satellite system",
                    checked = gnssSettings.useGPS,
                    onCheckedChange = { viewModel.updateGnssSystem("GPS", it) }
                )

                SettingSwitch(
                    title = "GLONASS",
                    subtitle = "Russian satellite system",
                    checked = gnssSettings.useGLONASS,
                    onCheckedChange = { viewModel.updateGnssSystem("GLONASS", it) }
                )

                SettingSwitch(
                    title = "Galileo",
                    subtitle = "European satellite system",
                    checked = gnssSettings.useGalileo,
                    onCheckedChange = { viewModel.updateGnssSystem("GALILEO", it) }
                )

                SettingSwitch(
                    title = "BeiDou",
                    subtitle = "Chinese satellite system",
                    checked = gnssSettings.useBeidou,
                    onCheckedChange = { viewModel.updateGnssSystem("BEIDOU", it) }
                )
            }
        }

        // About Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // App Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Satellite,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "GeoSit GNSS Logger",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Version 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Professional GNSS data logging application for high-precision positioning. " +
                            "Supports Static, Kinematic, and Stop & Go survey modes with raw data recording " +
                            "in UBX format for post-processing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Developer Info
                Text(
                    "Developer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                AboutItem(
                    icon = Icons.Default.Person,
                    title = "GeoSit Solutions",
                    subtitle = "Professional GNSS Software Development"
                )

                AboutItem(
                    icon = Icons.Default.Email,
                    title = "Contact",
                    subtitle = "support@geosit.com",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@geosit.com")
                            putExtra(Intent.EXTRA_SUBJECT, "GeoSit GNSS Logger Support")
                        }
                        context.startActivity(intent)
                    }
                )

                AboutItem(
                    icon = Icons.Default.Language,
                    title = "Website",
                    subtitle = "www.geosit.com",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.geosit.com"))
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "© 2025 GeoSit Solutions. All rights reserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Extra padding at the bottom for navigation bar
        Spacer(modifier = Modifier.height(80.dp))
    }

    // Dialogs
    if (showStaticDurationDialog) {
        DurationPickerDialog(
            title = "Static Duration",
            currentValue = recordingSettings.staticDuration,
            onDismiss = { showStaticDurationDialog = false },
            onConfirm = { value ->
                viewModel.updateStaticDuration(value)
                showStaticDurationDialog = false
            }
        )
    }

    if (showStopGoDurationDialog) {
        DurationPickerDialog(
            title = "Stop & Go Duration",
            currentValue = recordingSettings.stopGoDuration,
            onDismiss = { showStopGoDurationDialog = false },
            onConfirm = { value ->
                viewModel.updateStopGoDuration(value)
                showStopGoDurationDialog = false
            }
        )
    }

    if (showAutoSaveDialog) {
        IntervalPickerDialog(
            title = "Auto-save Interval",
            currentValue = recordingSettings.autoSaveInterval,
            onDismiss = { showAutoSaveDialog = false },
            onConfirm = { value ->
                viewModel.updateAutoSaveInterval(value)
                showAutoSaveDialog = false
            }
        )
    }

    if (showNavigationRateDialog) {
        RatePickerDialog(
            title = "Navigation Rate",
            currentValue = recordingSettings.navigationRate,
            onDismiss = { showNavigationRateDialog = false },
            onConfirm = { value ->
                viewModel.updateNavigationRate(value)
                showNavigationRateDialog = false
            }
        )
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AboutItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun DurationPickerDialog(
    title: String,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Duration in seconds (minimum 10)")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() } },
                    label = { Text("Seconds") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val seconds = value.toIntOrNull() ?: 0
                    if (seconds >= 10) {
                        onConfirm(seconds)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IntervalPickerDialog(
    title: String,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Interval in minutes (0 to disable)")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() } },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = value.toIntOrNull() ?: 0
                    onConfirm(minutes)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RatePickerDialog(
    title: String,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val rates = listOf(1, 2, 5, 10)
    var selectedRate by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Select navigation rate in Hz")
                Spacer(modifier = Modifier.height(16.dp))
                rates.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRate = rate }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRate == rate,
                            onClick = { selectedRate = rate }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$rate Hz")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedRate) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}