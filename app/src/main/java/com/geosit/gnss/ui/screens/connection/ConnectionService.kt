package com.geosit.gnss.ui.screens.connection

interface ConnectionService {
    interface ConnectionListener {
        fun onDataReceived(data: ByteArray)
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
    }
    
    suspend fun connect()
    suspend fun disconnect()
    fun sendData(data: ByteArray)
    fun isConnected(): Boolean
}
