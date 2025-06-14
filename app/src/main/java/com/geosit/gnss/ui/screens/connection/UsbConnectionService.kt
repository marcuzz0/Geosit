package com.geosit.gnss.ui.screens.connection

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException

class UsbConnectionService(
    private val device: UsbDevice,
    private val usbManager: UsbManager,
    private val listener: ConnectionService.ConnectionListener
) : ConnectionService {
    
    companion object {
        private const val TAG = "UsbService"
        private const val READ_BUFFER_SIZE = 1024
        private const val BAUD_RATE = 115200
    }
    
    private var driver: UsbSerialDriver? = null
    private var port: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var isConnectedFlag = false
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Connecting to USB device: ${device.productName}")
            
            driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver == null) {
                withContext(Dispatchers.Main) {
                    listener.onError("No USB serial driver available")
                }
                return@withContext
            }
            
            connection = usbManager.openDevice(device)
            if (connection == null) {
                withContext(Dispatchers.Main) {
                    listener.onError("USB permission denied")
                }
                return@withContext
            }
            
            port = driver?.ports?.firstOrNull()
            port?.open(connection)
            port?.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            isConnectedFlag = true
            
            withContext(Dispatchers.Main) {
                listener.onConnected()
            }
            
            startReading()
            
        } catch (e: IOException) {
            Timber.e(e, "USB connection error")
            withContext(Dispatchers.Main) {
                listener.onError("USB connection failed: ${e.message}")
            }
        }
    }
    
    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            
            while (isActive && isConnectedFlag) {
                try {
                    val bytesRead = port?.read(buffer, 1000) ?: 0
                    
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        withContext(Dispatchers.Main) {
                            listener.onDataReceived(data)
                        }
                    }
                } catch (e: IOException) {
                    if (isConnectedFlag) {
                        Timber.e(e, "USB read error")
                        withContext(Dispatchers.Main) {
                            listener.onError("Read error: ${e.message}")
                        }
                    }
                    break
                }
            }
            
            if (isConnectedFlag) {
                disconnect()
            }
        }
    }
    
    override suspend fun disconnect() {
        Timber.d("Disconnecting USB")
        
        isConnectedFlag = false
        readJob?.cancel()
        
        withContext(Dispatchers.IO) {
            try {
                port?.close()
                connection?.close()
            } catch (e: IOException) {
                Timber.e(e, "Error closing USB connection")
            }
        }
        
        withContext(Dispatchers.Main) {
            listener.onDisconnected()
        }
    }
    
    override fun sendData(data: ByteArray) {
        scope.launch {
            try {
                port?.write(data, 1000)
            } catch (e: IOException) {
                Timber.e(e, "Error sending data")
                withContext(Dispatchers.Main) {
                    listener.onError("Send error: ${e.message}")
                }
            }
        }
    }
    
    override fun isConnected(): Boolean = isConnectedFlag
}
