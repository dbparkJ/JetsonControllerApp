package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.data.network.WifiAccessPoint

data class NetworkSettingsUiState(
    val ssid: String = "",
    val password: String = "",
    val hidden: Boolean = false,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val scanningAccessPoints: Boolean = false,
    val accessPointError: String? = null,
    val sending: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
