package com.example.jetsoncontroller.data.transport

fun canStartServerUpload(transportType: TransportType?): Boolean =
    transportType == TransportType.LAN

fun serverUploadUnavailableMessage(transportType: TransportType?): String = when (transportType) {
    TransportType.LAN -> ""
    TransportType.BLE -> "서버로 보내려면 장치를 휴대폰과 같은 네트워크(LAN)로 연결해 주세요."
    TransportType.WIFI_DIRECT ->
        "장치에 직접 연결(Wi-Fi Direct)한 상태에서는 서버로 보낼 수 없습니다. 같은 네트워크(LAN)로 연결해 주세요."
    null -> "장치를 휴대폰과 같은 네트워크(LAN)에 연결한 뒤 서버로 보내세요."
}
