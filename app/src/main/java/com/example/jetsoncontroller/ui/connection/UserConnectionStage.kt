package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.components.StatusTone

/**
 * How the app currently reaches the selected device, in the operator's words.
 *
 * The [tone] is attached here rather than being re-derived on each screen, so that the
 * same connection state can never be painted green on the home screen and amber in the
 * settings header. Note what the tone does *not* claim: a connected transport is
 * [StatusTone.SUCCESS] only as a statement about the control path. Whether a collection
 * is running, whether files were written and whether the server received them are
 * separate axes with their own state.
 */
internal enum class UserConnectionStage(
    val label: String,
    val detail: String,
    val tone: StatusTone
) {
    PHONE_CONNECTED(
        label = "제어 가능",
        detail = "핸드폰이 장비에 직접 연결되어 있습니다.",
        tone = StatusTone.SUCCESS
    ),
    WIFI_CONNECTED(
        label = "제어 가능",
        detail = "핸드폰과 장비가 같은 Wi-Fi에 연결되어 있습니다.",
        tone = StatusTone.SUCCESS
    ),
    BASIC_CONNECTED(
        label = "기본 연결",
        detail = "일부 기능을 사용할 수 있습니다. 전체 제어는 네트워크 연결이 필요합니다.",
        tone = StatusTone.WARNING
    ),
    OFFLINE(
        label = "오프라인",
        detail = "핸드폰과 장비의 연결을 확인해 주세요.",
        tone = StatusTone.ERROR
    )
}

internal fun userConnectionStage(
    online: Boolean,
    transportType: TransportType?
): UserConnectionStage = when {
    !online -> UserConnectionStage.OFFLINE
    transportType == TransportType.BLE -> UserConnectionStage.BASIC_CONNECTED
    transportType == TransportType.LAN -> UserConnectionStage.WIFI_CONNECTED
    else -> UserConnectionStage.PHONE_CONNECTED
}
