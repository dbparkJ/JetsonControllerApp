package com.example.jetsoncontroller.model

data class WifiProvisionStatus(
    val state: String,
    val ssid: String? = null,
    val message: String? = null
)

data class WifiProvisionReceipt(
    val ssid: String,
    val statusPollingAvailable: Boolean,
    val lanHandoffRequired: Boolean = false,
    val deviceId: String? = null
)

enum class WifiProvisionPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    FAILED,
    UNKNOWN
}

fun WifiProvisionStatus.phase(): WifiProvisionPhase =
    runCatching { WifiProvisionPhase.valueOf(state.trim().uppercase()) }
        .getOrDefault(WifiProvisionPhase.UNKNOWN)
