package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.StopGoAction
import com.geosit.gnss.data.recording.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    // Stati esposti alla UI
    val connectionState = connectionManager.connectionState
    val gnssPosition = connectionManager.currentPosition

    // Combina recording state con timer tick per aggiornamenti UI
    private val _uiTick = MutableStateFlow(0L)

    val recordingState = combine(
        recordingRepository.recordingState,
        _uiTick
    ) { recording, _ ->
        recording
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = recordingRepository.recordingState.value
    )

    private var timerJob: Job? = null

    init {
        // Monitora lo stato di registrazione per avviare/fermare il timer
        viewModelScope.launch {
            recordingRepository.recordingState.collect { state ->
                if (state.isRecording && timerJob == null) {
                    startTimer()
                } else if (!state.isRecording && timerJob != null) {
                    stopTimer()
                }
            }
        }
    }

    private fun startTimer() {
        Timber.d("Starting recording timer")
        timerJob = viewModelScope.launch {
            while (isActive) {
                _uiTick.value = System.currentTimeMillis()
                delay(100) // Aggiorna ogni 100ms per un timer fluido
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun startRecording(
        mode: RecordingMode,
        pointName: String,
        instrumentHeight: Double,
        staticDuration: Int
    ) {
        viewModelScope.launch {
            try {
                recordingRepository.startRecording(
                    mode = mode,
                    pointName = pointName,
                    instrumentHeight = instrumentHeight,
                    staticDuration = staticDuration
                )
            } catch (e: Exception) {
                Timber.e(e, "Error starting recording")
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                recordingRepository.stopRecording()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping recording")
            }
        }
    }

    fun addStopPoint() {
        viewModelScope.launch {
            recordingRepository.addStopAndGoPoint(StopGoAction.STOP)
        }
    }

    fun addGoPoint() {
        viewModelScope.launch {
            recordingRepository.addStopAndGoPoint(StopGoAction.GO)
        }
    }

    fun getRecordingDuration(): String {
        val duration = recordingState.value.recordingDuration
        return formatDuration((duration / 1000).toInt())
    }

    fun getRecordingSize(): String {
        val bytes = recordingState.value.recordedBytes
        return formatFileSize(bytes)
    }

    fun getDataRate(): String {
        return connectionManager.getDataRate()
    }

    fun clearError() {
        // TODO: Implementare nel repository se necessario
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        recordingRepository.cleanup()
    }
}