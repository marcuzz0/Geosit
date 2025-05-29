package com.geosit.gnss.service.recording

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle recording logic
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup
    }
}