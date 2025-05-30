package com.geosit.gnss.ui.viewmodel

import android.content.Context
import android.content.Intent
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
                        // Cerca il file .ubx nella directory
                        val ubxFile = sessionDir.listFiles()?.find { it.extension == "ubx" }
                        val csvFile = sessionDir.listFiles()?.find { it.extension == "csv" }
                        
                        if (ubxFile != null) {
                            val recording = parseRecordingFile(ubxFile, csvFile)
                            recording?.let { recordingFiles.add(it) }
                        }
                    }
                    
                    // Ordina per data (più recenti prima)
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
            val fileName = ubxFile.nameWithoutExtension
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val date = dateFormat.parse(fileName) ?: Date()
            
            // Determina il modo dalla lettura del CSV se disponibile
            var mode = RecordingMode.KINEMATIC // Default
            var pointName = fileName
            
            csvFile?.let { csv ->
                val lines = csv.readLines()
                lines.find { it.startsWith("# Mode:") }?.let { modeLine ->
                    mode = when {
                        modeLine.contains("STATIC") -> RecordingMode.STATIC
                        modeLine.contains("KINEMATIC") -> RecordingMode.KINEMATIC
                        modeLine.contains("STOP & GO") -> RecordingMode.STOP_AND_GO
                        else -> RecordingMode.KINEMATIC
                    }
                }
                
                // Cerca il nome del punto/track
                lines.find { it.startsWith("# Point Name:") || 
                           it.startsWith("# Track Name:") || 
                           it.startsWith("# Session Name:") }?.let { nameLine ->
                    val name = nameLine.substringAfter(":").trim()
                    if (name.isNotEmpty()) {
                        pointName = name
                    }
                }
            }
            
            RecordingFile(
                id = ubxFile.absolutePath,
                name = pointName,
                mode = mode,
                date = date,
                size = ubxFile.length(),
                duration = 0, // TODO: calcola dalla differenza di timestamp nel CSV
                filePath = ubxFile.absolutePath,
                csvPath = csvFile?.absolutePath
            )
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
        
        // Esci dalla modalità selezione se non ci sono più elementi selezionati
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
        // Apri il file con un'app esterna
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(Intent.createChooser(intent, "Open with"))
                
            } catch (e: Exception) {
                Timber.e(e, "Error opening file")
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
                
                val files = selected.mapNotNull { recording ->
                    val file = File(recording.filePath)
                    if (file.exists()) file else null
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
    
    fun deleteSelected() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val selected = _recordings.value.filter { 
                        _selectedRecordings.value.contains(it.id) 
                    }
                    
                    selected.forEach { recording ->
                        // Elimina l'intera directory della sessione
                        val file = File(recording.filePath)
                        val sessionDir = file.parentFile
                        
                        sessionDir?.deleteRecursively()
                        
                        Timber.d("Deleted recording: ${recording.name}")
                    }
                    
                    withContext(Dispatchers.Main) {
                        clearSelection()
                        loadRecordings() // Ricarica la lista
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error deleting files")
                }
            }
        }
    }
}
