package com.geosit.gnss.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosit.gnss.data.gnss.FixType
import com.geosit.gnss.ui.viewmodel.DashboardViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val gnssPosition by viewModel.gnssPosition.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var positionMarker by remember { mutableStateOf<Marker?>(null) }
    var trackPolyline by remember { mutableStateOf<Polyline?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var showMapTypes by remember { mutableStateOf(false) }

    // Track points for polyline
    val trackPoints = remember { mutableListOf<GeoPoint>() }

    // Location permission
    val locationPermission = rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map View") },
                actions = {
                    // Map type selector
                    IconButton(onClick = { showMapTypes = !showMapTypes }) {
                        Icon(Icons.Default.Layers, contentDescription = "Map Type")
                    }

                    // Center on position
                    IconButton(
                        onClick = {
                            mapView?.let { map ->
                                if (gnssPosition.latitude != 0.0 || gnssPosition.longitude != 0.0) {
                                    map.controller.animateTo(
                                        GeoPoint(gnssPosition.latitude, gnssPosition.longitude)
                                    )
                                }
                            }
                        },
                        enabled = gnssPosition.fixType != FixType.NO_FIX
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center")
                    }

                    // Track toggle
                    IconButton(
                        onClick = {
                            isTracking = !isTracking
                            if (!isTracking) {
                                trackPoints.clear()
                                trackPolyline?.let {
                                    mapView?.overlays?.remove(it)
                                    mapView?.invalidate()
                                }
                            }
                        },
                        enabled = connectionState.isConnected
                    ) {
                        Icon(
                            if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking",
                            tint = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (locationPermission.hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)

                            // Set initial position
                            val startPoint = if (gnssPosition.latitude != 0.0 || gnssPosition.longitude != 0.0) {
                                GeoPoint(gnssPosition.latitude, gnssPosition.longitude)
                            } else {
                                // Default to Venice area if no position
                                GeoPoint(45.4375, 12.3358)
                            }
                            controller.setCenter(startPoint)

                            // Add my location overlay
                            if (ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val myLocationOverlay = MyLocationNewOverlay(
                                    GpsMyLocationProvider(ctx),
                                    this
                                )
                                myLocationOverlay.enableMyLocation()
                                overlays.add(myLocationOverlay)
                            }

                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Update position marker
                LaunchedEffect(gnssPosition) {
                    if (gnssPosition.fixType != FixType.NO_FIX) {
                        mapView?.let { map ->
                            val position = GeoPoint(gnssPosition.latitude, gnssPosition.longitude)

                            // Update or create marker
                            if (positionMarker == null) {
                                positionMarker = Marker(map).apply {
                                    title = "GNSS Position"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    map.overlays.add(this)
                                }
                            }

                            positionMarker?.apply {
                                this.position = position
                                snippet = "Fix: ${gnssPosition.fixStatus}\n" +
                                        "Accuracy: ±${String.format("%.1f", gnssPosition.horizontalAccuracy)}m\n" +
                                        "Satellites: ${gnssPosition.satellitesUsed}"
                            }

                            // Add to track if tracking
                            if (isTracking) {
                                trackPoints.add(position)

                                // Update or create polyline
                                if (trackPolyline == null) {
                                    trackPolyline = Polyline().apply {
                                        outlinePaint.color = Color.BLUE
                                        outlinePaint.strokeWidth = 5f
                                        map.overlays.add(0, this) // Add below markers
                                    }
                                }

                                trackPolyline?.setPoints(trackPoints.toList())
                            }

                            map.invalidate()
                        }
                    }
                }

                // Position info overlay
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Fix: ${gnssPosition.fixStatus}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (gnssPosition.fixType) {
                                    FixType.NO_FIX -> MaterialTheme.colorScheme.error
                                    FixType.FIX_2D -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                "Sats: ${gnssPosition.satellitesUsed}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (gnssPosition.fixType != FixType.NO_FIX) {
                            Text(
                                String.format(
                                    "%.8f°, %.8f° • %.1fm",
                                    gnssPosition.latitude,
                                    gnssPosition.longitude,
                                    gnssPosition.altitude
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (gnssPosition.speedMs > 0.5) {
                                Text(
                                    String.format(
                                        "Speed: %.1f km/h • Course: %.0f°",
                                        gnssPosition.speedMs * 3.6,
                                        gnssPosition.course
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (isTracking && trackPoints.isNotEmpty()) {
                            Text(
                                "Tracking: ${trackPoints.size} points",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Map type dropdown
                DropdownMenu(
                    expanded = showMapTypes,
                    onDismissRequest = { showMapTypes = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard") },
                        onClick = {
                            mapView?.setTileSource(TileSourceFactory.MAPNIK)
                            showMapTypes = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Satellite") },
                        onClick = {
                            // Note: For satellite imagery, you might need additional setup
                            // or use a different tile source
                            mapView?.setTileSource(TileSourceFactory.MAPNIK)
                            showMapTypes = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Topographic") },
                        onClick = {
                            mapView?.setTileSource(TileSourceFactory.OpenTopo)
                            showMapTypes = false
                        }
                    )
                }

            } else {
                // No location permission
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Location permission required",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Please grant location permission to view the map",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                        onClick = { locationPermission.launchPermissionRequest() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}