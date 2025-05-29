package com.geosit.gnss.data.model

import java.util.Date

data class RecordingSession(
    val id: String,
    val mode: RecordingMode,
    val startTime: Date,
    val endTime: Date? = null,
    val fileName: String,
    val pointName: String = "",
    val instrumentHeight: Double = 0.0,
    val staticDuration: Int = 0, // seconds, for static mode
    val isCompleted: Boolean = false,
    val fileSize: Long = 0,
    val dataPointsCount: Int = 0
)

enum class RecordingMode {
    STATIC,
    KINEMATIC,
    STOP_AND_GO
}

data class StopAndGoPoint(
    val id: Int,
    val name: String,
    val timestamp: Date,
    val action: StopGoAction,
    val instrumentHeight: Double
)

enum class StopGoAction {
    STOP,
    GO
}
