package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.recording.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    val connectionState = connectionManager.connectionState
    val gnssPosition = connectionManager.currentPosition
    val recordingState = recordingRepository.recordingState

    // Get satellites directly from connection manager
    val satellites = connectionManager.satellites

    // Get GNSS statistics from connection manager
    val gnssStatistics = connectionManager.gnssStatistics

    fun getRecordingDuration(): String {
        return recordingRepository.getRecordingDurationString()
    }

    fun getRecordingSize(): String {
        return recordingRepository.getRecordingSizeString()
    }

    fun configureGnssReceiver() {
        viewModelScope.launch {
            connectionManager.configureGnssReceiver()
        }
    }
}