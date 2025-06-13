package com.geosit.gnss.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.RecordingFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedRecordings = MutableStateFlow<Set<String>>(emptySet())
    val selectedRecordings: StateFlow<Set<String>> = _selectedRecordings.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true

            withContext(Dispatchers.IO) {
                try {
                    val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    val geoSitDir = File(documentsDir, "GeoSit")

                    if (!geoSitDir.exists()) {
                        _recordings.value = emptyList()
                        return@withContext
                    }

                    val recordingFiles = mutableListOf<RecordingFile>()

                    geoSitDir.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
                        // Find .ubx file in directory
                        val ubxFile = sessionDir.listFiles()?.find { it.extension == "ubx" }
                        val csvFile = sessionDir.listFiles()?.find { it.extension == "csv" }

                        if (ubxFile != null) {
                            val recording = parseRecordingFile(ubxFile, csvFile)
                            recording?.let { recordingFiles.add(it) }
                        }
                    }

                    // Sort by date (most recent first)
                    recordingFiles.sortByDescending { it.date }

                    withContext(Dispatchers.Main) {
                        _recordings.value = recordingFiles
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error loading recordings")
                }
            }

            _isLoading.value = false
        }
    }

    private fun parseRecordingFile(ubxFile: File, csvFile: File?): RecordingFile? {
        return try {
            Timber.d("Parsing recording file: ${ubxFile.absolutePath}")
            Timber.d("File size: ${ubxFile.length()} bytes")
            Timber.d("File exists: ${ubxFile.exists()}")
            Timber.d("Can read: ${ubxFile.canRead()}")

            val fileName = ubxFile.nameWithoutExtension
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val date = dateFormat.parse(fileName) ?: Date()

            // Determine mode from CSV if available
            var mode = RecordingMode.KINEMATIC // Default
            var pointName = fileName
            var duration = 0

            csvFile?.let { csv ->
                Timber.d("Found CSV file: ${csv.absolutePath}")
                val lines = csv.readLines()
                lines.find { it.startsWith("# Mode:") }?.let { modeLine ->
                    mode = when {
                        modeLine.contains("STATIC") -> RecordingMode.STATIC
                        modeLine.contains("KINEMATIC") -> RecordingMode.KINEMATIC
                        modeLine.contains("STOP & GO") -> RecordingMode.STOP_AND_GO
                        else -> RecordingMode.KINEMATIC
                    }
                }

                // Look for point/track name
                lines.find { it.startsWith("# Point Name:") ||
                        it.startsWith("# Track Name:") ||
                        it.startsWith("# Session Name:") }?.let { nameLine ->
                    val name = nameLine.substringAfter(":").trim()
                    if (name.isNotEmpty()) {
                        pointName = name
                    }
                }

                // Extract duration if available
                lines.find { it.startsWith("# Duration:") }?.let { durationLine ->
                    val durationStr = durationLine.substringAfter(":").trim()
                    duration = durationStr.removeSuffix("s").toIntOrNull() ?: 0
                }
            }

            val recording = RecordingFile(
                id = ubxFile.absolutePath,
                name = pointName,
                mode = mode,
                date = date,
                size = ubxFile.length(),
                duration = duration,
                filePath = ubxFile.absolutePath,
                csvPath = csvFile?.absolutePath
            )

            Timber.d("Parsed recording: $recording")
            return recording

        } catch (e: Exception) {
            Timber.e(e, "Error parsing recording file: ${ubxFile.name}")
            null
        }
    }

    fun toggleSelection(recording: RecordingFile) {
        val current = _selectedRecordings.value.toMutableSet()
        if (current.contains(recording.id)) {
            current.remove(recording.id)
        } else {
            current.add(recording.id)
        }
        _selectedRecordings.value = current

        // Exit selection mode if no items selected
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun startSelection(recording: RecordingFile) {
        _isSelectionMode.value = true
        _selectedRecordings.value = setOf(recording.id)
    }

    fun clearSelection() {
        _selectedRecordings.value = emptySet()
        _isSelectionMode.value = false
    }

    fun openRecording(recording: RecordingFile) {
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                if (!file.exists()) {
                    _errorMessage.value = "File not found: ${file.name}"
                    Timber.e("File not found: ${recording.filePath}")
                    return@launch
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Determine MIME type
                val mimeType = when (file.extension.lowercase()) {
                    "ubx" -> "application/octet-stream"
                    "csv" -> "text/csv"
                    else -> "*/*"
                }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // If no app can handle the file, show chooser
                    val chooserIntent = Intent.createChooser(intent, "Open with")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Cannot open file: ${e.message}"
                Timber.e(e, "Error opening file: ${e.message}")
            }
        }
    }

    fun shareRecording(recording: RecordingFile) {
        viewModelScope.launch {
            try {
                val files = mutableListOf<File>()

                // Add UBX file
                val ubxFile = File(recording.filePath)
                if (!ubxFile.exists()) {
                    _errorMessage.value = "Recording file not found"
                    Timber.e("UBX file not found: ${recording.filePath}")
                    return@launch
                }
                files.add(ubxFile)

                // Add CSV file if exists
                recording.csvPath?.let {
                    val csvFile = File(it)
                    if (csvFile.exists()) {
                        files.add(csvFile)
                        Timber.d("Including CSV file in share")
                    }
                }

                val uris = ArrayList<Uri>()
                files.forEach { file ->
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        uris.add(uri)
                        Timber.d("Added URI for sharing: $uri")
                    } catch (e: Exception) {
                        Timber.e(e, "Error creating URI for file: ${file.name}")
                    }
                }

                if (uris.isEmpty()) {
                    _errorMessage.value = "No files available to share"
                    Timber.e("No valid URIs for sharing")
                    return@launch
                }

                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_STREAM, uris[0])
                        putExtra(Intent.EXTRA_SUBJECT, "GNSS Recording - ${recording.name}")
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_SUBJECT, "GNSS Recording - ${recording.name}")
                    }
                }

                intent.apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooserIntent = Intent.createChooser(intent, "Share recording")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(chooserIntent)

                Timber.d("Share intent launched successfully")

            } catch (e: Exception) {
                _errorMessage.value = "Cannot share file: ${e.message}"
                Timber.e(e, "Error sharing file: ${e.message}")
            }
        }
    }

    fun shareSelected() {
        viewModelScope.launch {
            try {
                val selected = _recordings.value.filter {
                    _selectedRecordings.value.contains(it.id)
                }

                if (selected.isEmpty()) return@launch

                val files = selected.flatMap { recording ->
                    val list = mutableListOf<File>()
                    val ubxFile = File(recording.filePath)
                    if (ubxFile.exists()) list.add(ubxFile)

                    recording.csvPath?.let {
                        val csvFile = File(it)
                        if (csvFile.exists()) list.add(csvFile)
                    }
                    list
                }

                val uris = files.map { file ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/octet-stream"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(Intent.createChooser(intent, "Share recordings"))

                clearSelection()

            } catch (e: Exception) {
                Timber.e(e, "Error sharing files")
            }
        }
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Delete entire session directory
                    val file = File(recording.filePath)
                    val sessionDir = file.parentFile

                    sessionDir?.deleteRecursively()

                    Timber.d("Deleted recording: ${recording.name}")

                    withContext(Dispatchers.Main) {
                        loadRecordings() // Reload list
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error deleting file")
                }
            }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val selected = _recordings.value.filter {
                        _selectedRecordings.value.contains(it.id)
                    }

                    selected.forEach { recording ->
                        // Delete entire session directory
                        val file = File(recording.filePath)
                        val sessionDir = file.parentFile

                        sessionDir?.deleteRecursively()

                        Timber.d("Deleted recording: ${recording.name}")
                    }

                    withContext(Dispatchers.Main) {
                        clearSelection()
                        loadRecordings() // Reload list
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error deleting files")
                }
            }
        }
    }

    fun deleteAllRecordings() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    val geoSitDir = File(documentsDir, "GeoSit")

                    if (geoSitDir.exists()) {
                        val deleted = geoSitDir.deleteRecursively()

                        if (deleted) {
                            Timber.d("All recordings deleted successfully")
                        } else {
                            Timber.e("Failed to delete all recordings")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        clearSelection()
                        loadRecordings() // Reload list (will be empty)
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error deleting all recordings")
                }
            }
        }
    }

    fun getStorageInfo(): String {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val geoSitDir = File(documentsDir, "GeoSit")

        if (!geoSitDir.exists()) {
            return "Storage path: ${geoSitDir.absolutePath}\nNo recordings found"
        }

        var totalSize = 0L
        var fileCount = 0
        var sessionCount = 0
        val fileDetails = mutableListOf<String>()

        geoSitDir.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
            sessionCount++
            sessionDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    fileCount++
                    fileDetails.add("${file.name} (${formatFileSize(file.length())})")
                }
            }
        }

        val sizeStr = when {
            totalSize < 1024 -> "$totalSize B"
            totalSize < 1024 * 1024 -> "${totalSize / 1024} KB"
            else -> String.format("%.2f MB", totalSize / (1024.0 * 1024.0))
        }

        return buildString {
            appendLine("Storage path:")
            appendLine(geoSitDir.absolutePath)
            appendLine("\nSessions: $sessionCount")
            appendLine("Total files: $fileCount")
            appendLine("Total size: $sizeStr")

            if (fileDetails.isNotEmpty() && fileDetails.size <= 10) {
                appendLine("\nFiles:")
                fileDetails.forEach { appendLine("• $it") }
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}