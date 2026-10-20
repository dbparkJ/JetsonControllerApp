package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.model.Tone

/**
 * The words the app uses for a device's connection. The vocabulary is fixed by the
 * device-console plan: screens show these labels and do not invent their own.
 */
internal enum class DeviceConnectionState(val label: String) {
    CONNECTED("연결됨"),
    AVAILABLE("연결 가능"),
    OFFLINE("오프라인"),
    NO_RESPONSE("응답 없음"),
    CHECKING("확인 중")
}

/** One connection projection. Every screen that shows a device's connection reads it from here. */
internal data class DeviceConnectionUi(
    val state: DeviceConnectionState,
    val label: String,
    val detail: String,
    val tone: Tone
)

/**
 * The single online rule for the selected device: the phone holds a transport to it.
 * BLE is a real link and counts here. Commands that change the device still require
 * [hasFullControl], so a BLE link never enables them.
 */
internal fun deviceIsOnline(transportType: TransportType?): Boolean = transportType != null

/** Device-changing commands run over LAN or Wi-Fi Direct only. */
internal fun hasFullControl(transportType: TransportType?): Boolean =
    transportType == TransportType.LAN || transportType == TransportType.WIFI_DIRECT

/**
 * Projects one device's connection into the fixed vocabulary.
 *
 * @param isOnline the result of [deviceIsOnline] for this device.
 * @param transportType the transport the phone holds to this device, when online.
 * @param checking a connection attempt to this device is running.
 * @param available the device was found on the local network but is not connected.
 * @param connectFailed the latest attempt to reach this device failed.
 * @param lastSeenText readable last response time, shown while the device is offline.
 */
internal fun deviceConnectionUi(
    isOnline: Boolean,
    transportType: TransportType?,
    checking: Boolean = false,
    available: Boolean = false,
    connectFailed: Boolean = false,
    lastSeenText: String? = null,
    localDeviceWord: String = "휴대폰"
): DeviceConnectionUi = when {
    isOnline && transportType != null -> when (transportType) {
        TransportType.BLE -> connected(
            "기본 연결입니다. 일부 기능만 사용할 수 있습니다.",
            Tone.WARNING
        )
        TransportType.LAN -> connected(
            "같은 네트워크로 연결되어 있습니다.",
            Tone.SUCCESS
        )
        else -> connected(
            "장치에 직접 연결되어 있습니다.",
            Tone.SUCCESS
        )
    }
    checking -> DeviceConnectionUi(
        state = DeviceConnectionState.CHECKING,
        label = DeviceConnectionState.CHECKING.label,
        detail = "장치 응답을 기다리고 있습니다.",
        tone = Tone.PENDING
    )
    available -> DeviceConnectionUi(
        state = DeviceConnectionState.AVAILABLE,
        label = DeviceConnectionState.AVAILABLE.label,
        detail = "같은 네트워크에서 장치를 찾았습니다.",
        tone = Tone.INFO
    )
    connectFailed -> DeviceConnectionUi(
        state = DeviceConnectionState.NO_RESPONSE,
        label = DeviceConnectionState.NO_RESPONSE.label,
        detail = "장치가 응답하지 않습니다. 같은 네트워크인지 확인해 주세요.",
        tone = Tone.WARNING
    )
    else -> DeviceConnectionUi(
        state = DeviceConnectionState.OFFLINE,
        label = DeviceConnectionState.OFFLINE.label,
        detail = lastSeenText?.let { "마지막 응답 $it" } ?: "이 ${localDeviceWord}과 연결한 기록이 없습니다.",
        tone = Tone.UNKNOWN
    )
}

private fun connected(detail: String, tone: Tone) = DeviceConnectionUi(
    state = DeviceConnectionState.CONNECTED,
    label = DeviceConnectionState.CONNECTED.label,
    detail = detail,
    tone = tone
)
