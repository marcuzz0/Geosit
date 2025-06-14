package com.geosit.gnss.di

import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.gnss.GnssDataProcessor
import com.geosit.gnss.data.gnss.UbxMessageEnabler
import com.geosit.gnss.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GnssModule {

    @Provides
    @Singleton
    fun provideUbxMessageEnabler(
        connectionManager: ConnectionManager
    ): UbxMessageEnabler {
        return UbxMessageEnabler(connectionManager)
    }

    @Provides
    @Singleton
    fun provideGnssDataProcessor(): GnssDataProcessor {
        return GnssDataProcessor()
    }
}