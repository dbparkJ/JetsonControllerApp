package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.data.transport.TransportType

internal enum class UserConnectionStage(
    val label: String,
    val detail: String
) {
    PHONE_CONNECTED(
        label = "핸드폰과 연결",
        detail = "핸드폰이 장비에 직접 연결되어 있습니다."
    ),
    WIFI_CONNECTED(
        label = "Wi-Fi 연결",
        detail = "핸드폰과 장비가 같은 Wi-Fi에 연결되어 있습니다."
    ),
    OFFLINE(
        label = "오프라인",
        detail = "핸드폰과 장비의 연결을 확인해 주세요."
    )
}

internal fun userConnectionStage(
    online: Boolean,
    transportType: TransportType?
): UserConnectionStage = when {
    !online -> UserConnectionStage.OFFLINE
    transportType == TransportType.LAN -> UserConnectionStage.WIFI_CONNECTED
    else -> UserConnectionStage.PHONE_CONNECTED
}
