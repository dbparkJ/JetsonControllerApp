package com.example.jetsoncontroller.model

import android.bluetooth.BluetoothDevice

data class JetsonDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)
