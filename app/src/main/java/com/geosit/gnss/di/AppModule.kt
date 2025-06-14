package com.geosit.gnss.di

import android.content.Context
import com.geosit.gnss.ui.screens.connection.ConnectionManager  // Import corretto!
import com.geosit.gnss.data.gnss.GnssDataProcessor
import com.geosit.gnss.data.recording.RecordingRepository
import com.geosit.gnss.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context,
        gnssDataProcessor: GnssDataProcessor,
        settingsRepository: SettingsRepository
    ): ConnectionManager {
        return ConnectionManager(context, gnssDataProcessor, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideRecordingRepository(
        @ApplicationContext context: Context,
        connectionManager: ConnectionManager
    ): RecordingRepository {
        return RecordingRepository(context, connectionManager)
    }
}