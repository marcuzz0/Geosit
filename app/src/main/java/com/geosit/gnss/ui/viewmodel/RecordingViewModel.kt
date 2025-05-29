package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.model.RecordingMode
import com.geosit.gnss.data.model.StopGoAction
import com.geosit.gnss.data.recording.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val recordingRepository: RecordingRepository
) : ViewModel() {
    
    val connectionState = connectionManager.connectionState
    val recordingState = recordingRepository.recordingState
    val gnssPosition = connectionManager.currentPosition
    
    init {
        // Update recording duration every second
        viewModelScope.launch {
            while (isActive) {
                if (recordingState.value.isRecording) {
                    // Force UI update for duration
                    delay(1000)
                }
            }
        }
    }
    
    fun startRecording(
        mode: RecordingMode,
        pointName: String,
        instrumentHeight: Double,
        staticDuration: Int
    ) {
        recordingRepository.startRecording(
            mode = mode,
            pointName = pointName,
            instrumentHeight = instrumentHeight,
            staticDuration = staticDuration
        )
    }
    
    fun stopRecording() {
        recordingRepository.stopRecording()
    }
    
    fun addStopPoint() {
        recordingRepository.addStopAndGoPoint(StopGoAction.STOP)
    }
    
    fun addGoPoint() {
        recordingRepository.addStopAndGoPoint(StopGoAction.GO)
    }
    
    fun getRecordingDuration(): String {
        return recordingRepository.getRecordingDurationString()
    }
    
    fun getRecordingSize(): String {
        return recordingRepository.getRecordingSizeString()
    }
    
    fun clearError() {
        // Clear error in recording state
    }
}
