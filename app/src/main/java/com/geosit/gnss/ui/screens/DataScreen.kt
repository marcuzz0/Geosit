package com.geosit.gnss.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var recordingToDelete by remember { mutableStateOf<RecordingFile?>(null) }
    var selectedRecording by remember { mutableStateOf<RecordingFile?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }

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
                                showDeleteDialog = true
                            },
                            enabled = selectedRecordings.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        // Menu button
                        var showMenu by remember { mutableStateOf(false) }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    viewModel.loadRecordings()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                            if (recordings.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Delete All") },
                                    onClick = {
                                        showDeleteAllDialog = true
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Storage Info") },
                                onClick = {
                                    scope.launch {
                                        val info = viewModel.getStorageInfo()
                                        snackbarHostState.showSnackbar(
                                            message = info,
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                }
                            )
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
                            if (!isSelectionMode) {
                                selectedRecording = recording
                                showOptionsDialog = true
                            } else {
                                viewModel.toggleSelection(recording)
                            }
                        }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recordings") },
            text = {
                Text("Are you sure you want to delete ${selectedRecordings.size} recording(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteSelected()
                            showDeleteDialog = false
                            snackbarHostState.showSnackbar(
                                message = "Recordings deleted",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete all confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Recordings") },
            text = {
                Text("Are you sure you want to delete ALL recordings (${recordings.size} files)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteAllRecordings()
                            showDeleteAllDialog = false
                            snackbarHostState.showSnackbar(
                                message = "All recordings deleted",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete single recording dialog
    recordingToDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Delete Recording") },
            text = {
                Text("Are you sure you want to delete \"${recording.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteRecording(recording)
                            recordingToDelete = null
                            selectedRecording = null
                            snackbarHostState.showSnackbar(
                                message = "Recording deleted",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Options dialog for single recording
    if (showOptionsDialog && selectedRecording != null) {
        AlertDialog(
            onDismissRequest = {
                showOptionsDialog = false
                selectedRecording = null
            },
            title = {
                Text(
                    selectedRecording!!.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Open") },
                        leadingContent = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.openRecording(selectedRecording!!)
                            showOptionsDialog = false
                            selectedRecording = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.shareRecording(selectedRecording!!)
                            showOptionsDialog = false
                            selectedRecording = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Select Multiple") },
                        leadingContent = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.startSelection(selectedRecording!!)
                            showOptionsDialog = false
                            selectedRecording = null
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable {
                            recordingToDelete = selectedRecording
                            showOptionsDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showOptionsDialog = false
                        selectedRecording = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordingCard(
    recording: RecordingFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
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