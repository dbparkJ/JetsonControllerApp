package com.example.jetsoncontroller.model

enum class EndpointTransport {
    WIFI_DIRECT,
    LAN
}

data class DeviceEndpoint(
    val deviceId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val transport:
        EndpointTransport
)
