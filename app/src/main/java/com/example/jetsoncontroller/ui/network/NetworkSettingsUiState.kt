package com.example.jetsoncontroller.ui.network

data class NetworkSettingsUiState(
    val ssid: String = "",
    val password: String = "",
    val hidden: Boolean = false,
    val sending: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
