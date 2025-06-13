package com.geosit.gnss.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.gnss.FixType
import com.geosit.gnss.data.model.displayName
import com.geosit.gnss.ui.viewmodel.DashboardViewModel
import com.geosit.gnss.ui.viewmodel.RecordingViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel()
) {
    val connectionState by dashboardViewModel.connectionState.collectAsState()
    val gnssPosition by dashboardViewModel.gnssPosition.collectAsState()
    val recordingState by recordingViewModel.recordingState.collectAsState()

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

                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Show coordinates even if no fix
                    Text(
                        String.format(
                            Locale.US,
                            "Latitude: %.8f°",
                            gnssPosition.latitude
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (gnssPosition.fixType != FixType.NO_FIX) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        String.format(
                            Locale.US,
                            "Longitude: %.8f°",
                            gnssPosition.longitude
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (gnssPosition.fixType != FixType.NO_FIX) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        String.format(
                            Locale.US,
                            "Altitude: %.2f m",
                            gnssPosition.altitude
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (gnssPosition.fixType != FixType.NO_FIX) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Fix info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Fix status with color
                        Row {
                            Text(
                                "Fix: ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (connectionState.isConnected && gnssPosition.fixStatus == "Waiting for GPS...") {
                                    "No Fix"
                                } else {
                                    gnssPosition.fixStatus
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (gnssPosition.fixType) {
                                    FixType.NO_FIX -> MaterialTheme.colorScheme.error
                                    FixType.FIX_2D -> MaterialTheme.colorScheme.tertiary
                                    FixType.FIX_3D -> MaterialTheme.colorScheme.primary
                                    FixType.DGPS -> MaterialTheme.colorScheme.primary
                                    FixType.RTK_FIXED -> Color(0xFF4CAF50) // Green
                                    FixType.RTK_FLOAT -> Color(0xFFFFA726) // Orange
                                }
                            )
                        }

                        Text(
                            "Sats: ${gnssPosition.satellitesUsed}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            String.format("HDOP: %.1f", gnssPosition.hdop),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                gnssPosition.hdop <= 1.0 -> Color(0xFF4CAF50) // Excellent
                                gnssPosition.hdop <= 2.0 -> MaterialTheme.colorScheme.primary // Good
                                gnssPosition.hdop <= 5.0 -> Color(0xFFFFA726) // Moderate
                                else -> MaterialTheme.colorScheme.error // Poor
                            }
                        )
                    }

                    // Additional info if available
                    if (gnssPosition.satellitesInView > 0) {
                        Text(
                            "Satellites in view: ${gnssPosition.satellitesInView}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Accuracy if available
                    if (gnssPosition.horizontalAccuracy > 0) {
                        Text(
                            String.format(
                                Locale.US,
                                "Estimated accuracy: ±%.1f m",
                                gnssPosition.horizontalAccuracy
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Speed if moving
                    if (gnssPosition.speedMs > 0.5) { // Show speed if > 0.5 m/s
                        Text(
                            String.format(
                                Locale.US,
                                "Speed: %.1f km/h (%.1f m/s)",
                                gnssPosition.speedMs * 3.6,
                                gnssPosition.speedMs
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // UTC time if available
                    if (gnssPosition.utcTime.isNotEmpty()) {
                        Text(
                            "GPS Time: ${gnssPosition.utcTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Recording Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = if (recordingState.isRecording) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Recording Status",
                    style = MaterialTheme.typography.titleLarge
                )

                if (recordingState.isRecording) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text(
                            "Recording",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Recording details
                    recordingState.currentSession?.let { session ->
                        Column(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
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
                            Text(
                                "Duration: ${recordingViewModel.getRecordingDuration()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Size: ${recordingViewModel.getRecordingSize()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        "Not Recording",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}