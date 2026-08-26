package com.example.jetsoncontroller.model

import android.bluetooth.BluetoothDevice

data class JetsonDevice(
    val device: android.bluetooth.BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val advertisedServiceUuids: List<String> = emptyList(),
    val scanGeneration: Long = 0L,
    val observedAtElapsedRealtimeMillis: Long = 0L
)
