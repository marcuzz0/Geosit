package com.geosit.gnss.di

import com.geosit.gnss.data.gnss.GnssDataProcessor
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
    fun provideGnssDataProcessor(): GnssDataProcessor {
        return GnssDataProcessor()
    }
}