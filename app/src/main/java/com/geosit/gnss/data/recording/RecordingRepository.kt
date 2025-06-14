package com.geosit.gnss.data.recording

import android.content.Context
import android.os.Environment
import com.geosit.gnss.ui.screens.connection.ConnectionManager
import com.geosit.gnss.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class RecordingRepository(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {

    data class RecordingState(
        val isRecording: Boolean = false,
        val currentSession: RecordingSession? = null,
        val recordingMode: RecordingMode? = null,
        val stopAndGoPoints: List<StopAndGoPoint> = emptyList(),
        val recordedBytes: Long = 0,
        val recordingDuration: Long = 0, // milliseconds
        val dataReceivedCount: Long = 0,
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

    private fun recordData(data: ByteArray) {
        scope.launch {
            try {
                val stream = outputStream
                if (stream == null || !_recordingState.value.isRecording) {
                    Timber.w("Cannot record data - not recording or stream is null")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    stream.write(data)
                    stream.flush()
                }

                val currentBytes = _recordingState.value.recordedBytes + data.size
                val duration = System.currentTimeMillis() - recordingStartTime
                val currentCount = _recordingState.value.dataReceivedCount + 1

                _recordingState.value = _recordingState.value.copy(
                    recordedBytes = currentBytes,
                    recordingDuration = duration,
                    dataReceivedCount = currentCount
                )

            } catch (e: Exception) {
                Timber.e(e, "Failed to record data: ${e.message}")
                // Don't stop recording on write error, just log it
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
                val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir

                val recordingDir = File(documentsDir, "GeoSit/$timestamp")
                if (!recordingDir.exists()) {
                    if (!recordingDir.mkdirs()) {
                        Timber.e("Failed to create recording directory: ${recordingDir.absolutePath}")
                        _recordingState.value = _recordingState.value.copy(
                            error = "Failed to create recording directory"
                        )
                        return@launch
                    }
                }
                Timber.d("Recording directory: ${recordingDir.absolutePath}")

                // Create UBX file
                recordingFile = File(recordingDir, fileName)
                try {
                    outputStream = FileOutputStream(recordingFile)
                    Timber.d("Created UBX file: ${recordingFile?.absolutePath}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create UBX file")
                    _recordingState.value = _recordingState.value.copy(
                        error = "Failed to create recording file: ${e.message}"
                    )
                    return@launch
                }

                // Create CSV file for metadata
                val csvFile = File(recordingDir, "${timestamp}.csv")
                try {
                    csvWriter = FileWriter(csvFile)
                    Timber.d("Created CSV file: ${csvFile.absolutePath}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create CSV file")
                    outputStream?.close()
                    _recordingState.value = _recordingState.value.copy(
                        error = "Failed to create CSV file: ${e.message}"
                    )
                    return@launch
                }

                // Write CSV header
                writeCSVHeader(mode, pointName, instrumentHeight, staticDuration)

                // Clear data buffer
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
                val session = _recordingState.value.currentSession
                val endTime = Date()

                // Write final CSV data BEFORE closing files
                if (session != null && csvWriter != null) {
                    withContext(Dispatchers.IO) {
                        writeFinalCSVData(session, endTime)
                    }
                }

                // Now close files
                withContext(Dispatchers.IO) {
                    try {
                        outputStream?.flush()
                        outputStream?.close()
                        Timber.d("Output stream closed")
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing output stream")
                    }

                    try {
                        csvWriter?.flush()
                        csvWriter?.close()
                        Timber.d("CSV writer closed")
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing CSV writer")
                    }
                }

                if (session != null) {
                    val fileSize = recordingFile?.length() ?: 0

                    val completedSession = session.copy(
                        endTime = endTime,
                        isCompleted = true,
                        fileSize = fileSize,
                        dataPointsCount = _recordingState.value.dataReceivedCount.toInt()
                    )

                    Timber.d("Recording stopped successfully: ${session.fileName}, size: $fileSize bytes, points: ${_recordingState.value.dataReceivedCount}")
                }

                // Reset state
                _recordingState.value = RecordingState(
                    isRecording = false,
                    currentSession = null,
                    recordingMode = null,
                    error = null
                )

                // Clear references
                outputStream = null
                csvWriter = null
                recordingFile = null

            } catch (e: Exception) {
                Timber.e(e, "Failed to stop recording: ${e.message}")

                // Force reset state even on error
                _recordingState.value = RecordingState(
                    isRecording = false,
                    currentSession = null,
                    recordingMode = null,
                    error = "Failed to stop recording: ${e.message}"
                )

                // Clear references
                outputStream = null
                csvWriter = null
                recordingFile = null
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

                withContext(Dispatchers.IO) {
                    csvWriter?.appendLine(
                        "${point.name},${dateFormat.format(point.timestamp)},${action.name},${point.instrumentHeight}"
                    )
                    csvWriter?.flush()
                }

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
        try {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")

            val duration = (endTime.time - session.startTime.time) / 1000 // seconds

            csvWriter?.apply {
                if (session.mode != RecordingMode.STOP_AND_GO) {
                    appendLine("")
                    appendLine("# End: ${dateFormat.format(endTime)}")
                    appendLine("# Duration: ${duration}s")
                    appendLine("# Data points: ${_recordingState.value.dataReceivedCount}")
                }
                flush()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing final CSV data")
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
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}