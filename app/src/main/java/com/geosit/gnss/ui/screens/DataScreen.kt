package com.geosit.gnss.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.RecordingFile
import com.geosit.gnss.ui.viewmodel.DataViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    viewModel: DataViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRecordings by viewModel.selectedRecordings.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            "${selectedRecordings.size} selected"
                        } else {
                            "Recorded Data"
                        },
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.shareSelected()
                                }
                            },
                            enabled = selectedRecordings.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.deleteSelected()
                                }
                            },
                            enabled = selectedRecordings.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadRecordings() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                recordings.isEmpty() -> {
                    EmptyDataScreen()
                }
                else -> {
                    RecordingsList(
                        recordings = recordings,
                        selectedRecordings = selectedRecordings,
                        isSelectionMode = isSelectionMode,
                        onRecordingClick = { recording ->
                            if (isSelectionMode) {
                                viewModel.toggleSelection(recording)
                            } else {
                                viewModel.openRecording(recording)
                            }
                        },
                        onRecordingLongClick = { recording ->
                            viewModel.startSelection(recording)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDataScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No recordings yet",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Start recording to see data here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsList(
    recordings: List<RecordingFile>,
    selectedRecordings: Set<String>,
    isSelectionMode: Boolean,
    onRecordingClick: (RecordingFile) -> Unit,
    onRecordingLongClick: (RecordingFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recordings) { recording ->
            RecordingCard(
                recording = recording,
                isSelected = selectedRecordings.contains(recording.id),
                isSelectionMode = isSelectionMode,
                onClick = { onRecordingClick(recording) },
                onLongClick = { onRecordingLongClick(recording) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingCard(
    recording: RecordingFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox
            AnimatedVisibility(visible = isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // Mode icon
            Icon(
                when (recording.mode) {
                    RecordingMode.STATIC -> Icons.Default.LocationOn
                    RecordingMode.KINEMATIC -> Icons.Default.DirectionsRun
                    RecordingMode.STOP_AND_GO -> Icons.Default.PauseCircleOutline
                },
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 16.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            // Recording info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    recording.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    recording.mode.name.replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            .format(recording.date),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatFileSize(recording.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (recording.duration > 0) {
                        Text(
                            formatDuration(recording.duration),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // More options
            if (!isSelectionMode) {
                IconButton(onClick = { /* Show options menu */ }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}