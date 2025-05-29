package com.geosit.gnss.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.gnss.FixType
import com.geosit.gnss.data.model.displayName
import com.geosit.gnss.ui.viewmodel.DashboardViewModel
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val gnssPosition by viewModel.gnssPosition.collectAsState()
    val satellites by viewModel.satellites.collectAsState()
    val gnssStatistics by viewModel.gnssStatistics.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    "GeoSit GNSS Logger",
                    style = MaterialTheme.typography.headlineLarge
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Connection Status",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (connectionState.isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (connectionState.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    connectionState.connectedDevice?.let { device ->
                        Text(
                            " • ${device.displayName()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Data stats when connected
                if (connectionState.isConnected) {
                    Text(
                        "Messages: ${gnssStatistics.totalMessages} (${gnssStatistics.lastMessageType})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Position Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Position",
                    style = MaterialTheme.typography.titleLarge
                )

                if (connectionState.isConnected) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        // Show coordinates even if 0,0 (waiting for fix)
                        Text(
                            String.format(
                                Locale.US,
                                "Latitude: %.8f°",
                                gnssPosition.latitude
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            String.format(
                                Locale.US,
                                "Longitude: %.8f°",
                                gnssPosition.longitude
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            String.format(
                                Locale.US,
                                "Altitude: %.2f m",
                                gnssPosition.altitude
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when (gnssPosition.fixType) {
                                    FixType.NO_FIX -> "No Fix"
                                    FixType.FIX_2D -> "2D Fix"
                                    FixType.FIX_3D -> "3D Fix"
                                    FixType.DGPS -> "DGPS"
                                    FixType.RTK_FIXED -> "RTK Fixed"
                                    FixType.RTK_FLOAT -> "RTK Float"
                                    else -> "Single"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (gnssPosition.fixType) {
                                    FixType.NO_FIX -> MaterialTheme.colorScheme.error
                                    FixType.FIX_2D -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                "Sats: ${gnssPosition.satellitesUsed}/${satellites.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                String.format("HDOP: %.1f", gnssPosition.hdop),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Show waiting message if no fix
                        if (gnssPosition.fixType == FixType.NO_FIX) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Waiting for satellite fix...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Additional info if available
                        if (gnssPosition.accuracy > 0) {
                            Text(
                                String.format("Accuracy: %.1f m", gnssPosition.accuracy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (gnssPosition.speed > 0.1) {
                            Text(
                                String.format("Speed: %.1f m/s (%.1f km/h)",
                                    gnssPosition.speed,
                                    gnssPosition.speed * 3.6
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        "Connect to a GNSS device to see position",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recording Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (recordingState.isRecording) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
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
                        "Recording Status",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (recordingState.isRecording) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (recordingState.isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))

                    recordingState.currentSession?.let { session ->
                        Text(
                            "Mode: ${session.mode.name.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (session.pointName.isNotEmpty()) {
                            Text(
                                "Name: ${session.pointName}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "File: ${session.fileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                viewModel.getRecordingDuration(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                viewModel.getRecordingSize(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Points",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                "${recordingState.dataReceivedCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    Text(
                        "Not Recording",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Go to Record tab to start",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}