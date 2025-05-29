package com.geosit.gnss.ui.viewmodel

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geosit.gnss.data.connection.ConnectionManager
import com.geosit.gnss.data.model.Device
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    data class ConnectionUiState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val connectedDevice: Device? = null,
        val availableDevices: List<Device> = emptyList(),
        val dataRate: String = "0 B/s",
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    init {
        // Observe connection state changes
        viewModelScope.launch {
            connectionManager.connectionState.collect { connState ->
                _uiState.value = _uiState.value.copy(
                    isConnected = connState.isConnected,
                    connectedDevice = connState.connectedDevice,
                    isConnecting = false,
                    dataRate = connectionManager.getDataRate()
                )
            }
        }

        scanForDevices()
    }

    fun scanForDevices() {
        viewModelScope.launch {
            try {
                val devices = mutableListOf<Device>()

                // Scan Bluetooth devices
                try {
                    bluetoothAdapter?.bondedDevices?.forEach { btDevice ->
                        devices.add(
                            Device.Bluetooth(
                                device = btDevice,
                                name = btDevice.name ?: "Unknown Device",
                                address = btDevice.address
                            )
                        )
                    }
                } catch (e: SecurityException) {
                    Timber.e(e, "Bluetooth permission denied")
                }

                // Scan USB devices
                try {
                    val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                    availableDrivers.forEach { driver ->
                        val usbDevice = driver.device
                        devices.add(
                            Device.Usb(
                                device = usbDevice,
                                name = usbDevice.productName ?: "USB Device",
                                vendorId = usbDevice.vendorId,
                                productId = usbDevice.productId
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error scanning USB devices")
                }

                // Add saved TCP devices (for now, hardcoded example)
                // TODO: Load from database/preferences
                devices.add(
                    Device.Tcp(
                        host = "192.168.1.100",
                        port = 8080,
                        name = "Example TCP Device"
                    )
                )

                _uiState.value = _uiState.value.copy(
                    availableDevices = devices,
                    error = null
                )

                Timber.d("Found ${devices.size} devices")

            } catch (e: Exception) {
                Timber.e(e, "Error scanning devices")
                _uiState.value = _uiState.value.copy(
                    error = "Error scanning devices: ${e.message}"
                )
            }
        }
    }

    fun connectToDevice(device: Device) {
        if (_uiState.value.isConnecting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                connectedDevice = device,
                error = null
            )

            try {
                connectionManager.connect(device)
                // Connection state will be updated via the connectionManager flow
            } catch (e: Exception) {
                Timber.e(e, "Error connecting to device")
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                connectionManager.disconnect()
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    connectedDevice = null
                )
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting")
                _uiState.value = _uiState.value.copy(
                    error = "Disconnect failed: ${e.message}"
                )
            }
        }
    }

    fun addTcpDevice(host: String, port: Int, name: String) {
        val tcpDevice = Device.Tcp(host, port, name)
        val updatedDevices = _uiState.value.availableDevices + tcpDevice
        _uiState.value = _uiState.value.copy(availableDevices = updatedDevices)

        // TODO: Save to database/preferences
    }

    fun removeTcpDevice(device: Device.Tcp) {
        val updatedDevices = _uiState.value.availableDevices.filter { it != device }
        _uiState.value = _uiState.value.copy(availableDevices = updatedDevices)

        // TODO: Remove from database/preferences
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}