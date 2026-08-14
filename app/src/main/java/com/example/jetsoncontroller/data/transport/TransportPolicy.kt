package com.example.jetsoncontroller.data.transport

fun canStartServerUpload(transportType: TransportType?): Boolean =
    transportType == TransportType.WIFI_DIRECT

fun serverUploadUnavailableMessage(transportType: TransportType?): String = when (transportType) {
    TransportType.LAN -> "LAN 연결에서는 서버 업로드를 시작할 수 없습니다. Wi-Fi Direct로 연결해 주세요."
    TransportType.BLE -> "서버 업로드를 시작하려면 Wi-Fi Direct로 연결해 주세요."
    TransportType.WIFI_DIRECT -> ""
    null -> "Jetson을 Wi-Fi Direct로 연결한 뒤 서버 업로드를 시작하세요."
}
