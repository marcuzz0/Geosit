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
    val accuracy: Double = 0.0,
    val speed: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
enum class FixType {
    NO_FIX,
    FIX_2D,
    FIX_3D,
    DGPS,
    RTK_FIXED,
    RTK_FLOAT,
    DEAD_RECKONING,
    MANUAL_INPUT,
    SIMULATION
}

data class SatelliteInfo(
    val svId: Int,
    val constellation: Constellation,
    val elevation: Int,
    val azimuth: Int,
    val snr: Int,
    val used: Boolean
)

enum class Constellation {
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    SBAS,
    UNKNOWN
}

@Singleton
class GnssDataProcessor @Inject constructor() {

    private val parser = GnssDataParser()

    private val _currentPosition = MutableStateFlow(GnssPosition())
    val currentPosition: StateFlow<GnssPosition> = _currentPosition.asStateFlow()

    private val _satellites = MutableStateFlow<List<SatelliteInfo>>(emptyList())
    val satellites: StateFlow<List<SatelliteInfo>> = _satellites.asStateFlow()

    private val _rawDataBuffer = mutableListOf<Byte>()
    val rawDataBuffer: List<Byte> get() = _rawDataBuffer.toList()

    private val _dataRate = MutableStateFlow(0)
    val dataRate: StateFlow<Int> = _dataRate.asStateFlow()

    private var lastDataTime = System.currentTimeMillis()
    private var dataCounter = 0

    // Statistics
    private val _statistics = MutableStateFlow(GnssStatistics())
    val statistics: StateFlow<GnssStatistics> = _statistics.asStateFlow()

    // Track if we're getting UBX or NMEA data
    private var hasUbxData = false
    private var hasNmeaData = false

    data class GnssStatistics(
        val totalMessages: Long = 0,
        val ubxMessages: Long = 0,
        val nmeaMessages: Long = 0,
        val parseErrors: Long = 0,
        val lastMessageType: String = "",
        val messagesPerSecond: Double = 0.0
    )

    fun processData(data: ByteArray) {
        // Update data rate
        updateDataRate(data.size)

        // Store raw data (limit buffer size to prevent memory issues)
        synchronized(_rawDataBuffer) {
            _rawDataBuffer.addAll(data.toList())
            if (_rawDataBuffer.size > 10000) {
                _rawDataBuffer.subList(0, _rawDataBuffer.size - 10000).clear()
            }
        }

        // Parse GNSS messages
        try {
            val messages = parser.parseData(data)

            messages.forEach { message ->
                updateStatistics(message)

                when (message) {
                    is GnssData.UbxMessage -> {
                        hasUbxData = true
                        processUbxMessage(message)
                    }
                    is GnssData.NmeaSentence -> {
                        hasNmeaData = true
                        processNmeaSentence(message)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing GNSS data")
            _statistics.value = _statistics.value.copy(
                parseErrors = _statistics.value.parseErrors + 1
            )
        }
    }

    private fun updateDataRate(bytes: Int) {
        dataCounter += bytes
        val now = System.currentTimeMillis()

        if (now - lastDataTime >= 1000) {
            _dataRate.value = dataCounter
            dataCounter = 0
            lastDataTime = now
        }
    }

    private fun updateStatistics(message: GnssData) {
        val stats = _statistics.value
        val now = System.currentTimeMillis()
        val timeDiff = (now - lastDataTime) / 1000.0

        _statistics.value = when (message) {
            is GnssData.UbxMessage -> stats.copy(
                totalMessages = stats.totalMessages + 1,
                ubxMessages = stats.ubxMessages + 1,
                lastMessageType = "UBX ${message.messageClass.toString(16).uppercase()}:${message.messageId.toString(16).uppercase()}",
                messagesPerSecond = if (timeDiff > 0) stats.totalMessages / timeDiff else 0.0
            )
            is GnssData.NmeaSentence -> {
                val sentenceType = message.sentence.substringBefore(',').removePrefix("$")
                stats.copy(
                    totalMessages = stats.totalMessages + 1,
                    nmeaMessages = stats.nmeaMessages + 1,
                    lastMessageType = sentenceType,
                    messagesPerSecond = if (timeDiff > 0) stats.totalMessages / timeDiff else 0.0
                )
            }
        }
    }

    private fun processUbxMessage(message: GnssData.UbxMessage) {
        when (message.messageClass) {
            0x01 -> { // NAV class
                when (message.messageId) {
                    0x07 -> processNavPvt(message.payload) // NAV-PVT
                    0x35 -> processNavSat(message.payload) // NAV-SAT
                    0x03 -> processNavStatus(message.payload) // NAV-STATUS
                    0x12 -> processNavVelNed(message.payload) // NAV-VELNED
                    0x14 -> processNavHpPosLlh(message.payload) // NAV-HPPOSLLH (High precision)
                    else -> Timber.d("Unhandled NAV message: 0x${message.messageId.toString(16)}")
                }
            }
            0x02 -> { // RXM class (Receiver Manager)
                when (message.messageId) {
                    0x15 -> processRxmRawx(message.payload) // RXM-RAWX
                    0x13 -> processRxmSfrbx(message.payload) // RXM-SFRBX
                    else -> Timber.d("Unhandled RXM message: 0x${message.messageId.toString(16)}")
                }
            }
            else -> {
                Timber.d("Unhandled UBX class: 0x${message.messageClass.toString(16)}")
            }
        }
    }

    private fun processNavPvt(payload: ByteArray) {
        if (payload.size < 92) {
            Timber.w("NAV-PVT payload too small: ${payload.size}")
            return
        }

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // iTOW (0-3)
            val iTow = buffer.int

            // Date/Time (4-9)
            val year = buffer.short.toInt()
            val month = buffer.get().toInt()
            val day = buffer.get().toInt()
            val hour = buffer.get().toInt()
            val min = buffer.get().toInt()
            val sec = buffer.get().toInt()

            // Valid flags (10)
            val valid = buffer.get().toInt()
            val validDate = (valid and 0x01) != 0
            val validTime = (valid and 0x02) != 0
            val fullyResolved = (valid and 0x04) != 0
            val validMag = (valid and 0x08) != 0

            // Time accuracy (11-14)
            val tAcc = buffer.int

            // Nanoseconds (15-18)
            val nano = buffer.int

            // Fix type (19)
            val fixType = buffer.get().toInt()

            // Fix status flags (20)
            val flags = buffer.get().toInt()
            val gnssFixOk = (flags and 0x01) != 0
            val diffSoln = (flags and 0x02) != 0
            val psmState = (flags shr 2) and 0x07
            val headVehValid = (flags and 0x20) != 0
            val carrSoln = (flags shr 6) and 0x03

            // Additional flags (21)
            val flags2 = buffer.get().toInt()
            val confirmedAvailable = (flags2 and 0x20) != 0
            val confirmedDate = (flags2 and 0x40) != 0
            val confirmedTime = (flags2 and 0x80) != 0

            // Number of satellites (22)
            val numSV = buffer.get().toInt() and 0xFF

            // Skip reserved (23-27)
            buffer.position(28)

            // Position (28-39)
            val lon = buffer.int / 1e7
            val lat = buffer.int / 1e7
            val height = buffer.int / 1000.0 // Height above ellipsoid (mm to m)
            val hMSL = buffer.int / 1000.0 // Height above mean sea level (mm to m)

            // Accuracy (40-47)
            val hAcc = buffer.int / 1000.0 // Horizontal accuracy (mm to m)
            val vAcc = buffer.int / 1000.0 // Vertical accuracy (mm to m)

            // Velocity (48-59)
            val velN = buffer.int / 1000.0 // North velocity (mm/s to m/s)
            val velE = buffer.int / 1000.0 // East velocity (mm/s to m/s)
            val velD = buffer.int / 1000.0 // Down velocity (mm/s to m/s)

            // Ground speed and heading (60-67)
            val gSpeed = buffer.int / 1000.0 // Ground speed (mm/s to m/s)
            val headMot = buffer.int / 1e5 // Heading of motion (degrees)

            // Speed accuracy (68-71)
            val sAcc = buffer.int / 1000.0 // Speed accuracy (mm/s to m/s)

            // Heading accuracy (72-75)
            val headAcc = buffer.int / 1e5 // Heading accuracy (degrees)

            // DOP values (76-81)
            val pDOP = buffer.short / 100.0

            // Skip reserved bytes
            buffer.position(82)

            // More flags (82-89) - skip invalid flags
            buffer.position(90)

            // Vehicle heading (90-91) - if available
            val headVeh = if (buffer.remaining() >= 2) buffer.short / 100.0 else 0.0

            // Determine fix type based on UBX protocol
            val gnssFixType = when (fixType) {
                0 -> FixType.NO_FIX
                1 -> FixType.DEAD_RECKONING
                2 -> FixType.FIX_2D
                3 -> FixType.FIX_3D
                4 -> FixType.DGPS // GPS + SBAS
                5 -> FixType.RTK_FIXED // Time only fix
                else -> FixType.NO_FIX
            }

            // For RTK, check carrier solution
            val finalFixType = if (gnssFixOk && carrSoln > 0) {
                when (carrSoln) {
                    1 -> FixType.RTK_FLOAT
                    2 -> FixType.RTK_FIXED
                    else -> gnssFixType
                }
            } else {
                gnssFixType
            }

            // Build UTC time string
            val utcTime = if (validDate && validTime) {
                String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d",
                    year, month, day, hour, min, sec, nano / 1000000)
            } else {
                ""
            }

            // Calculate 2D speed from velocity components
            val speed2d = kotlin.math.sqrt(velN * velN + velE * velE)

            // Calculate HDOP from pDOP (approximation)
            val hdop = if (pDOP > 0) pDOP * 0.7 else _currentPosition.value.hdop

            _currentPosition.value = GnssPosition(
                latitude = lat,
                longitude = lon,
                altitude = hMSL, // Use MSL altitude
                fixType = finalFixType,
                satellitesUsed = numSV,
                hdop = hdop,
                vdop = 0.0,
                pdop = pDOP,
                accuracy = hAcc,
                speed = speed2d,
                heading = if (headMot >= 0) headMot else 360.0 + headMot,
                timestamp = System.currentTimeMillis(),
                utcTime = utcTime
            )

            Timber.d("NAV-PVT: Fix=$finalFixType, Pos=($lat,$lon,$hMSL), Sats=$numSV, Acc=$hAcc m, HDOP=$hdop")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-PVT")
        }
    }

    private fun processNavSat(payload: ByteArray) {
        if (payload.size < 8) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // Skip iTOW (0-3)
            buffer.position(4)

            // Version (4)
            val version = buffer.get().toInt() and 0xFF

            // Number of satellites (5)
            val numSvs = buffer.get().toInt() and 0xFF

            // Skip reserved (6-7)
            buffer.position(8)

            val satellites = mutableListOf<SatelliteInfo>()

            // Each satellite is 12 bytes
            for (i in 0 until numSvs) {
                if (buffer.remaining() < 12) break

                // GNSS ID (0)
                val gnssId = buffer.get().toInt() and 0xFF

                // Satellite ID (1)
                val svId = buffer.get().toInt() and 0xFF

                // C/N0 (2)
                val cno = buffer.get().toInt() and 0xFF

                // Elevation (3)
                val elev = buffer.get().toInt()

                // Azimuth (4-5)
                val azim = buffer.short.toInt()

                // Pseudo range residual (6-7)
                val prRes = buffer.short.toInt()

                // Flags (8-11)
                val flags = buffer.int

                val qualityInd = flags and 0x07
                val used = (flags and 0x08) != 0
                val health = (flags shr 4) and 0x03
                val diffCorr = (flags and 0x40) != 0
                val smoothed = (flags and 0x80) != 0
                val orbitSource = (flags shr 8) and 0x07
                val ephAvail = (flags and 0x800) != 0
                val almAvail = (flags and 0x1000) != 0
                val anoAvail = (flags and 0x2000) != 0
                val aopAvail = (flags and 0x4000) != 0

                val constellation = when (gnssId) {
                    0 -> Constellation.GPS
                    1 -> Constellation.SBAS
                    2 -> Constellation.GALILEO
                    3 -> Constellation.BEIDOU
                    5 -> Constellation.QZSS
                    6 -> Constellation.GLONASS
                    else -> Constellation.UNKNOWN
                }

                satellites.add(
                    SatelliteInfo(
                        svId = svId,
                        constellation = constellation,
                        elevation = elev,
                        azimuth = azim,
                        snr = cno,
                        used = used
                    )
                )
            }

            _satellites.value = satellites

            Timber.d("NAV-SAT: ${satellites.size} satellites, ${satellites.count { it.used }} used")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-SAT")
        }
    }

    private fun processNavStatus(payload: ByteArray) {
        // Process NAV-STATUS for additional fix information
        if (payload.size < 16) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // Skip iTOW
            buffer.position(4)

            // GPS fix type
            val gpsFix = buffer.get().toInt() and 0xFF

            // Flags
            val flags = buffer.get().toInt() and 0xFF

            // Fix status
            val fixStat = buffer.get().toInt() and 0xFF

            // Flags 2
            val flags2 = buffer.get().toInt() and 0xFF

            Timber.d("NAV-STATUS: gpsFix=$gpsFix, flags=$flags")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-STATUS")
        }
    }

    private fun processNavVelNed(payload: ByteArray) {
        // Process velocity in NED frame
        if (payload.size < 36) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // Skip iTOW
            buffer.position(4)

            // Velocities (cm/s)
            val velN = buffer.int / 100.0
            val velE = buffer.int / 100.0
            val velD = buffer.int / 100.0

            // Speed
            val speed = buffer.getInt() / 100.0

            // Ground speed
            val gSpeed = buffer.getInt() / 100.0

            // Heading
            val heading = buffer.getInt() / 1e5

            // Speed accuracy
            val sAcc = buffer.getInt() / 100.0

            // Course accuracy
            val cAcc = buffer.getInt() / 1e5

            Timber.d("NAV-VELNED: Speed=$gSpeed m/s, Heading=$heading°")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-VELNED")
        }
    }

    private fun processNavHpPosLlh(payload: ByteArray) {
        // High precision position
        if (payload.size < 36) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // Skip version and reserved
            buffer.position(4)

            // Skip iTOW
            buffer.position(8)

            // Position (standard precision)
            val lon = buffer.int / 1e7
            val lat = buffer.int / 1e7
            val height = buffer.int / 1000.0
            val hMSL = buffer.int / 1000.0

            // High precision components (0.1 mm)
            val lonHp = buffer.get() / 1e9
            val latHp = buffer.get() / 1e9
            val heightHp = buffer.get() / 10000.0
            val hMSLHp = buffer.get() / 10000.0

            // Accuracy
            val hAcc = buffer.int / 10000.0
            val vAcc = buffer.int / 10000.0

            val highPrecisionLat = lat + latHp
            val highPrecisionLon = lon + lonHp
            val highPrecisionHeight = hMSL + hMSLHp

            Timber.d("NAV-HPPOSLLH: HP Pos=($highPrecisionLat,$highPrecisionLon,$highPrecisionHeight)")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-HPPOSLLH")
        }
    }

    private fun processRxmRawx(payload: ByteArray) {
        // Raw measurement data
        Timber.d("RXM-RAWX received, size=${payload.size}")
    }

    private fun processRxmSfrbx(payload: ByteArray) {
        // Subframe buffer data
        Timber.d("RXM-SFRBX received, size=${payload.size}")
    }

    private fun processNmeaSentence(sentence: GnssData.NmeaSentence) {
        val cleanSentence = sentence.sentence.trim()
        val parts = cleanSentence.split(',')
        if (parts.isEmpty()) return

        try {
            val sentenceType = parts[0].removePrefix("$")
            when {
                sentenceType.endsWith("GGA") -> processGGA(parts)
                sentenceType.endsWith("RMC") -> processRMC(parts)
                sentenceType.endsWith("GSA") -> processGSA(parts)
                sentenceType.endsWith("GSV") -> processGSV(parts)
                sentenceType.endsWith("GLL") -> processGLL(parts)
                sentenceType.endsWith("VTG") -> processVTG(parts)
                else -> Timber.d("Unhandled NMEA: $sentenceType")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing NMEA sentence: ${sentence.sentence}")
        }
    }

    private fun processGGA(parts: List<String>) {
        if (parts.size < 15) {
            Timber.w("GGA sentence too short: ${parts.size} parts")
            return
        }

        try {
            // Log raw data for debugging
            Timber.d("GGA Raw: ${parts.joinToString(",")}")

            // Time
            val time = parts[1]

            // Position
            val lat = parseNmeaCoordinate(parts[2], parts[3])
            val lon = parseNmeaCoordinate(parts[4], parts[5])

            // Fix quality
            val fixQuality = parts[6].trim().toIntOrNull() ?: 0

            // Satellites
            val numSats = parts[7].trim().toIntOrNull() ?: 0

            // HDOP
            val hdop = parts[8].trim().toDoubleOrNull() ?: 99.9

            // Altitude - make sure to trim whitespace
            val altitudeStr = parts[9].trim()
            val altitude = if (altitudeStr.isNotEmpty()) altitudeStr.toDoubleOrNull() ?: 0.0 else 0.0
            val altitudeUnits = if (parts.size > 10) parts[10] else "M"

            // Geoidal separation
            val geoidHeight = if (parts.size > 11 && parts[11].isNotEmpty()) parts[11].trim().toDoubleOrNull() ?: 0.0 else 0.0
            val geoidUnits = if (parts.size > 12) parts[12] else "M"

            // Log parsed values
            Timber.d("GGA Parsed: Sats=$numSats, Alt=$altitude m, HDOP=$hdop")

            val fixType = when (fixQuality) {
                0 -> FixType.NO_FIX
                1 -> FixType.FIX_3D
                2 -> FixType.DGPS
                4 -> FixType.RTK_FIXED
                5 -> FixType.RTK_FLOAT
                6 -> FixType.DEAD_RECKONING
                7 -> FixType.MANUAL_INPUT
                8 -> FixType.SIMULATION
                else -> FixType.NO_FIX
            }

            // Build time string
            val utcTime = if (time.length >= 6) {
                "${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
            } else ""

            // Update position - preserve existing data if we're getting partial updates
            val currentPos = _currentPosition.value
            _currentPosition.value = currentPos.copy(
                latitude = if (lat != 0.0) lat else currentPos.latitude,
                longitude = if (lon != 0.0) lon else currentPos.longitude,
                altitude = if (altitude != 0.0 || fixType != FixType.NO_FIX) altitude else currentPos.altitude,
                fixType = fixType,
                satellitesUsed = if (numSats > 0) numSats else currentPos.satellitesUsed,
                hdop = if (hdop < 99.9) hdop else currentPos.hdop,
                utcTime = if (utcTime.isNotEmpty()) utcTime else currentPos.utcTime
            )

            Timber.d("GGA Update: Fix=$fixType, Pos=($lat,$lon,$altitude), Sats=$numSats, HDOP=$hdop")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GGA")
        }
    }

    private fun processRMC(parts: List<String>) {
        if (parts.size < 12) return

        try {
            // Time
            val time = parts[1]

            // Status
            val status = parts[2].trim() // A=active, V=void

            if (status == "A") {
                // Position
                val lat = parseNmeaCoordinate(parts[3], parts[4])
                val lon = parseNmeaCoordinate(parts[5], parts[6])

                // Speed
                val speedKnots = parts[7].trim().toDoubleOrNull() ?: 0.0
                val speedMs = speedKnots * 0.514444 // Convert knots to m/s

                // Course
                val course = parts[8].trim().toDoubleOrNull() ?: 0.0

                // Date
                val date = parts[9]

                // Build date/time string
                val dateTime = if (date.length >= 6 && time.length >= 6) {
                    "20${date.substring(4, 6)}-${date.substring(2, 4)}-${date.substring(0, 2)} " +
                            "${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
                } else ""

                _currentPosition.value = _currentPosition.value.copy(
                    speed = speedMs,
                    heading = course,
                    utcTime = if (dateTime.isNotEmpty()) dateTime else _currentPosition.value.utcTime
                )

                Timber.d("RMC: Speed=$speedMs m/s, Course=$course°")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing RMC")
        }
    }

    private fun processGSA(parts: List<String>) {
        if (parts.size < 18) return

        try {
            // Selection mode
            val mode = parts[1].trim() // M=manual, A=automatic

            // Fix mode
            val fixMode = parts[2].trim().toIntOrNull() ?: 1

            // Satellite PRNs (fields 3-14)
            val satellites = mutableListOf<Int>()
            for (i in 3..14) {
                if (i < parts.size) {
                    parts[i].trim().toIntOrNull()?.let { satellites.add(it) }
                }
            }

            // DOP values
            val pdop = parts[15].trim().toDoubleOrNull() ?: 99.9
            val hdop = parts[16].trim().toDoubleOrNull() ?: 99.9
            val vdop = parts[17].trim().toDoubleOrNull() ?: 99.9

            _currentPosition.value = _currentPosition.value.copy(
                pdop = pdop,
                hdop = hdop,
                vdop = vdop,
                fixType = when (fixMode) {
                    2 -> FixType.FIX_2D
                    3 -> FixType.FIX_3D
                    else -> _currentPosition.value.fixType // Keep existing if not specified
                }
            )

            Timber.d("GSA: Mode=$fixMode, PDOP=$pdop, HDOP=$hdop, VDOP=$vdop")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GSA")
        }
    }

    private fun processGSV(parts: List<String>) {
        // Satellites in view - could be used to update satellite list
        // For now just log
        if (parts.size >= 4) {
            val totalMessages = parts[1].trim().toIntOrNull() ?: 0
            val messageNum = parts[2].trim().toIntOrNull() ?: 0
            val satellitesInView = parts[3].trim().toIntOrNull() ?: 0

            Timber.d("GSV: Message $messageNum/$totalMessages, $satellitesInView satellites in view")
        }
    }

    private fun processGLL(parts: List<String>) {
        if (parts.size < 8) return

        try {
            // Position
            val lat = parseNmeaCoordinate(parts[1], parts[2])
            val lon = parseNmeaCoordinate(parts[3], parts[4])

            // Time
            val time = parts[5]

            // Status
            val status = parts[6].trim() // A=active, V=void

            if (status == "A") {
                _currentPosition.value = _currentPosition.value.copy(
                    latitude = lat,
                    longitude = lon
                )

                Timber.d("GLL: Pos=($lat,$lon)")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GLL")
        }
    }

    private fun processVTG(parts: List<String>) {
        if (parts.size < 10) return

        try {
            // Course
            val courseTrue = parts[1].trim().toDoubleOrNull() ?: 0.0
            val courseMag = parts[3].trim().toDoubleOrNull() ?: 0.0

            // Speed
            val speedKnots = parts[5].trim().toDoubleOrNull() ?: 0.0
            val speedKmh = parts[7].trim().toDoubleOrNull() ?: 0.0
            val speedMs = speedKmh / 3.6

            // Mode
            val mode = if (parts.size > 9) parts[9] else ""

            _currentPosition.value = _currentPosition.value.copy(
                speed = speedMs,
                heading = courseTrue
            )

            Timber.d("VTG: Course=$courseTrue°, Speed=$speedMs m/s")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing VTG")
        }
    }

    private fun parseNmeaCoordinate(coord: String, dir: String): Double {
        val cleanCoord = coord.trim()
        val cleanDir = dir.trim()

        if (cleanCoord.isEmpty()) return 0.0

        return try {
            val dot = cleanCoord.indexOf('.')
            if (dot < 2) return 0.0

            val degrees = cleanCoord.substring(0, dot - 2).toDouble()
            val minutes = cleanCoord.substring(dot - 2).toDouble()

            var result = degrees + minutes / 60.0

            if (cleanDir == "S" || cleanDir == "W") {
                result = -result
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error parsing coordinate: $coord $dir")
            0.0
        }
    }

    fun clearBuffer() {
        synchronized(_rawDataBuffer) {
            _rawDataBuffer.clear()
        }
        dataCounter = 0
    }

    fun getBufferSize(): Int = _rawDataBuffer.size

    fun getFormattedPosition(): String {
        val pos = _currentPosition.value
        return if (pos.fixType != FixType.NO_FIX) {
            String.format(
                "%.8f°, %.8f°, %.2fm",
                pos.latitude,
                pos.longitude,
                pos.altitude
            )
        } else {
            "No fix"
        }
    }

    fun reset() {
        _currentPosition.value = GnssPosition()
        _satellites.value = emptyList()
        _statistics.value = GnssStatistics()
        clearBuffer()
        hasUbxData = false
        hasNmeaData = false
    }
}