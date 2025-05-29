package com.geosit.gnss.data.connection

import android.content.Context
import android.hardware.usb.UsbManager
import com.geosit.gnss.data.gnss.GnssDataProcessor
import com.geosit.gnss.data.model.Device
import com.geosit.gnss.data.model.displayName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gnssDataProcessor: GnssDataProcessor
) {

    data class ConnectionState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val connectedDevice: Device? = null,
        val receivedData: ByteArray? = null,
        val error: String? = null,
        val dataReceivedCount: Long = 0,
        val lastDataTimestamp: Long = 0
    )

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Expose GNSS data from processor
    val currentPosition = gnssDataProcessor.currentPosition
    val satellites = gnssDataProcessor.satellites
    val gnssStatistics = gnssDataProcessor.statistics
    val dataRate = gnssDataProcessor.dataRate

    private var currentService: ConnectionService? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val connectionListener = object : ConnectionService.ConnectionListener {
        override fun onDataReceived(data: ByteArray) {
            Timber.d("Data received: ${data.size} bytes")

            // Log first 20 bytes in hex for debug
            if (data.isNotEmpty()) {
                val hexString = data.take(20).joinToString(" ") {
                    String.format("%02X", it)
                }
                Timber.d("Data hex (first 20): $hexString")
            }

            val currentCount = _connectionState.value.dataReceivedCount

            _connectionState.value = _connectionState.value.copy(
                receivedData = data,
                dataReceivedCount = currentCount + 1,
                lastDataTimestamp = System.currentTimeMillis()
            )

            // Process GNSS data
            try {
                gnssDataProcessor.processData(data)
            } catch (e: Exception) {
                Timber.e(e, "Error processing GNSS data")
            }
        }

        override fun onConnected() {
            Timber.d("Device connected successfully")
            _connectionState.value = _connectionState.value.copy(
                isConnected = true,
                isConnecting = false,
                error = null,
                dataReceivedCount = 0,
                lastDataTimestamp = System.currentTimeMillis()
            )
        }

        override fun onDisconnected() {
            Timber.d("Device disconnected")
            _connectionState.value = ConnectionState(
                isConnected = false,
                isConnecting = false,
                connectedDevice = null,
                error = null
            )
            currentService = null

            // Reset GNSS processor
            gnssDataProcessor.reset()
        }

        override fun onError(error: String) {
            Timber.e("Connection error: $error")
            _connectionState.value = _connectionState.value.copy(
                isConnected = false,
                isConnecting = false,
                error = error
            )
            currentService = null
        }
    }

    suspend fun connect(device: Device) {
        Timber.d("Attempting to connect to device: ${device.displayName()}")

        // Disconnect any existing connection
        disconnect()

        _connectionState.value = _connectionState.value.copy(
            isConnecting = true,
            connectedDevice = device,
            error = null
        )

        try {
            currentService = when (device) {
                is Device.Bluetooth -> {
                    Timber.d("Creating Bluetooth connection service")
                    BluetoothConnectionService(device.device, connectionListener)
                }
                is Device.Usb -> {
                    Timber.d("Creating USB connection service")
                    UsbConnectionService(device.device, usbManager, connectionListener)
                }
                is Device.Tcp -> {
                    Timber.d("Creating TCP connection service")
                    TcpConnectionService(device.host, device.port, connectionListener)
                }
            }

            // Clear any previous GNSS data
            gnssDataProcessor.reset()

            // Attempt connection
            currentService?.connect()

        } catch (e: Exception) {
            Timber.e(e, "Failed to create connection service")
            _connectionState.value = _connectionState.value.copy(
                isConnecting = false,
                error = "Failed to connect: ${e.message}"
            )
            currentService = null
        }
    }

    suspend fun disconnect() {
        Timber.d("Disconnecting current device")

        try {
            currentService?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error during disconnect")
        } finally {
            currentService = null
            _connectionState.value = ConnectionState()
            gnssDataProcessor.reset()
        }
    }

    fun sendData(data: ByteArray) {
        if (!isConnected()) {
            Timber.w("Cannot send data - not connected")
            return
        }

        try {
            currentService?.sendData(data)
            Timber.d("Sent ${data.size} bytes")
        } catch (e: Exception) {
            Timber.e(e, "Error sending data")
        }
    }

    fun isConnected(): Boolean = currentService?.isConnected() == true

    fun getConnectionInfo(): String {
        val state = _connectionState.value
        return when {
            state.isConnecting -> "Connecting..."
            state.isConnected -> {
                val device = state.connectedDevice
                when (device) {
                    is Device.Bluetooth -> "Bluetooth: ${device.name}"
                    is Device.Usb -> "USB: ${device.name}"
                    is Device.Tcp -> "TCP: ${device.host}:${device.port}"
                    null -> "Connected"
                }
            }
            else -> "Not connected"
        }
    }

    fun getDataRate(): String {
        val bytesPerSecond = dataRate.value
        return when {
            bytesPerSecond == 0 -> "0 B/s"
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }

    fun getRawDataBuffer(): ByteArray {
        return gnssDataProcessor.rawDataBuffer.toByteArray()
    }

    fun getBufferSize(): Int {
        return gnssDataProcessor.getBufferSize()
    }

    fun clearBuffer() {
        gnssDataProcessor.clearBuffer()
    }

    // Configuration commands for GNSS receivers
    fun sendUbxCommand(messageClass: Int, messageId: Int, payload: ByteArray = byteArrayOf()) {
        val message = buildUbxMessage(messageClass, messageId, payload)
        sendData(message)
    }

    fun configureGnssReceiver() {
        // For UBX devices
        if (gnssDataProcessor.statistics.value.ubxMessages > 0) {
            // Configure update rate to 1Hz
            sendUbxCommand(0x06, 0x08, byteArrayOf(
                0xE8.toByte(), 0x03, // Measurement rate (1000ms)
                0x01, 0x00, // Navigation rate (1)
                0x01, 0x00  // Time reference (GPS)
            ))

            // Enable NAV-PVT message
            sendUbxCommand(0x06, 0x01, byteArrayOf(
                0x01, // NAV class
                0x07, // PVT message
                0x01  // Enable on UART1
            ))

            // Enable NAV-SAT message
            sendUbxCommand(0x06, 0x01, byteArrayOf(
                0x01, // NAV class
                0x35, // SAT message
                0x01  // Enable on UART1
            ))
        } else {
            // For NMEA devices, request GGA sentence
            // Most NMEA devices respond to standard commands
            val enableGGA = "\$PSRF103,00,00,01,01*25\r\n" // Enable GGA at 1Hz
            val enableRMC = "\$PSRF103,04,00,01,01*21\r\n" // Enable RMC at 1Hz
            val enableGSV = "\$PSRF103,03,00,05,01*26\r\n" // Enable GSV every 5 fixes

            sendData(enableGGA.toByteArray())
            sendData(enableRMC.toByteArray())
            sendData(enableGSV.toByteArray())

            Timber.d("Sent NMEA configuration commands")
        }
    }

    private fun buildUbxMessage(msgClass: Int, msgId: Int, payload: ByteArray): ByteArray {
        val message = mutableListOf<Byte>()

        // Sync chars
        message.add(0xB5.toByte())
        message.add(0x62.toByte())

        // Class and ID
        message.add(msgClass.toByte())
        message.add(msgId.toByte())

        // Length (little endian)
        message.add((payload.size and 0xFF).toByte())
        message.add((payload.size shr 8).toByte())

        // Payload
        message.addAll(payload.toList())

        // Calculate checksum
        var ckA = 0
        var ckB = 0
        for (i in 2 until message.size) {
            ckA = (ckA + message[i].toInt()) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }

        message.add(ckA.toByte())
        message.add(ckB.toByte())

        return message.toByteArray()
    }

    // Cleanup
    fun cleanup() {
        Timber.d("Cleaning up ConnectionManager")
        scope.launch {
            disconnect()
        }
    }
}