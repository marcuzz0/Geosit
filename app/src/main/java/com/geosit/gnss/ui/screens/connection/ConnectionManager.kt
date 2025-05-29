package com.geosit.gnss.data.connection

import android.content.Context
import android.hardware.usb.UsbManager
import com.geosit.gnss.data.gnss.GnssDataProcessor
import com.geosit.gnss.data.model.Device
import com.geosit.gnss.data.model.displayName  // <-- Aggiungi questo import
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

    // Expose GNSS position from processor
    val currentPosition = gnssDataProcessor.currentPosition

    private var currentService: ConnectionService? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val connectionListener = object : ConnectionService.ConnectionListener {
        override fun onDataReceived(data: ByteArray) {
            Timber.d("Data received: ${data.size} bytes")

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
            gnssDataProcessor.clearBuffer()

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
        val state = _connectionState.value
        if (!state.isConnected) return "0 B/s"

        val timeDiff = System.currentTimeMillis() - state.lastDataTimestamp
        if (timeDiff > 5000) return "0 B/s" // No data for 5 seconds

        // Simple estimation based on last received data
        val bytesPerSecond = if (state.receivedData != null && timeDiff > 0) {
            (state.receivedData.size * 1000 / timeDiff).toInt()
        } else {
            0
        }

        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> "${bytesPerSecond / (1024 * 1024)} MB/s"
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