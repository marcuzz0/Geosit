package com.geosit.gnss.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.gnss.FixType
import com.geosit.gnss.data.model.displayName  // <-- Aggiungi questo import
import com.geosit.gnss.ui.viewmodel.DashboardViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val gnssPosition by viewModel.gnssPosition.collectAsState()

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

                if (gnssPosition.fixType != FixType.NO_FIX) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
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
                                "Fix: ${gnssPosition.fixType.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Sats: ${gnssPosition.satellitesUsed}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                String.format("HDOP: %.1f", gnssPosition.hdop),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Text(
                        "No position fix",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Recording Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Recording Status",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Not Recording",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}