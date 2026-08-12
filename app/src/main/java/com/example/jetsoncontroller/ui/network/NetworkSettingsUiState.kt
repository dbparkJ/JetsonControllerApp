package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.transport.TransportType

data class NetworkSettingsUiState(
    val ssid: String = "",
    val password: String = "",
    val hidden: Boolean = false,
    val selectedAccessPointSsid: String? = null,
    val transportType: TransportType? = null,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val scanningAccessPoints: Boolean = false,
    val accessPointError: String? = null,
    val sending: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
