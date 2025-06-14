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

    // Stop & Go state
    data class StopGoState(
        val isInStopPhase: Boolean = false,
        val remainingTime: Int = 0,
        val currentPointName: String = "",
        val canStop: Boolean = true,
        val canGo: Boolean = false
    )

    private val _stopGoState = MutableStateFlow(StopGoState())
    val stopGoState: StateFlow<StopGoState> = _stopGoState.asStateFlow()

    private var timerJob: Job? = null
    private var stopGoTimerJob: Job? = null
    private var stopGoDuration: Int = 30 // Default duration

    init {
        // Monitora lo stato di registrazione per avviare/fermare il timer
        viewModelScope.launch {
            recordingRepository.recordingState.collect { state ->
                if (state.isRecording && timerJob == null) {
                    startTimer()
                } else if (!state.isRecording && timerJob != null) {
                    stopTimer()
                }

                // Reset Stop&Go state when recording mode changes
                if (state.recordingMode != RecordingMode.STOP_AND_GO) {
                    _stopGoState.value = StopGoState()
                    stopGoTimerJob?.cancel()
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
                // Save Stop & Go duration if applicable
                if (mode == RecordingMode.STOP_AND_GO) {
                    stopGoDuration = staticDuration
                    // Initialize Stop&Go state
                    _stopGoState.value = StopGoState(
                        isInStopPhase = false,
                        remainingTime = 0,
                        currentPointName = "",
                        canStop = true,
                        canGo = false
                    )
                }

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
                _stopGoState.value = StopGoState() // Reset Stop & Go state
                stopGoTimerJob?.cancel()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping recording")
            }
        }
    }

    fun addStopPoint() {
        if (_stopGoState.value.isInStopPhase) {
            return // Already in stop phase
        }

        viewModelScope.launch {
            val pointName = "Point${recordingState.value.stopAndGoPoints.count { it.action == StopGoAction.STOP } + 1}"

            _stopGoState.value = StopGoState(
                isInStopPhase = true,
                remainingTime = stopGoDuration,
                currentPointName = pointName,
                canStop = false,
                canGo = false
            )

            recordingRepository.addStopAndGoPoint(StopGoAction.STOP, pointName)

            // Start countdown timer
            stopGoTimerJob?.cancel()
            stopGoTimerJob = viewModelScope.launch {
                for (i in stopGoDuration downTo 1) {
                    _stopGoState.value = _stopGoState.value.copy(remainingTime = i)
                    delay(1000)
                }

                // Countdown complete - enable GO button
                _stopGoState.value = _stopGoState.value.copy(
                    remainingTime = 0,
                    canStop = false,
                    canGo = true
                )
            }
        }
    }

    fun addGoPoint() {
        if (!_stopGoState.value.isInStopPhase || !_stopGoState.value.canGo) {
            return
        }

        viewModelScope.launch {
            recordingRepository.addStopAndGoPoint(StopGoAction.GO, _stopGoState.value.currentPointName)

            // Reset to allow new stop
            _stopGoState.value = StopGoState(
                isInStopPhase = false,
                remainingTime = 0,
                currentPointName = "",
                canStop = true,
                canGo = false
            )

            stopGoTimerJob?.cancel()
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
        stopGoTimerJob?.cancel()

        // Stop recording if still active
        if (recordingState.value.isRecording) {
            stopRecording()
        }
    }
}