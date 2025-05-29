package com.geosit.gnss.data.model

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice

sealed class Device {
    data class Bluetooth(
        val device: BluetoothDevice,
        val name: String,
        val address: String
    ) : Device()
    
    data class Usb(
        val device: UsbDevice,
        val name: String,
        val vendorId: Int,
        val productId: Int
    ) : Device()
    
    data class Tcp(
        val host: String,
        val port: Int,
        val name: String
    ) : Device()
}

fun Device.displayName(): String = when (this) {
    is Device.Bluetooth -> name
    is Device.Usb -> name
    is Device.Tcp -> "$name ($host:$port)"
}

fun Device.connectionInfo(): String = when (this) {
    is Device.Bluetooth -> "Bluetooth • $address"
    is Device.Usb -> String.format("USB • VID:%04X PID:%04X", vendorId, productId)
    is Device.Tcp -> "TCP/IP • $host:$port"
}
