package com.example.jetsoncontroller.model

sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data class Connecting(
        val deviceName: String
    ) : ConnectionState

    data class Connected(
        val deviceName: String
    ) : ConnectionState

    data class Ready(
        val deviceName: String
    ) : ConnectionState

    data class Error(
        val message: String
    ) : ConnectionState
}
