package com.geosit.gnss.data.model

import java.util.Date

data class RecordingFile(
    val id: String,
    val name: String,
    val mode: RecordingMode,
    val date: Date,
    val size: Long,
    val duration: Int = 0,
    val filePath: String,
    val csvPath: String? = null
)
