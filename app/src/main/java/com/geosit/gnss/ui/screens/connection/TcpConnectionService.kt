package com.geosit.gnss.data.connection

import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TcpConnectionService(
    private val host: String,
    private val port: Int,
    private val listener: ConnectionService.ConnectionListener
) : ConnectionService {
    
    companion object {
        private const val TAG = "TcpService"
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 5000
        private const val READ_BUFFER_SIZE = 1024
    }
    
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var isConnectedFlag = false
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Connecting to TCP device: $host:$port")
            
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
                soTimeout = READ_TIMEOUT
            }
            
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            
            isConnectedFlag = true
            
            withContext(Dispatchers.Main) {
                listener.onConnected()
            }
            
            startReading()
            
        } catch (e: IOException) {
            Timber.e(e, "TCP connection error")
            val errorMsg = when (e) {
                is SocketTimeoutException -> "Connection timeout"
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
                        Timber.d("TCP stream ended")
                        break
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal timeout, continue
                    continue
                } catch (e: IOException) {
                    if (isConnectedFlag) {
                        Timber.e(e, "TCP read error")
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
        Timber.d("Disconnecting TCP")
        
        isConnectedFlag = false
        readJob?.cancel()
        
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: IOException) {
                Timber.e(e, "Error closing TCP connection")
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
