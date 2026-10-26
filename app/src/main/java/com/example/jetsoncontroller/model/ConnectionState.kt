package com.example.jetsoncontroller.model

sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data class Connecting(
        val deviceName: String,
        val attempt: Int = 1,
        val deviceId: String? = null
    ) : ConnectionState

    data class Connected(
        val deviceName: String
    ) : ConnectionState

    data class RegistrationRequired(
        val deviceName: String,
        val deviceId: String
    ) : ConnectionState

    data class Ready(
        val deviceName: String
    ) : ConnectionState

    data class Error(
        val message: String,
        val deviceId: String? = null
    ) : ConnectionState
}
