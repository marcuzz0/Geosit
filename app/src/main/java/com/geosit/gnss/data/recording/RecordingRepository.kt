package com.geosit.gnss.data.recording

import android.content.Context
import android.os.Environment
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager
) {
    
    data class RecordingState(
        val isRecording: Boolean = false,
        val currentSession: RecordingSession? = null,
        val recordingMode: RecordingMode? = null,
        val stopAndGoPoints: List<StopAndGoPoint> = emptyList(),
        val recordedBytes: Long = 0,
        val recordingDuration: Long = 0, // milliseconds
        val error: String? = null
    )
    
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var recordingFile: File? = null
    private var outputStream: FileOutputStream? = null
    private var csvWriter: FileWriter? = null
    private var recordingStartTime: Long = 0
    private var stopAndGoCounter = 1
    
    // Buffer for accumulating data
    private val dataBuffer = mutableListOf<Byte>()
    
    init {
        // Observe connection data
        scope.launch {
            connectionManager.connectionState.collect { connState ->
                if (_recordingState.value.isRecording && connState.receivedData != null) {
                    recordData(connState.receivedData)
                }
            }
        }
    }
    
    fun startRecording(
        mode: RecordingMode,
        pointName: String = "",
        instrumentHeight: Double = 0.0,
        staticDuration: Int = 60
    ) {
        if (_recordingState.value.isRecording) {
            Timber.w("Already recording")
            return
        }
        
        if (!connectionManager.isConnected()) {
            _recordingState.value = _recordingState.value.copy(
                error = "No device connected"
            )
            return
        }
        
        scope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                val fileName = "${timestamp}.ubx"
                val sessionId = UUID.randomUUID().toString()
                
                // Create recording directory
                val recordingDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "GeoSit/$timestamp"
                )
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs()
                }
                
                // Create UBX file
                recordingFile = File(recordingDir, fileName)
                outputStream = FileOutputStream(recordingFile)
                
                // Create CSV file for metadata
                val csvFile = File(recordingDir, "${timestamp}.csv")
                csvWriter = FileWriter(csvFile)
                
                // Write CSV header
                writeCSVHeader(mode, pointName, instrumentHeight, staticDuration)
                
                // Clear data buffer
                dataBuffer.clear()
                connectionManager.clearBuffer()
                
                recordingStartTime = System.currentTimeMillis()
                stopAndGoCounter = 1
                
                val session = RecordingSession(
                    id = sessionId,
                    mode = mode,
                    startTime = Date(),
                    fileName = fileName,
                    pointName = pointName,
                    instrumentHeight = instrumentHeight,
                    staticDuration = staticDuration
                )
                
                _recordingState.value = RecordingState(
                    isRecording = true,
                    currentSession = session,
                    recordingMode = mode,
                    stopAndGoPoints = emptyList(),
                    recordedBytes = 0,
                    error = null
                )
                
                Timber.d("Recording started: $fileName, mode: $mode")
                
                // For static mode, automatically stop after duration
                if (mode == RecordingMode.STATIC) {
                    scheduleStaticStop(staticDuration)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                _recordingState.value = _recordingState.value.copy(
                    error = "Failed to start recording: ${e.message}"
                )
            }
        }
    }
    
    fun stopRecording() {
        if (!_recordingState.value.isRecording) {
            Timber.w("Not recording")
            return
        }
        
        scope.launch {
            try {
                // Close files
                outputStream?.close()
                csvWriter?.close()
                
                val session = _recordingState.value.currentSession
                if (session != null) {
                    val endTime = Date()
                    val fileSize = recordingFile?.length() ?: 0
                    
                    // Write final CSV data
                    writeFinalCSVData(session, endTime)
                    
                    val completedSession = session.copy(
                        endTime = endTime,
                        isCompleted = true,
                        fileSize = fileSize,
                        dataPointsCount = dataBuffer.size
                    )
                    
                    _recordingState.value = RecordingState(
                        isRecording = false,
                        currentSession = completedSession,
                        recordingMode = null,
                        error = null
                    )
                    
                    Timber.d("Recording stopped: ${session.fileName}, size: $fileSize bytes")
                }
                
                // Reset
                outputStream = null
                csvWriter = null
                recordingFile = null
                dataBuffer.clear()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop recording")
                _recordingState.value = _recordingState.value.copy(
                    error = "Failed to stop recording: ${e.message}"
                )
            }
        }
    }
    
    fun addStopAndGoPoint(action: StopGoAction, pointName: String? = null) {
        if (_recordingState.value.recordingMode != RecordingMode.STOP_AND_GO) {
            Timber.w("Not in Stop&Go mode")
            return
        }
        
        val currentState = _recordingState.value
        val instrumentHeight = currentState.currentSession?.instrumentHeight ?: 0.0
        
        val point = StopAndGoPoint(
            id = stopAndGoCounter,
            name = pointName ?: "Point$stopAndGoCounter",
            timestamp = Date(),
            action = action,
            instrumentHeight = instrumentHeight
        )
        
        // Write to CSV
        scope.launch {
            try {
                val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                
                csvWriter?.appendLine(
                    "${point.name},${dateFormat.format(point.timestamp)},${action.name},${point.instrumentHeight}"
                )
                csvWriter?.flush()
                
                val updatedPoints = currentState.stopAndGoPoints + point
                _recordingState.value = currentState.copy(stopAndGoPoints = updatedPoints)
                
                if (action == StopGoAction.GO) {
                    stopAndGoCounter++
                }
                
                Timber.d("Stop&Go point added: $point")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to add Stop&Go point")
            }
        }
    }
    
    private fun recordData(data: ByteArray) {
        scope.launch {
            try {
                outputStream?.write(data)
                dataBuffer.addAll(data.toList())
                
                val currentBytes = _recordingState.value.recordedBytes + data.size
                val duration = System.currentTimeMillis() - recordingStartTime
                
                _recordingState.value = _recordingState.value.copy(
                    recordedBytes = currentBytes,
                    recordingDuration = duration
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to record data")
            }
        }
    }
    
    private fun writeCSVHeader(
        mode: RecordingMode,
        pointName: String,
        instrumentHeight: Double,
        staticDuration: Int
    ) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        
        csvWriter?.apply {
            appendLine("# Session: ${recordingFile?.name}")
            appendLine("# Mode: ${mode.name.replace('_', ' ')}")
            appendLine("# Start: ${dateFormat.format(Date())}")
            
            when (mode) {
                RecordingMode.STATIC -> {
                    appendLine("# Point Name: $pointName")
                    appendLine("# Duration: ${staticDuration}s")
                    appendLine("# Instrument Height: $instrumentHeight m")
                }
                RecordingMode.KINEMATIC -> {
                    appendLine("# Track Name: $pointName")
                    appendLine("# Instrument Height: $instrumentHeight m")
                }
                RecordingMode.STOP_AND_GO -> {
                    appendLine("")
                    appendLine("Point,Timestamp,Action,Height")
                }
            }
            flush()
        }
    }
    
    private fun writeFinalCSVData(session: RecordingSession, endTime: Date) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        
        val duration = (endTime.time - session.startTime.time) / 1000 // seconds
        
        csvWriter?.apply {
            if (session.mode != RecordingMode.STOP_AND_GO) {
                appendLine("# End: ${dateFormat.format(endTime)}")
                appendLine("# Duration: ${duration}s")
            }
            flush()
        }
    }
    
    private fun scheduleStaticStop(durationSeconds: Int) {
        scope.launch {
            kotlinx.coroutines.delay(durationSeconds * 1000L)
            if (_recordingState.value.isRecording && 
                _recordingState.value.recordingMode == RecordingMode.STATIC) {
                stopRecording()
            }
        }
    }
    
    fun getRecordingDurationString(): String {
        val duration = _recordingState.value.recordingDuration
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = duration / (1000 * 60 * 60)
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    fun getRecordingSizeString(): String {
        val bytes = _recordingState.value.recordedBytes
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
