package com.geosit.gnss.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Preference Keys
    private object PreferenceKeys {
        // Recording settings
        val STATIC_DURATION = intPreferencesKey("static_duration")
        val STOP_GO_DURATION = intPreferencesKey("stop_go_duration")
        val NAVIGATION_RATE = intPreferencesKey("navigation_rate")
        val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval")
        val ENABLE_RAW_DATA = booleanPreferencesKey("enable_raw_data")
        val ENABLE_HIGH_PRECISION = booleanPreferencesKey("enable_high_precision")

        // GNSS settings
        val USE_GPS = booleanPreferencesKey("use_gps")
        val USE_GLONASS = booleanPreferencesKey("use_glonass")
        val USE_GALILEO = booleanPreferencesKey("use_galileo")
        val USE_BEIDOU = booleanPreferencesKey("use_beidou")

        // Notification settings
        val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")
        val ENABLE_SOUND = booleanPreferencesKey("enable_sound")
        val ENABLE_VIBRATION = booleanPreferencesKey("enable_vibration")
    }

    // Data class for settings
    data class RecordingSettings(
        val staticDuration: Int = 60,
        val stopGoDuration: Int = 30,
        val navigationRate: Int = 1,
        val autoSaveInterval: Int = 5,
        val enableRawData: Boolean = true,
        val enableHighPrecision: Boolean = false
    )

    data class GnssSettings(
        val useGPS: Boolean = true,
        val useGLONASS: Boolean = true,
        val useGalileo: Boolean = true,
        val useBeidou: Boolean = true
    )

    data class NotificationSettings(
        val enableNotifications: Boolean = true,
        val enableSound: Boolean = true,
        val enableVibration: Boolean = false
    )

    // Flow for recording settings
    val recordingSettings: Flow<RecordingSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading recording settings")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            RecordingSettings(
                staticDuration = preferences[PreferenceKeys.STATIC_DURATION] ?: 60,
                stopGoDuration = preferences[PreferenceKeys.STOP_GO_DURATION] ?: 30,
                navigationRate = preferences[PreferenceKeys.NAVIGATION_RATE] ?: 1,
                autoSaveInterval = preferences[PreferenceKeys.AUTO_SAVE_INTERVAL] ?: 5,
                enableRawData = preferences[PreferenceKeys.ENABLE_RAW_DATA] ?: true,
                enableHighPrecision = preferences[PreferenceKeys.ENABLE_HIGH_PRECISION] ?: false
            )
        }

    // Flow for GNSS settings
    val gnssSettings: Flow<GnssSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading GNSS settings")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            GnssSettings(
                useGPS = preferences[PreferenceKeys.USE_GPS] ?: true,
                useGLONASS = preferences[PreferenceKeys.USE_GLONASS] ?: true,
                useGalileo = preferences[PreferenceKeys.USE_GALILEO] ?: true,
                useBeidou = preferences[PreferenceKeys.USE_BEIDOU] ?: true
            )
        }

    // Flow for notification settings
    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading notification settings")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            NotificationSettings(
                enableNotifications = preferences[PreferenceKeys.ENABLE_NOTIFICATIONS] ?: true,
                enableSound = preferences[PreferenceKeys.ENABLE_SOUND] ?: true,
                enableVibration = preferences[PreferenceKeys.ENABLE_VIBRATION] ?: false
            )
        }

    // Update functions
    suspend fun updateStaticDuration(duration: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.STATIC_DURATION] = duration.coerceAtLeast(10)
        }
    }

    suspend fun updateStopGoDuration(duration: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.STOP_GO_DURATION] = duration.coerceAtLeast(10)
        }
    }

    suspend fun updateNavigationRate(rate: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.NAVIGATION_RATE] = rate.coerceIn(1, 10)
        }
    }

    suspend fun updateAutoSaveInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AUTO_SAVE_INTERVAL] = interval.coerceAtLeast(0)
        }
    }

    suspend fun updateEnableRawData(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ENABLE_RAW_DATA] = enabled
        }
    }

    suspend fun updateEnableHighPrecision(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ENABLE_HIGH_PRECISION] = enabled
        }
    }

    suspend fun updateGnssSystem(system: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            when (system) {
                "GPS" -> preferences[PreferenceKeys.USE_GPS] = enabled
                "GLONASS" -> preferences[PreferenceKeys.USE_GLONASS] = enabled
                "GALILEO" -> preferences[PreferenceKeys.USE_GALILEO] = enabled
                "BEIDOU" -> preferences[PreferenceKeys.USE_BEIDOU] = enabled
            }
        }
    }

    suspend fun updateNotificationSettings(
        enableNotifications: Boolean? = null,
        enableSound: Boolean? = null,
        enableVibration: Boolean? = null
    ) {
        context.dataStore.edit { preferences ->
            enableNotifications?.let { preferences[PreferenceKeys.ENABLE_NOTIFICATIONS] = it }
            enableSound?.let { preferences[PreferenceKeys.ENABLE_SOUND] = it }
            enableVibration?.let { preferences[PreferenceKeys.ENABLE_VIBRATION] = it }
        }
    }
}