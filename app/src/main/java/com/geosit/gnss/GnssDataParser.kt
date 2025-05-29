package com.geosit.gnss.data.gnss

import timber.log.Timber

sealed class GnssData {
    data class UbxMessage(
        val messageClass: Int,
        val messageId: Int,
        val payload: ByteArray
    ) : GnssData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UbxMessage

            if (messageClass != other.messageClass) return false
            if (messageId != other.messageId) return false
            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = messageClass
            result = 31 * result + messageId
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class NmeaSentence(
        val sentence: String
    ) : GnssData()
}

class GnssDataParser {
    companion object {
        private const val TAG = "GnssDataParser"

        // UBX constants
        private const val UBX_SYNC_1 = 0xB5.toByte()
        private const val UBX_SYNC_2 = 0x62.toByte()

        // NMEA constants
        private const val NMEA_START = '$'.code.toByte()
        private const val NMEA_END_1 = '\r'.code.toByte()
        private const val NMEA_END_2 = '\n'.code.toByte()
    }

    private val buffer = mutableListOf<Byte>()
    private var parseState = ParseState.SEARCHING_SYNC
    private var ubxLength = 0
    private var ubxClass = 0
    private var ubxId = 0

    private enum class ParseState {
        SEARCHING_SYNC,
        UBX_SYNC2,
        UBX_CLASS,
        UBX_ID,
        UBX_LENGTH1,
        UBX_LENGTH2,
        UBX_PAYLOAD,
        UBX_CK_A,
        UBX_CK_B,
        NMEA_SENTENCE
    }

    fun parseData(data: ByteArray): List<GnssData> {
        val messages = mutableListOf<GnssData>()

        data.forEach { byte ->
            when (parseState) {
                ParseState.SEARCHING_SYNC -> {
                    when (byte) {
                        UBX_SYNC_1 -> {
                            buffer.clear()
                            buffer.add(byte)
                            parseState = ParseState.UBX_SYNC2
                        }
                        NMEA_START -> {
                            buffer.clear()
                            buffer.add(byte)
                            parseState = ParseState.NMEA_SENTENCE
                        }
                    }
                }

                ParseState.UBX_SYNC2 -> {
                    if (byte == UBX_SYNC_2) {
                        buffer.add(byte)
                        parseState = ParseState.UBX_CLASS
                    } else {
                        parseState = ParseState.SEARCHING_SYNC
                    }
                }

                ParseState.UBX_CLASS -> {
                    buffer.add(byte)
                    ubxClass = byte.toInt() and 0xFF
                    parseState = ParseState.UBX_ID
                }

                ParseState.UBX_ID -> {
                    buffer.add(byte)
                    ubxId = byte.toInt() and 0xFF
                    parseState = ParseState.UBX_LENGTH1
                }

                ParseState.UBX_LENGTH1 -> {
                    buffer.add(byte)
                    ubxLength = byte.toInt() and 0xFF
                    parseState = ParseState.UBX_LENGTH2
                }

                ParseState.UBX_LENGTH2 -> {
                    buffer.add(byte)
                    ubxLength = ubxLength or ((byte.toInt() and 0xFF) shl 8)
                    parseState = if (ubxLength > 0) ParseState.UBX_PAYLOAD else ParseState.UBX_CK_A
                }

                ParseState.UBX_PAYLOAD -> {
                    buffer.add(byte)
                    if (buffer.size >= 6 + ubxLength) {
                        parseState = ParseState.UBX_CK_A
                    }
                }

                ParseState.UBX_CK_A -> {
                    buffer.add(byte)
                    parseState = ParseState.UBX_CK_B
                }

                ParseState.UBX_CK_B -> {
                    buffer.add(byte)

                    // Verify checksum
                    if (verifyUbxChecksum(buffer)) {
                        val payload = buffer.subList(6, 6 + ubxLength).toByteArray()
                        messages.add(GnssData.UbxMessage(ubxClass, ubxId, payload))

                        Timber.d("UBX message: class=0x${ubxClass.toString(16)}, id=0x${ubxId.toString(16)}, len=$ubxLength")
                    } else {
                        Timber.w("UBX checksum failed")
                    }

                    parseState = ParseState.SEARCHING_SYNC
                }

                ParseState.NMEA_SENTENCE -> {
                    buffer.add(byte)

                    if (buffer.size >= 2) {
                        val lastTwo = buffer.takeLast(2)
                        if (lastTwo[0] == NMEA_END_1 && lastTwo[1] == NMEA_END_2) {
                            val sentence = String(buffer.toByteArray()).trim()
                            messages.add(GnssData.NmeaSentence(sentence))

                            Timber.d("NMEA sentence: $sentence")
                            parseState = ParseState.SEARCHING_SYNC
                        }
                    }

                    // Prevent buffer overflow
                    if (buffer.size > 82) { // Max NMEA sentence length
                        parseState = ParseState.SEARCHING_SYNC
                    }
                }
            }
        }

        return messages
    }

    private fun verifyUbxChecksum(data: List<Byte>): Boolean {
        if (data.size < 8) return false

        var ckA = 0
        var ckB = 0

        // Calculate checksum from class to end of payload
        for (i in 2 until data.size - 2) {
            ckA = (ckA + (data[i].toInt() and 0xFF)) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }

        return ckA == (data[data.size - 2].toInt() and 0xFF) &&
                ckB == (data[data.size - 1].toInt() and 0xFF)
    }
}