package com.geosit.gnss.data.gnss

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

data class GnssPosition(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val fixType: FixType = FixType.NO_FIX,
    val satellitesUsed: Int = 0,
    val hdop: Double = 99.9,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FixType {
    NO_FIX,
    FIX_2D,
    FIX_3D,
    DGPS,
    RTK_FIXED,
    RTK_FLOAT
}

@Singleton
class GnssDataProcessor @Inject constructor() {
    
    private val parser = GnssDataParser()
    
    private val _currentPosition = MutableStateFlow(GnssPosition())
    val currentPosition: StateFlow<GnssPosition> = _currentPosition.asStateFlow()
    
    private val _rawDataBuffer = mutableListOf<Byte>()
    val rawDataBuffer: List<Byte> get() = _rawDataBuffer.toList()
    
    fun processData(data: ByteArray) {
        // Store raw data
        _rawDataBuffer.addAll(data.toList())
        
        // Parse GNSS messages
        val messages = parser.parseData(data)
        
        messages.forEach { message ->
            when (message) {
                is GnssData.UbxMessage -> processUbxMessage(message)
                is GnssData.NmeaSentence -> processNmeaSentence(message)
            }
        }
    }
    
    private fun processUbxMessage(message: GnssData.UbxMessage) {
        when (message.messageClass) {
            0x01 -> { // NAV class
                when (message.messageId) {
                    0x07 -> processNavPvt(message.payload) // NAV-PVT
                    0x35 -> processNavSat(message.payload) // NAV-SAT
                    else -> Timber.d("Unhandled NAV message: 0x${message.messageId.toString(16)}")
                }
            }
            else -> {
                Timber.d("Unhandled UBX class: 0x${message.messageClass.toString(16)}")
            }
        }
    }
    
    private fun processNavPvt(payload: ByteArray) {
        if (payload.size < 92) return
        
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        
        // Skip iTOW (0-3)
        buffer.position(4)
        
        // Skip year, month, day, hour, min, sec (4-9)
        buffer.position(10)
        
        // Valid flags (10)
        val valid = buffer.get().toInt()
        
        // Skip tAcc (11-14)
        buffer.position(15)
        
        // Skip nano (15-18)
        buffer.position(19)
        
        // Fix type (19)
        val fixType = buffer.get().toInt()
        
        // Flags (20)
        val flags = buffer.get().toInt()
        
        // Skip flags2 (21)
        buffer.position(22)
        
        // Number of satellites (22)
        val numSV = buffer.get().toInt()
        
        // Skip reserved (23-27)
        buffer.position(28)
        
        // Longitude (28-31)
        val lon = buffer.int / 1e7
        
        // Latitude (32-35)
        val lat = buffer.int / 1e7
        
        // Height above ellipsoid (36-39)
        val height = buffer.int / 1000.0
        
        // Skip hMSL (40-43)
        buffer.position(44)
        
        // Skip hAcc, vAcc (44-51)
        buffer.position(52)
        
        // Skip velocities (52-63)
        buffer.position(64)
        
        // Skip gSpeed, headMot (64-71)
        buffer.position(72)
        
        // Skip sAcc, headAcc (72-79)
        buffer.position(80)
        
        // pDOP (80-81)
        val pDOP = buffer.short / 100.0
        
        val gnssFixType = when (fixType) {
            0 -> FixType.NO_FIX
            1 -> FixType.NO_FIX // Dead reckoning
            2 -> FixType.FIX_2D
            3 -> FixType.FIX_3D
            4 -> FixType.DGPS
            5 -> FixType.RTK_FIXED
            else -> FixType.NO_FIX
        }
        
        _currentPosition.value = GnssPosition(
            latitude = lat,
            longitude = lon,
            altitude = height,
            fixType = gnssFixType,
            satellitesUsed = numSV,
            hdop = pDOP,
            timestamp = System.currentTimeMillis()
        )
        
        Timber.d("Position update: $lat, $lon, $height, fix=$gnssFixType, sats=$numSV")
    }
    
    private fun processNavSat(payload: ByteArray) {
        // Process satellite info if needed
        Timber.d("NAV-SAT message received")
    }
    
    private fun processNmeaSentence(sentence: GnssData.NmeaSentence) {
        val parts = sentence.sentence.split(',')
        if (parts.isEmpty()) return
        
        when {
            parts[0].endsWith("GGA") -> processGGA(parts)
            parts[0].endsWith("RMC") -> processRMC(parts)
            else -> Timber.d("Unhandled NMEA: ${parts[0]}")
        }
    }
    
    private fun processGGA(parts: List<String>) {
        if (parts.size < 15) return
        
        try {
            val lat = parseNmeaCoordinate(parts[2], parts[3])
            val lon = parseNmeaCoordinate(parts[4], parts[5])
            val fixQuality = parts[6].toIntOrNull() ?: 0
            val numSats = parts[7].toIntOrNull() ?: 0
            val hdop = parts[8].toDoubleOrNull() ?: 99.9
            val altitude = parts[9].toDoubleOrNull() ?: 0.0
            
            val fixType = when (fixQuality) {
                0 -> FixType.NO_FIX
                1 -> FixType.FIX_3D
                2 -> FixType.DGPS
                4 -> FixType.RTK_FIXED
                5 -> FixType.RTK_FLOAT
                else -> FixType.NO_FIX
            }
            
            _currentPosition.value = _currentPosition.value.copy(
                latitude = lat,
                longitude = lon,
                altitude = altitude,
                fixType = fixType,
                satellitesUsed = numSats,
                hdop = hdop
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error parsing GGA")
        }
    }
    
    private fun processRMC(parts: List<String>) {
        // Process RMC if needed for time/date
    }
    
    private fun parseNmeaCoordinate(coord: String, dir: String): Double {
        if (coord.isEmpty()) return 0.0
        
        val dot = coord.indexOf('.')
        val degrees = coord.substring(0, dot - 2).toDouble()
        val minutes = coord.substring(dot - 2).toDouble()
        
        var result = degrees + minutes / 60.0
        
        if (dir == "S" || dir == "W") {
            result = -result
        }
        
        return result
    }
    
    fun clearBuffer() {
        _rawDataBuffer.clear()
    }
    
    fun getBufferSize(): Int = _rawDataBuffer.size
}
