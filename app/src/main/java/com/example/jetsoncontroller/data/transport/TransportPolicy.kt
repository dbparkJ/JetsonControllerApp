package com.example.jetsoncontroller.data.transport

fun canStartServerUpload(transportType: TransportType?): Boolean =
    transportType == TransportType.LAN

fun serverUploadUnavailableMessage(transportType: TransportType?): String = when (transportType) {
    TransportType.LAN -> ""
    TransportType.BLE -> "서버 업로드를 시작하려면 Jetson을 LAN으로 연결해 주세요."
    TransportType.WIFI_DIRECT ->
        "Wi-Fi Direct 연결에서는 서버 업로드를 시작할 수 없습니다. LAN으로 연결해 주세요."
    null -> "Jetson을 LAN으로 연결한 뒤 서버 업로드를 시작하세요."
}
