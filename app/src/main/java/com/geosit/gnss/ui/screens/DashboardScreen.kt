package com.geosit.gnss.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        // Connection Status Card with pulsing indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
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
                        "Connection Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Pulsing connection indicator
                    ConnectionIndicator(isConnected = connectionState.isConnected)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (connectionState.isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (connectionState.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    connectionState.connectedDevice?.let { device ->
                        Text(
                            " • ${device.displayName()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Position Card - Only show when recording
        if (recordingState.isRecording && connectionState.isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "GNSS Position",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coordinates
                    PositionRow(
                        label = "Latitude",
                        value = String.format(Locale.US, "%.8f°", gnssPosition.latitude)
                    )
                    PositionRow(
                        label = "Longitude",
                        value = String.format(Locale.US, "%.8f°", gnssPosition.longitude)
                    )
                    PositionRow(
                        label = "Altitude",
                        value = String.format(Locale.US, "%.2f m", gnssPosition.altitude)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Fix info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoChip(
                            label = "Fix",
                            value = gnssPosition.fixStatus,
                            color = when (gnssPosition.fixType) {
                                FixType.NO_FIX -> MaterialTheme.colorScheme.error
                                FixType.FIX_2D -> MaterialTheme.colorScheme.tertiary
                                FixType.FIX_3D -> MaterialTheme.colorScheme.primary
                                FixType.DGPS -> MaterialTheme.colorScheme.primary
                                FixType.RTK_FIXED -> Color(0xFF4CAF50)
                                FixType.RTK_FLOAT -> Color(0xFFFFA726)
                            }
                        )

                        InfoChip(
                            label = "Sats",
                            value = "${gnssPosition.satellitesUsed}",
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        InfoChip(
                            label = "HDOP",
                            value = String.format("%.1f", gnssPosition.hdop),
                            color = when {
                                gnssPosition.hdop <= 1.0 -> Color(0xFF4CAF50)
                                gnssPosition.hdop <= 2.0 -> MaterialTheme.colorScheme.primary
                                gnssPosition.hdop <= 5.0 -> Color(0xFFFFA726)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    // Additional info if available
                    if (gnssPosition.horizontalAccuracy > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            String.format(
                                Locale.US,
                                "Accuracy: ±%.1f m",
                                gnssPosition.horizontalAccuracy
                            ),
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (recordingState.isRecording) {
                    Row(
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
                        Spacer(modifier = Modifier.height(8.dp))

                        RecordingInfoRow(
                            label = "Mode",
                            value = session.mode.name.replace('_', ' ')
                        )

                        if (session.pointName.isNotEmpty()) {
                            RecordingInfoRow(
                                label = "Name",
                                value = session.pointName
                            )
                        }

                        RecordingInfoRow(
                            label = "Duration",
                            value = recordingViewModel.getRecordingDuration()
                        )

                        RecordingInfoRow(
                            label = "Size",
                            value = recordingViewModel.getRecordingSize()
                        )
                    }
                } else {
                    Text(
                        "Not Recording",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionIndicator(isConnected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()

    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 0.3f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    val color by animateColorAsState(
        targetValue = if (isConnected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(
                color.copy(alpha = if (isConnected) animatedAlpha else 1f)
            )
    )
}

@Composable
fun PositionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InfoChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun RecordingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}