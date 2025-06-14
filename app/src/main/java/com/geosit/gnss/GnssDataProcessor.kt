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
    val satellitesInView: Int = 0,
    val hdop: Double = 99.9,
    val vdop: Double = 99.9,
    val pdop: Double = 99.9,
    val timestamp: Long = System.currentTimeMillis(),
    val horizontalAccuracy: Double = 0.0,
    val verticalAccuracy: Double = 0.0,
    val speedKnots: Double = 0.0,
    val speedMs: Double = 0.0,
    val course: Double = 0.0,
    val fixStatus: String = "No Fix",
    val utcTime: String = ""
)

enum class FixType {
    NO_FIX,
    FIX_2D,
    FIX_3D,
    DGPS,
    RTK_FIXED,
    RTK_FLOAT
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

    // Statistics
    private var messageCount = 0L
    private var lastMessageTime = System.currentTimeMillis()

    // GSV message tracking
    private val gsvSatellites = mutableMapOf<String, MutableList<SatelliteInfo>>()
    private val gsvMessageCount = mutableMapOf<String, Int>()

    fun processData(data: ByteArray): List<GnssData> {
        // Store raw data (limit buffer size)
        synchronized(_rawDataBuffer) {
            _rawDataBuffer.addAll(data.toList())
            if (_rawDataBuffer.size > 10000) {
                _rawDataBuffer.subList(0, _rawDataBuffer.size - 10000).clear()
            }
        }

        // Parse GNSS messages
        val messages = parser.parseData(data)

        messages.forEach { message ->
            messageCount++
            when (message) {
                is GnssData.UbxMessage -> processUbxMessage(message)
                is GnssData.NmeaSentence -> processNmeaSentence(message)
            }
        }

        return messages
    }

    private fun processUbxMessage(message: GnssData.UbxMessage) {
        when (message.messageClass) {
            0x01 -> { // NAV class
                when (message.messageId) {
                    0x07 -> processNavPvt(message.payload) // NAV-PVT
                    0x35 -> processNavSat(message.payload) // NAV-SAT
                    0x03 -> processNavStatus(message.payload) // NAV-STATUS
                    0x04 -> processNavDop(message.payload) // NAV-DOP
                    else -> Timber.d("Unhandled NAV message: 0x${message.messageId.toString(16)}")
                }
            }
            0x02 -> { // RXM class
                when (message.messageId) {
                    0x15 -> Timber.d("RXM-RAWX received") // Raw measurements
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
            Timber.w("NAV-PVT payload too small: ${payload.size} bytes")
            return
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        try {
            // iTOW (0-3)
            val iTOW = buffer.int

            // Date/Time (4-9)
            val year = buffer.short.toInt() and 0xFFFF
            val month = buffer.get().toInt() and 0xFF
            val day = buffer.get().toInt() and 0xFF
            val hour = buffer.get().toInt() and 0xFF
            val min = buffer.get().toInt() and 0xFF
            val sec = buffer.get().toInt() and 0xFF

            // Valid flags (10)
            val valid = buffer.get().toInt() and 0xFF

            // Time accuracy (11-14)
            val tAcc = buffer.int

            // Nano (15-18)
            val nano = buffer.int

            // Fix type (19)
            val fixType = buffer.get().toInt() and 0xFF

            // Flags (20)
            val flags = buffer.get().toInt() and 0xFF

            // Flags2 (21)
            val flags2 = buffer.get().toInt() and 0xFF

            // Number of satellites (22)
            val numSV = buffer.get().toInt() and 0xFF

            // Longitude (23-26) in degrees * 1e-7
            val lonRaw = buffer.int
            val lon = lonRaw / 1e7

            // Latitude (27-30) in degrees * 1e-7
            val latRaw = buffer.int
            val lat = latRaw / 1e7

            // Height above ellipsoid (31-34) in mm
            val heightRaw = buffer.int
            val height = heightRaw / 1000.0

            // Height above mean sea level (35-38) in mm
            val hMSLRaw = buffer.int
            val hMSL = hMSLRaw / 1000.0

            // Horizontal accuracy (39-42) in mm
            val hAccRaw = buffer.int
            val hAcc = hAccRaw / 1000.0

            // Vertical accuracy (43-46) in mm
            val vAccRaw = buffer.int
            val vAcc = vAccRaw / 1000.0

            // Velocities NED (47-58) in mm/s
            val velN = buffer.int / 1000.0
            val velE = buffer.int / 1000.0
            val velD = buffer.int / 1000.0

            // Ground speed (59-62) in mm/s
            val gSpeed = buffer.int / 1000.0

            // Heading of motion (63-66) in degrees * 1e-5
            val headMot = buffer.int / 1e5

            // Speed accuracy (67-70) in mm/s
            val sAcc = buffer.int / 1000.0

            // Heading accuracy (71-74) in degrees * 1e-5
            val headAcc = buffer.int / 1e5

            // pDOP (75-76) * 0.01
            val pDOPRaw = buffer.short.toInt() and 0xFFFF
            val pDOP = pDOPRaw / 100.0

            // Determina il tipo di fix
            val gnssFixType = when (fixType) {
                0 -> FixType.NO_FIX
                1 -> FixType.NO_FIX // Dead reckoning only
                2 -> FixType.FIX_2D
                3 -> FixType.FIX_3D
                4 -> FixType.FIX_3D // GNSS + dead reckoning
                5 -> FixType.NO_FIX // Time only fix
                else -> FixType.NO_FIX
            }

            // Verifica flags
            val gnssFixOk = (flags and 0x01) != 0
            val diffSoln = (flags and 0x02) != 0
            val carrSoln = (flags and 0xC0) shr 6

            // Determina tipo di fix migliorato
            val enhancedFixType = when {
                !gnssFixOk -> FixType.NO_FIX
                carrSoln == 2 -> FixType.RTK_FIXED
                carrSoln == 1 -> FixType.RTK_FLOAT
                diffSoln -> FixType.DGPS
                else -> gnssFixType
            }

            // UTC time string
            val utcTime = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                year, month, day, hour, min, sec)

            // Calcola HDOP approssimato da pDOP
            val hdop = if (pDOP > 0) pDOP * 0.7 else 99.9

            // Velocità in knots
            val speedKnots = gSpeed * 1.94384

            _currentPosition.value = GnssPosition(
                latitude = lat,
                longitude = lon,
                altitude = hMSL,
                fixType = enhancedFixType,
                satellitesUsed = numSV,
                hdop = hdop,
                pdop = pDOP,
                horizontalAccuracy = hAcc,
                verticalAccuracy = vAcc,
                speedMs = gSpeed,
                speedKnots = speedKnots,
                course = if (headMot >= 0) headMot else 360.0 + headMot,
                timestamp = System.currentTimeMillis(),
                fixStatus = when (enhancedFixType) {
                    FixType.NO_FIX -> "No Fix"
                    FixType.FIX_2D -> "2D Fix"
                    FixType.FIX_3D -> "3D Fix"
                    FixType.DGPS -> "DGPS"
                    FixType.RTK_FIXED -> "RTK Fixed"
                    FixType.RTK_FLOAT -> "RTK Float"
                },
                utcTime = utcTime
            )

            Timber.d("NAV-PVT: Fix=$enhancedFixType, Pos=($lat,$lon,$hMSL), Sats=$numSV, Acc=H:$hAcc V:$vAcc")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-PVT")
        }
    }

    private fun processNavSat(payload: ByteArray) {
        if (payload.size < 8) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            // Skip iTOW
            buffer.position(4)

            // Version
            val version = buffer.get().toInt() and 0xFF

            // Number of satellites
            val numSvs = buffer.get().toInt() and 0xFF

            // Skip reserved
            buffer.position(8)

            val satellites = mutableListOf<SatelliteInfo>()
            var usedCount = 0

            // Each satellite is 12 bytes
            for (i in 0 until numSvs) {
                if (buffer.remaining() < 12) break

                val gnssId = buffer.get().toInt() and 0xFF
                val svId = buffer.get().toInt() and 0xFF
                val cno = buffer.get().toInt() and 0xFF
                val elev = buffer.get().toInt()
                val azim = buffer.short.toInt()
                val prRes = buffer.short.toInt()
                val flags = buffer.int

                val used = (flags and 0x08) != 0
                if (used) usedCount++

                val constellation = when (gnssId) {
                    0 -> Constellation.GPS
                    1 -> Constellation.SBAS
                    2 -> Constellation.GALILEO
                    3 -> Constellation.BEIDOU
                    5 -> Constellation.QZSS
                    6 -> Constellation.GLONASS
                    else -> Constellation.UNKNOWN
                }

                if (cno > 0) {
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
            }

            _satellites.value = satellites
            _currentPosition.value = _currentPosition.value.copy(
                satellitesInView = satellites.size
            )

            Timber.d("NAV-SAT: ${satellites.size} visible, $usedCount used")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-SAT")
        }
    }

    private fun processNavStatus(payload: ByteArray) {
        // Additional status info
        if (payload.size < 16) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4) // Skip iTOW

            val gpsFix = buffer.get().toInt() and 0xFF
            val flags = buffer.get().toInt() and 0xFF

            Timber.d("NAV-STATUS: gpsFix=$gpsFix, flags=0x${flags.toString(16)}")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-STATUS")
        }
    }

    private fun processNavDop(payload: ByteArray) {
        if (payload.size < 18) return

        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4) // Skip iTOW

            val gDOP = buffer.short / 100.0
            val pDOP = buffer.short / 100.0
            val tDOP = buffer.short / 100.0
            val vDOP = buffer.short / 100.0
            val hDOP = buffer.short / 100.0
            val nDOP = buffer.short / 100.0
            val eDOP = buffer.short / 100.0

            _currentPosition.value = _currentPosition.value.copy(
                pdop = pDOP,
                hdop = hDOP,
                vdop = vDOP
            )

            Timber.d("NAV-DOP: PDOP=$pDOP, HDOP=$hDOP, VDOP=$vDOP")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing NAV-DOP")
        }
    }

    private fun processNmeaSentence(sentence: GnssData.NmeaSentence) {
        val parts = sentence.sentence.trim().split(',')
        if (parts.isEmpty()) return

        try {
            when {
                parts[0].endsWith("GGA") -> processGGA(parts)
                parts[0].endsWith("RMC") -> processRMC(parts)
                parts[0].endsWith("GSA") -> processGSA(parts)
                parts[0].endsWith("GSV") -> processGSV(parts)
                parts[0].endsWith("GLL") -> processGLL(parts)
                parts[0].endsWith("VTG") -> processVTG(parts)
                else -> Timber.d("Unhandled NMEA: ${parts[0]}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing NMEA: ${sentence.sentence}")
        }
    }

    private fun processGGA(parts: List<String>) {
        if (parts.size < 15) {
            Timber.w("GGA too short: ${parts.size} parts")
            return
        }

        try {
            // Skip empty or invalid GGA
            if (parts[2].isEmpty() || parts[4].isEmpty()) {
                Timber.d("GGA: Empty position data")
                return
            }

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

            // Altitude MSL
            val altitude = parts[9].trim().toDoubleOrNull() ?: 0.0

            val fixType = when (fixQuality) {
                0 -> FixType.NO_FIX
                1 -> FixType.FIX_3D
                2 -> FixType.DGPS
                4 -> FixType.RTK_FIXED
                5 -> FixType.RTK_FLOAT
                else -> FixType.NO_FIX
            }

            // UTC time
            val utcTime = if (time.length >= 6) {
                "${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
            } else ""

            // Only update if we have valid coordinates or fix
            if (lat != 0.0 || lon != 0.0 || fixQuality > 0) {
                _currentPosition.value = GnssPosition(
                    latitude = lat,
                    longitude = lon,
                    altitude = altitude,
                    fixType = fixType,
                    satellitesUsed = numSats,
                    satellitesInView = _currentPosition.value.satellitesInView,
                    hdop = hdop,
                    vdop = _currentPosition.value.vdop,
                    pdop = _currentPosition.value.pdop,
                    timestamp = System.currentTimeMillis(),
                    horizontalAccuracy = if (hdop < 50) hdop * 2.5 else 0.0, // Rough estimate
                    verticalAccuracy = if (hdop < 50) hdop * 3.5 else 0.0,
                    speedKnots = _currentPosition.value.speedKnots,
                    speedMs = _currentPosition.value.speedMs,
                    course = _currentPosition.value.course,
                    fixStatus = when (fixType) {
                        FixType.NO_FIX -> "No Fix"
                        FixType.FIX_3D -> "3D Fix"
                        FixType.DGPS -> "DGPS"
                        FixType.RTK_FIXED -> "RTK Fixed"
                        FixType.RTK_FLOAT -> "RTK Float"
                        else -> "Fix"
                    },
                    utcTime = if (utcTime.isNotEmpty()) utcTime else _currentPosition.value.utcTime
                )

                Timber.d("GGA Update: Fix=$fixType, Pos=($lat,$lon,$altitude), Sats=$numSats, HDOP=$hdop")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GGA: ${parts.joinToString(",")}")
        }
    }

    private fun processRMC(parts: List<String>) {
        if (parts.size < 12) return

        try {
            val time = parts[1]
            val status = parts[2] // A=active, V=void

            if (status == "A") {
                val lat = parseNmeaCoordinate(parts[3], parts[4])
                val lon = parseNmeaCoordinate(parts[5], parts[6])
                val speedKnots = parts[7].toDoubleOrNull() ?: 0.0
                val course = parts[8].toDoubleOrNull() ?: 0.0
                val date = parts[9]

                // Build date/time
                val dateTime = if (date.length >= 6 && time.length >= 6) {
                    "20${date.substring(4, 6)}-${date.substring(2, 4)}-${date.substring(0, 2)} " +
                            "${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
                } else ""

                _currentPosition.value = _currentPosition.value.copy(
                    latitude = lat,
                    longitude = lon,
                    speedKnots = speedKnots,
                    speedMs = speedKnots * 0.514444,
                    course = course,
                    utcTime = if (dateTime.isNotEmpty()) dateTime else _currentPosition.value.utcTime
                )

                Timber.d("RMC: Pos=($lat,$lon), Speed=$speedKnots kts, Course=$course°")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing RMC")
        }
    }

    private fun processGSA(parts: List<String>) {
        if (parts.size < 18) return

        try {
            val mode = parts[1] // M=manual, A=auto
            val fixMode = parts[2].toIntOrNull() ?: 1

            // Count used satellites
            val usedSats = (3..14).count {
                parts.getOrNull(it)?.trim()?.isNotEmpty() == true
            }

            val pdop = parts[15].toDoubleOrNull() ?: 99.9
            val hdop = parts[16].toDoubleOrNull() ?: 99.9
            val vdop = parts[17].toDoubleOrNull() ?: 99.9

            _currentPosition.value = _currentPosition.value.copy(
                pdop = pdop,
                hdop = hdop,
                vdop = vdop,
                fixType = when (fixMode) {
                    1 -> FixType.NO_FIX
                    2 -> FixType.FIX_2D
                    3 -> FixType.FIX_3D
                    else -> _currentPosition.value.fixType
                }
            )

            Timber.d("GSA: Mode=$fixMode, PDOP=$pdop, HDOP=$hdop, VDOP=$vdop")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GSA")
        }
    }

    private fun processGSV(parts: List<String>) {
        if (parts.size < 4) return

        try {
            val talkerID = parts[0].substring(1, 3) // GP, GL, GA, etc.
            val totalMessages = parts[1].toIntOrNull() ?: 0
            val messageNum = parts[2].toIntOrNull() ?: 0
            val totalSats = parts[3].toIntOrNull() ?: 0

            // Initialize for first message
            if (messageNum == 1) {
                gsvSatellites[talkerID] = mutableListOf()
                gsvMessageCount[talkerID] = totalMessages
            }

            // Parse satellite data (4 sats per message max)
            var index = 4
            while (index + 3 < parts.size) {
                val svId = parts[index].toIntOrNull() ?: break
                val elevation = parts[index + 1].toIntOrNull() ?: 0
                val azimuth = parts[index + 2].toIntOrNull() ?: 0
                val snr = parts[index + 3].toIntOrNull() ?: 0

                if (svId > 0) {
                    val constellation = when (talkerID) {
                        "GP" -> Constellation.GPS
                        "GL" -> Constellation.GLONASS
                        "GA" -> Constellation.GALILEO
                        "GB" -> Constellation.BEIDOU
                        "GQ" -> Constellation.QZSS
                        else -> Constellation.UNKNOWN
                    }

                    gsvSatellites[talkerID]?.add(
                        SatelliteInfo(
                            svId = svId,
                            constellation = constellation,
                            elevation = elevation,
                            azimuth = azimuth,
                            snr = snr,
                            used = false
                        )
                    )
                }

                index += 4
            }

            // Update satellite list on last message
            if (messageNum == totalMessages) {
                updateSatelliteList()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing GSV")
        }
    }

    private fun updateSatelliteList() {
        val allSatellites = mutableListOf<SatelliteInfo>()
        gsvSatellites.values.forEach { satellites ->
            allSatellites.addAll(satellites)
        }

        _satellites.value = allSatellites
        _currentPosition.value = _currentPosition.value.copy(
            satellitesInView = allSatellites.size
        )

        Timber.d("GSV: Total ${allSatellites.size} satellites in view")
    }

    private fun processGLL(parts: List<String>) {
        if (parts.size < 7) return

        try {
            val lat = parseNmeaCoordinate(parts[1], parts[2])
            val lon = parseNmeaCoordinate(parts[3], parts[4])
            val time = parts[5]
            val status = parts[6]

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
        if (parts.size < 9) return

        try {
            val courseTrue = parts[1].toDoubleOrNull() ?: 0.0
            val speedKnots = parts[5].toDoubleOrNull() ?: 0.0
            val speedKmh = parts[7].toDoubleOrNull() ?: 0.0

            _currentPosition.value = _currentPosition.value.copy(
                speedKnots = speedKnots,
                speedMs = speedKmh / 3.6,
                course = courseTrue
            )

            Timber.d("VTG: Course=$courseTrue°, Speed=$speedKnots kts")

        } catch (e: Exception) {
            Timber.e(e, "Error parsing VTG")
        }
    }

    private fun parseNmeaCoordinate(coord: String, dir: String): Double {
        val cleanCoord = coord.trim()
        val cleanDir = dir.trim()

        if (cleanCoord.isEmpty() || cleanCoord == "0" || cleanCoord == "0.0") return 0.0

        return try {
            val dot = cleanCoord.indexOf('.')
            if (dot < 2) return 0.0

            // NMEA format: ddmm.mmmm or dddmm.mmmm
            // For latitude: dd = degrees (2 digits)
            // For longitude: ddd = degrees (3 digits)
            val degreeLength = if (cleanDir == "N" || cleanDir == "S") 2 else 3

            if (cleanCoord.length < degreeLength + 3) return 0.0 // Need at least dd.mm or ddd.mm

            val degrees = cleanCoord.substring(0, degreeLength).toDouble()
            val minutes = cleanCoord.substring(degreeLength).toDouble()

            var result = degrees + (minutes / 60.0)

            // Apply direction
            if (cleanDir == "S" || cleanDir == "W") {
                result = -result
            }

            // Sanity check
            if (cleanDir == "N" || cleanDir == "S") {
                // Latitude must be between -90 and 90
                if (result < -90.0 || result > 90.0) return 0.0
            } else {
                // Longitude must be between -180 and 180
                if (result < -180.0 || result > 180.0) return 0.0
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error parsing coordinate: '$coord' '$dir'")
            0.0
        }
    }

    fun clearBuffer() {
        synchronized(_rawDataBuffer) {
            _rawDataBuffer.clear()
        }
    }

    fun getBufferSize(): Int = _rawDataBuffer.size

    fun reset() {
        _currentPosition.value = GnssPosition(fixStatus = "Waiting for GPS...")
        _satellites.value = emptyList()
        gsvSatellites.clear()
        gsvMessageCount.clear()
        clearBuffer()
    }
}