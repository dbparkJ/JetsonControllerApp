package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.model.DeviceNetworkAddress
import com.example.jetsoncontroller.model.SensorTestResult
import com.example.jetsoncontroller.ui.model.ConnectionState
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant

internal fun devConnectionState(online: Boolean, controlAvailable: Boolean): ConnectionState = when {
    controlAvailable -> ConnectionState.CONNECTED
    online -> ConnectionState.DEFAULT_CONNECTION
    else -> ConnectionState.OFFLINE
}

internal fun networkAddressLabel(address: DeviceNetworkAddress): String = when (address.kind.lowercase()) {
    "wifi", "wi-fi", "wlan" -> "Wi-Fi"
    "ethernet", "wired", "lan" -> "유선"
    "wifi_direct", "wi-fi direct", "wifi-direct", "p2p", "direct" -> "직접 연결"
    "tailscale" -> "Tailscale"
    else -> address.interfaceName
}

internal data class SensorTestBatch(val sensor: String, val previousResult: SensorTestResult?) {
    fun finished(testingSensor: String?, result: SensorTestResult?): Boolean =
        testingSensor == null && result != null && result != previousResult

    fun advance(online: Boolean, testingSensor: String?, results: Map<String, SensorTestResult>, error: String?): SensorTestBatch? = when {
        !online || (error != null && testingSensor == null) -> null
        !finished(testingSensor, results[sensor]) -> this
        sensor == "camera" -> SensorTestBatch("gps", results["gps"])
        else -> null
    }
}

internal enum class ServerTrashContent { NO_PROFILE, LOADING, ERROR, EMPTY, CONTENT }

internal fun serverTrashContent(hasProfile: Boolean, busy: Boolean, failed: Boolean, hasItems: Boolean): ServerTrashContent = when {
    !hasProfile -> ServerTrashContent.NO_PROFILE
    hasItems -> ServerTrashContent.CONTENT
    busy -> ServerTrashContent.LOADING
    failed -> ServerTrashContent.ERROR
    else -> ServerTrashContent.EMPTY
}

internal fun proxySessionTitle(folderName: String, receivedAt: String?, now: Instant = Instant.now()): String =
    if (Regex("^run-\\d{8}T\\d{6}.*").matches(folderName)) {
        val time = receivedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        "수신 결과 · ${Formats.time(time, now)}"
    } else folderName
