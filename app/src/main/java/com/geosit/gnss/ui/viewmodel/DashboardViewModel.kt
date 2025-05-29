package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.gnss.SatelliteInfo
import com.geosit.gnss.data.recording.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Mock satellite data for now
    private val _satellites = MutableStateFlow<List<SatelliteInfo>>(emptyList())
    val satellites: StateFlow<List<SatelliteInfo>> = _satellites.asStateFlow()

    data class GnssStatistics(
        val totalMessages: Int = 0,
        val lastMessageType: String = "None"
    )

    private val _gnssStatistics = MutableStateFlow(GnssStatistics())
    val gnssStatistics: StateFlow<GnssStatistics> = _gnssStatistics.asStateFlow()

    init {
        // Update statistics based on connection state
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                if (state.isConnected && state.dataReceivedCount > 0) {
                    _gnssStatistics.value = GnssStatistics(
                        totalMessages = state.dataReceivedCount.toInt(),
                        lastMessageType = "UBX/NMEA"
                    )
                }
            }
        }
    }

    fun getRecordingDuration(): String {
        return recordingRepository.getRecordingDurationString()
    }

    fun getRecordingSize(): String {
        return recordingRepository.getRecordingSizeString()
    }
}