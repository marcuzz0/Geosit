package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // State for settings
    private val _recordingSettings = MutableStateFlow(SettingsRepository.RecordingSettings())
    val recordingSettings: StateFlow<SettingsRepository.RecordingSettings> = _recordingSettings.asStateFlow()

    private val _gnssSettings = MutableStateFlow(SettingsRepository.GnssSettings())
    val gnssSettings: StateFlow<SettingsRepository.GnssSettings> = _gnssSettings.asStateFlow()

    private val _notificationSettings = MutableStateFlow(SettingsRepository.NotificationSettings())
    val notificationSettings: StateFlow<SettingsRepository.NotificationSettings> = _notificationSettings.asStateFlow()

    init {
        // Load settings
        viewModelScope.launch {
            settingsRepository.recordingSettings.collect {
                _recordingSettings.value = it
            }
        }

        viewModelScope.launch {
            settingsRepository.gnssSettings.collect {
                _gnssSettings.value = it
            }
        }

        viewModelScope.launch {
            settingsRepository.notificationSettings.collect {
                _notificationSettings.value = it
            }
        }
    }

    // Update functions
    fun updateStaticDuration(duration: Int) {
        viewModelScope.launch {
            settingsRepository.updateStaticDuration(duration)
        }
    }

    fun updateStopGoDuration(duration: Int) {
        viewModelScope.launch {
            settingsRepository.updateStopGoDuration(duration)
        }
    }

    fun updateNavigationRate(rate: Int) {
        viewModelScope.launch {
            settingsRepository.updateNavigationRate(rate)
        }
    }

    fun updateAutoSaveInterval(interval: Int) {
        viewModelScope.launch {
            settingsRepository.updateAutoSaveInterval(interval)
        }
    }

    fun updateEnableRawData(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateEnableRawData(enabled)
        }
    }

    fun updateEnableHighPrecision(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateEnableHighPrecision(enabled)
        }
    }

    fun updateGnssSystem(system: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateGnssSystem(system, enabled)
        }
    }

    fun updateNotificationSound(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationSettings(enableSound = enabled)
        }
    }

    fun updateNotificationVibration(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationSettings(enableVibration = enabled)
        }
    }

    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationSettings(enableNotifications = enabled)
        }
    }
}