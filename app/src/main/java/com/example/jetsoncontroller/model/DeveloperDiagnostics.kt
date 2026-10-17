package com.example.jetsoncontroller.model

import com.google.gson.annotations.SerializedName

data class DeviceNetworkAddress(
    @SerializedName("interface") val interfaceName: String = "",
    val kind: String = "",
    val address: String = "",
    val family: String = "",
    val apiUrl: String = ""
)

data class DeviceNetworkDiagnostics(
    val interfaces: List<DeviceNetworkAddress> = emptyList(),
    val checkedAtEpochMillis: Long = 0
)

data class SensorTestResult(
    val sensor: String = "",
    val state: String = "",
    val message: String = "",
    val details: List<String> = emptyList(),
    val durationSeconds: Double = 0.0,
    val checkedAtEpochMillis: Long = 0
)
