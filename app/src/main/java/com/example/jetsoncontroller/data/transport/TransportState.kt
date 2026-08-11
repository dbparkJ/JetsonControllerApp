package com.example.jetsoncontroller.data.transport

enum class TransportType {
    BLE,
    WIFI_DIRECT,
    LAN
}

sealed interface TransportState {

    data object Disconnected :
        TransportState

    data class Connecting(
        val type: TransportType
    ) : TransportState

    data class Connected(
        val type: TransportType,
        val endpoint: String? = null
    ) : TransportState

    data class Error(
        val type: TransportType?,
        val message: String
    ) : TransportState
}
