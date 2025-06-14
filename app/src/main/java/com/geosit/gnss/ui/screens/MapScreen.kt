package com.geosit.gnss.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosit.gnss.data.gnss.FixType
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.StopGoAction
import com.geosit.gnss.ui.components.RecordingModeChip
import com.geosit.gnss.ui.components.RecordingSettingsDialog
import com.geosit.gnss.ui.viewmodel.RecordingViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val gnssPosition by viewModel.gnssPosition.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val stopGoState by viewModel.stopGoState.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showMapTypes by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(RecordingMode.STATIC) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }

    // Current position marker (always visible when connected)
    var currentPositionMarker by remember { mutableStateOf<Marker?>(null) }

    // Tracking elements
    val kinematicPoints = remember { mutableListOf<GeoPoint>() }
    val stopGoMarkers = remember { mutableStateMapOf<String, Marker>() }
    val stopGoPoints = remember { mutableListOf<GeoPoint>() }

    // Location permission
    val locationPermissionState = rememberPermissionState(
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

    // Clear recording overlays when recording stops (but keep position marker)
    LaunchedEffect(recordingState.isRecording) {
        if (!recordingState.isRecording) {
            mapView?.let { map ->
                // Remove only recording-related overlays, keep position marker
                map.overlays.removeAll { overlay ->
                    overlay is Polyline || (overlay is Marker && overlay != currentPositionMarker)
                }
                kinematicPoints.clear()
                stopGoMarkers.clear()
                stopGoPoints.clear()
                map.invalidate()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Map View")
                        if (recordingState.isRecording) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RecordingIndicator()
                        }
                    }
                },
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

                    // Expand/Collapse controls
                    IconButton(
                        onClick = { isMapExpanded = !isMapExpanded }
                    ) {
                        Icon(
                            if (isMapExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isMapExpanded) "Collapse" else "Expand"
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
            if (locationPermissionState.status.isGranted) {
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

                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Always update position marker when connected
                LaunchedEffect(gnssPosition, connectionState) {
                    if (connectionState.isConnected && gnssPosition.fixType != FixType.NO_FIX) {
                        mapView?.let { map ->
                            val position = GeoPoint(gnssPosition.latitude, gnssPosition.longitude)

                            // Update current position marker
                            if (currentPositionMarker == null) {
                                currentPositionMarker = Marker(map).apply {
                                    title = "Current Position"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    // Use a different icon for current position
                                    setTextIcon("⊕")
                                    map.overlays.add(0, this) // Add at bottom
                                }
                            }

                            currentPositionMarker?.position = position

                            // Update recording overlays if recording
                            if (recordingState.isRecording) {
                                when (recordingState.recordingMode) {
                                    RecordingMode.STATIC -> updateStaticMode(map, position, gnssPosition.fixType)
                                    RecordingMode.KINEMATIC -> updateKinematicMode(map, position, kinematicPoints)
                                    RecordingMode.STOP_AND_GO -> updateStopGoMode(map, position, recordingState, stopGoMarkers, stopGoPoints)
                                    null -> {}
                                }
                            }

                            map.invalidate()
                        }
                    }
                }

                // Recording controls overlay
                AnimatedVisibility(
                    visible = isMapExpanded || !recordingState.isRecording,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                            if (!recordingState.isRecording) {
                                // Mode selection
                                Text(
                                    "Recording Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    RecordingModeChip(
                                        selected = selectedMode == RecordingMode.STATIC,
                                        onClick = { selectedMode = RecordingMode.STATIC },
                                        label = "Static",
                                        icon = Icons.Default.LocationOn
                                    )
                                    RecordingModeChip(
                                        selected = selectedMode == RecordingMode.KINEMATIC,
                                        onClick = { selectedMode = RecordingMode.KINEMATIC },
                                        label = "Kinematic",
                                        icon = Icons.Default.DirectionsRun
                                    )
                                    RecordingModeChip(
                                        selected = selectedMode == RecordingMode.STOP_AND_GO,
                                        onClick = { selectedMode = RecordingMode.STOP_AND_GO },
                                        label = "Stop&Go",
                                        icon = Icons.Default.PauseCircleOutline
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Start button
                                Button(
                                    onClick = { showRecordingDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = connectionState.isConnected
                                ) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Recording")
                                }
                            } else {
                                // Recording status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Recording ${recordingState.recordingMode?.name?.replace('_', ' ')}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            viewModel.getRecordingDuration(),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Button(
                                        onClick = { viewModel.stopRecording() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop")
                                    }
                                }
                            }
                        }
                    }
                }

                // Stop&Go controls overlay
                AnimatedVisibility(
                    visible = recordingState.isRecording &&
                            recordingState.recordingMode == RecordingMode.STOP_AND_GO,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isMapExpanded) 140.dp else 8.dp)
                ) {
                    StopGoControlsCompact(
                        stopGoState = stopGoState,
                        onStop = { viewModel.addStopPoint() },
                        onGo = { viewModel.addGoPoint() }
                    )
                }

                // Position info overlay - always visible when connected
                AnimatedVisibility(
                    visible = connectionState.isConnected,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
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
                                Text(
                                    "HDOP: ${String.format("%.1f", gnssPosition.hdop)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (gnssPosition.fixType != FixType.NO_FIX) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        String.format("%.8f°", gnssPosition.latitude),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        String.format("%.8f°", gnssPosition.longitude),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        String.format("%.1fm", gnssPosition.altitude),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
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
                    Button(
                        onClick = { locationPermissionState.launchPermissionRequest() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }

    // Recording settings dialog
    if (showRecordingDialog) {
        RecordingSettingsDialog(
            mode = selectedMode,
            onDismiss = { showRecordingDialog = false },
            onConfirm = { pointName, instrumentHeight, staticDuration ->
                viewModel.startRecording(
                    mode = selectedMode,
                    pointName = pointName,
                    instrumentHeight = instrumentHeight,
                    staticDuration = staticDuration
                )
                showRecordingDialog = false
            }
        )
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
    )
}

@Composable
fun StopGoControlsCompact(
    stopGoState: RecordingViewModel.StopGoState,
    onStop: () -> Unit,
    onGo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = if (stopGoState.isInStopPhase) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stopGoState.isInStopPhase) {
                // Countdown display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stopGoState.currentPointName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${stopGoState.remainingTime}s",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = onStop,
                enabled = stopGoState.canStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop")
            }

            Button(
                onClick = onGo,
                enabled = stopGoState.canGo,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Go")
            }
        }
    }
}

// Update functions for different modes
private fun updateStaticMode(map: MapView, position: GeoPoint, fixType: FixType) {
    // Static marker (different from current position marker)
    var marker = map.overlays.filterIsInstance<Marker>().find { it.title == "Static Point" }

    if (marker == null) {
        marker = Marker(map).apply {
            title = "Static Point"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(this)
        }
    }

    marker.apply {
        this.position = position

        // Color based on fix quality
        when (fixType) {
            FixType.NO_FIX -> setTextIcon("●")
            FixType.FIX_2D -> setTextIcon("▲")
            FixType.FIX_3D, FixType.DGPS -> setTextIcon("●")
            FixType.RTK_FIXED -> setTextIcon("◆")
            FixType.RTK_FLOAT -> setTextIcon("◇")
        }
    }
}

private fun updateKinematicMode(map: MapView, position: GeoPoint, points: MutableList<GeoPoint>) {
    // Add point to track
    points.add(position)

    // Find or create polyline
    var polyline = map.overlays.filterIsInstance<Polyline>().firstOrNull()

    if (polyline == null) {
        polyline = Polyline().apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 5f
            map.overlays.add(0, this) // Add below markers
        }
    }

    polyline.setPoints(points.toList())
}

private fun updateStopGoMode(
    map: MapView,
    position: GeoPoint,
    recordingState: com.geosit.gnss.data.recording.RecordingRepository.RecordingState,
    markers: MutableMap<String, Marker>,
    points: MutableList<GeoPoint>
) {
    // Update track polyline
    var polyline = map.overlays.filterIsInstance<Polyline>().firstOrNull()

    if (polyline == null) {
        polyline = Polyline().apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 5f
            map.overlays.add(0, this) // Add below markers
        }
    }

    // Always update track
    points.add(position)
    polyline.setPoints(points.toList())

    // Update Stop markers
    recordingState.stopAndGoPoints.filter { it.action == StopGoAction.STOP }.forEach { point ->
        val markerId = point.name

        if (!markers.containsKey(markerId)) {
            val marker = Marker(map).apply {
                title = point.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setTextIcon(point.id.toString())
                map.overlays.add(this)
            }
            markers[markerId] = marker

            // Position at last known location when point was created
            marker.position = position
        }
    }
}