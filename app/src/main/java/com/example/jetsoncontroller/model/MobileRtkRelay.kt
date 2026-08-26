package com.example.jetsoncontroller.model

data class MobileRtkRelayConfig(
    val pipelineId: String,
    val available: Boolean = false,
    val upstreamHost: String? = null,
    val upstreamPort: Int? = null
)

data class RegisterMobileRtkRelayRequest(
    val pipelineId: String,
    val port: Int
)

data class MobileRtkRelayRegistration(
    val pipelineId: String,
    val relayHost: String,
    val relayPort: Int,
    val expiresAtEpochMillis: Long,
    val active: Boolean = false
)

data class MobileRtkRelayState(
    val active: Boolean = false,
    val preparing: Boolean = false,
    val pipelineId: String? = null,
    val upstreamHost: String? = null,
    val bytesFromCaster: Long = 0,
    val message: String? = null,
    val error: String? = null
)
