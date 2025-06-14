package com.geosit.gnss.ui.screens.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionService(
    private val device: BluetoothDevice,
    private val listener: ConnectionService.ConnectionListener
) : ConnectionService {
    
    companion object {
        private const val TAG = "BluetoothService"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_BUFFER_SIZE = 1024
    }
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var isConnectedFlag = false
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Connecting to Bluetooth device: ${device.name}")
            
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            
            isConnectedFlag = true
            
            withContext(Dispatchers.Main) {
                listener.onConnected()
            }
            
            startReading()
            
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission error")
            withContext(Dispatchers.Main) {
                listener.onError("Bluetooth permission denied")
            }
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth connection failed")
            val errorMsg = when {
                e.message?.contains("timeout") == true -> "Connection timeout"
                e.message?.contains("refused") == true -> "Connection refused"
                else -> "Connection failed: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                listener.onError(errorMsg)
            }
        }
    }
    
    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            
            while (isActive && isConnectedFlag) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        withContext(Dispatchers.Main) {
                            listener.onDataReceived(data)
                        }
                    } else if (bytesRead == -1) {
                        Timber.d("Bluetooth stream ended")
                        break
                    }
                } catch (e: IOException) {
                    if (isConnectedFlag) {
                        Timber.e(e, "Bluetooth read error")
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
        Timber.d("Disconnecting Bluetooth")
        
        isConnectedFlag = false
        readJob?.cancel()
        
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: IOException) {
                Timber.e(e, "Error closing Bluetooth connection")
            }
        }
        
        withContext(Dispatchers.Main) {
            listener.onDisconnected()
        }
    }
    
    override fun sendData(data: ByteArray) {
        scope.launch {
            try {
                outputStream?.write(data)
                outputStream?.flush()
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
