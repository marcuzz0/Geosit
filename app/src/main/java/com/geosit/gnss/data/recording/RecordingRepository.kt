package com.geosit.gnss.data.recording

import android.content.Context
import android.os.Environment
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Listener per i dati ricevuti
    private val dataReceivedListener = object : ConnectionManager.DataReceivedListener {
        override fun onDataReceived(data: ByteArray) {
            Timber.d("DataReceivedListener called with ${data.size} bytes")
            recordData(data)
        }
    }

    init {
        // Registra il listener per i dati
        connectionManager.addDataListener(dataReceivedListener)
        Timber.d("RecordingRepository initialized - listener registered")

        // Verifica che il ConnectionManager sia lo stesso istanza
        Timber.d("ConnectionManager instance: ${connectionManager.hashCode()}")
    }

    // Metodo privato per registrare dati (chiamato dal listener)
    private fun recordData(data: ByteArray) {
        if (!_recordingState.value.isRecording) {
            Timber.d("RecordData called but not recording")
            return
        }

        Timber.d("Recording ${data.size} bytes")

        scope.launch {
            try {
                outputStream?.write(data)
                outputStream?.flush() // Importante: flush dei dati

                val currentBytes = _recordingState.value.recordedBytes + data.size
                val duration = System.currentTimeMillis() - recordingStartTime
                val currentCount = _recordingState.value.dataReceivedCount + 1

                _recordingState.value = _recordingState.value.copy(
                    recordedBytes = currentBytes,
                    recordingDuration = duration,
                    dataReceivedCount = currentCount
                )

                Timber.d("Updated state - bytes: $currentBytes, duration: ${duration/1000}s")

            } catch (e: Exception) {
                Timber.e(e, "Failed to record data")
                // Non fermare la registrazione per errori minori
                if (e.message?.contains("closed") == true) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value = _recordingState.value.copy(
                            error = "Recording error: ${e.message}"
                        )
                    }
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

                // Create recording directory - usa directory app-specific
                val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir // Fallback se external storage non disponibile

                val recordingDir = File(documentsDir, "GeoSit/$timestamp")
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs()
                }

                // Create UBX file
                recordingFile = File(recordingDir, fileName)
                outputStream = FileOutputStream(recordingFile)

                // Create CSV file for metadata
                val csvFile = File(recordingDir, "${timestamp}.csv")
                csvWriter = FileWriter(csvFile)

                // Write CSV header sul thread IO
                withContext(Dispatchers.IO) {
                    writeCSVHeader(mode, pointName, instrumentHeight, staticDuration)
                }

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

                withContext(Dispatchers.Main) {
                    _recordingState.value = RecordingState(
                        isRecording = true,
                        currentSession = session,
                        recordingMode = mode,
                        stopAndGoPoints = emptyList(),
                        recordedBytes = 0,
                        error = null
                    )
                }

                Timber.d("Recording started: $fileName, mode: $mode")

                // For static mode, automatically stop after duration
                if (mode == RecordingMode.STATIC) {
                    scheduleStaticStop(staticDuration)
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                withContext(Dispatchers.Main) {
                    _recordingState.value = _recordingState.value.copy(
                        error = "Failed to start recording: ${e.message}",
                        isRecording = false
                    )
                }
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

                // Prima aggiorna lo stato per fermare la registrazione
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false
                )

                // Poi chiudi i file
                try {
                    outputStream?.flush()
                    outputStream?.close()
                    csvWriter?.close()
                } catch (e: Exception) {
                    Timber.e(e, "Error closing files")
                }

                if (session != null) {
                    val fileSize = recordingFile?.length() ?: 0

                    val completedSession = session.copy(
                        endTime = endTime,
                        isCompleted = true,
                        fileSize = fileSize,
                        dataPointsCount = (_recordingState.value.recordedBytes / 100).toInt()
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

            } catch (e: Exception) {
                Timber.e(e, "Failed to stop recording")
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
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

    // Cleanup quando il repository viene distrutto
    fun cleanup() {
        connectionManager.removeDataListener(dataReceivedListener)
        scope.launch {
            if (_recordingState.value.isRecording) {
                stopRecording()
            }
        }
    }
}