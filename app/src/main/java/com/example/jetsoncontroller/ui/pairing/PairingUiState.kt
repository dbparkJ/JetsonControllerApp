package com.example.jetsoncontroller.ui.pairing

import com.example.jetsoncontroller.model.PairingInfo

enum class PairingPhase {
    IDLE,
    QR_SCANNED,
    SEARCHING,
    CONNECTING,
    VERIFYING_IDENTITY,
    AUTHENTICATING,
    ENABLING_STATUS,
    READY,
    ERROR
}

data class PairingUiState(

    val phase:
        PairingPhase =
        PairingPhase.IDLE,

    val pairingInfo:
        PairingInfo? =
        null,

    val displayDeviceName:
        String? =
        null,

    val message:
        String =
        "Jetson 본체의 QR 코드를 스캔하세요.",

    val errorMessage:
        String? =
        null
)
