package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.model.ConnectionState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.data.transport.TransportType

/** A retained transport is not evidence that the device is still answering. */
internal fun workDeviceReachable(connected: Boolean, freshness: StatusFreshness): Boolean =
    connected && freshness != StatusFreshness.STALE

internal fun workConnectionLabel(online: Boolean, transport: TransportType?): String = when {
    !online -> ConnectionState.OFFLINE.word
    transport == TransportType.LAN -> "같은 네트워크"
    transport == TransportType.WIFI_DIRECT -> "직접 연결"
    transport == TransportType.BLE -> ConnectionState.DEFAULT_CONNECTION.word
    else -> "미확인"
}

/** Root chrome calls attention to an unreachable selected device; offline list badges stay neutral. */
internal fun workRootConnectionTone(
    connectionTone: Tone,
    reachable: Boolean,
    checking: Boolean = false,
    basicConnection: Boolean = false,
    permissionMissing: Boolean = false,
    credentialsRejected: Boolean = false
): Tone = when {
    credentialsRejected -> ConnectionState.AUTHENTICATION_REQUIRED.tone
    permissionMissing -> ConnectionState.PERMISSION_REQUIRED.tone
    checking -> ConnectionState.CHECKING.tone
    !reachable -> ConnectionState.NO_RESPONSE.tone
    basicConnection -> ConnectionState.DEFAULT_CONNECTION.tone
    else -> connectionTone
}
