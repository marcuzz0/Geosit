package com.geosit.gnss.data.connection

import android.content.Context
import android.hardware.usb.UsbManager
import com.geosit.gnss.data.gnss.GnssDataProcessor
import com.geosit.gnss.data.model.Device
import com.geosit.gnss.data.model.displayName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gnssDataProcessor: GnssDataProcessor
) {

    // Interface for data received notifications
    interface DataReceivedListener {
        fun onDataReceived(data: ByteArray)
    }

    private val dataListeners = mutableListOf<DataReceivedListener>()

    data class ConnectionState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val connectedDevice: Device? = null,
        val error: String? = null,
        val dataReceivedCount: Long = 0,
        val lastDataTimestamp: Long = 0,
        val bytesReceived: Long = 0,
        val dataRate: Long = 0 // Bytes per second
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

    // Coroutine scopes
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // For data rate calculation
    private val bytesReceivedAtomic = AtomicLong(0)
    private var dataRateCalculationJob: Job? = null

    private val connectionListener = object : ConnectionService.ConnectionListener {
        override fun onDataReceived(data: ByteArray) {
            // Process data on IO thread to avoid blocking UI
            ioScope.launch {
                try {
                    val dataSize = data.size
                    Timber.d("Data received: $dataSize bytes")

                    // Update counters atomically
                    bytesReceivedAtomic.addAndGet(dataSize.toLong())

                    // Process GNSS data on IO thread
                    gnssDataProcessor.processData(data)

                    // Notify data listeners
                    synchronized(dataListeners) {
                        dataListeners.forEach { listener ->
                            try {
                                listener.onDataReceived(data)
                            } catch (e: Exception) {
                                Timber.e(e, "Error in data listener")
                            }
                        }
                    }

                    // Update UI on main thread only with metadata
                    withContext(Dispatchers.Main) {
                        val currentState = _connectionState.value
                        _connectionState.value = currentState.copy(
                            dataReceivedCount = currentState.dataReceivedCount + 1,
                            lastDataTimestamp = System.currentTimeMillis(),
                            bytesReceived = currentState.bytesReceived + dataSize
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing GNSS data")
                    withContext(Dispatchers.Main) {
                        _connectionState.value = _connectionState.value.copy(
                            error = "Processing error: ${e.message}"
                        )
                    }
                }
            }
        }

        override fun onConnected() {
            Timber.d("Device connected successfully")
            _connectionState.value = _connectionState.value.copy(
                isConnected = true,
                isConnecting = false,
                error = null,
                dataReceivedCount = 0,
                bytesReceived = 0,
                lastDataTimestamp = System.currentTimeMillis()
            )

            // Start data rate calculation
            startDataRateCalculation()
        }

        override fun onDisconnected() {
            Timber.d("Device disconnected")
            stopDataRateCalculation()

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
            stopDataRateCalculation()

            _connectionState.value = _connectionState.value.copy(
                isConnected = false,
                isConnecting = false,
                error = error
            )
            currentService = null
        }
    }

    private fun startDataRateCalculation() {
        dataRateCalculationJob?.cancel()
        bytesReceivedAtomic.set(0)

        dataRateCalculationJob = scope.launch {
            while (isActive) {
                delay(1000) // Calculate every second

                val bytesInLastSecond = bytesReceivedAtomic.getAndSet(0)
                _connectionState.value = _connectionState.value.copy(
                    dataRate = bytesInLastSecond
                )
            }
        }
    }

    private fun stopDataRateCalculation() {
        dataRateCalculationJob?.cancel()
        dataRateCalculationJob = null
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
            withContext(Dispatchers.IO) {
                gnssDataProcessor.reset()
            }

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
            stopDataRateCalculation()
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

        ioScope.launch {
            try {
                currentService?.sendData(data)
                Timber.d("Sent ${data.size} bytes")
            } catch (e: Exception) {
                Timber.e(e, "Error sending data")
                withContext(Dispatchers.Main) {
                    _connectionState.value = _connectionState.value.copy(
                        error = "Send error: ${e.message}"
                    )
                }
            }
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
        val bytesPerSecond = _connectionState.value.dataRate

        return when {
            bytesPerSecond == 0L -> "0 B/s"
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }

    fun getRawDataBuffer(maxSize: Int = 10240): ByteArray {
        return gnssDataProcessor.rawDataBuffer.takeLast(maxSize).toByteArray()
    }

    fun getBufferSize(): Int {
        return gnssDataProcessor.getBufferSize()
    }

    fun clearBuffer() {
        ioScope.launch {
            gnssDataProcessor.clearBuffer()
        }
    }

    // Methods for managing data listeners
    fun addDataListener(listener: DataReceivedListener) {
        synchronized(dataListeners) {
            dataListeners.add(listener)
            Timber.d("Data listener added. Total listeners: ${dataListeners.size}")
        }
    }

    fun removeDataListener(listener: DataReceivedListener) {
        synchronized(dataListeners) {
            dataListeners.remove(listener)
            Timber.d("Data listener removed. Total listeners: ${dataListeners.size}")
        }
    }

    // Cleanup
    fun cleanup() {
        Timber.d("Cleaning up ConnectionManager")
        scope.launch {
            disconnect()
        }
        // Cancel scopes after disconnect
        scope.cancel()
        ioScope.cancel()
    }
}