package com.example.jetsoncontroller.ui.pairing

import com.example.jetsoncontroller.model.PairingInfo
import com.example.jetsoncontroller.protocol.PairingQrParser

internal const val INVALID_PAIRING_CODE = "등록 코드를 확인하세요. QR 아래에 있는 전체 코드를 입력하세요."

internal fun parsePairingCode(rawValue: String): PairingInfo? =
    runCatching { PairingQrParser.parse(rawValue) }.getOrNull()

internal fun maskedPairingCode(info: PairingInfo?): String =
    info?.let { "등록 코드 •••• ${it.shortId}" } ?: "등록 코드 미확인"

internal fun pairingProgressText(phase: PairingPhase): String = when (phase) {
    PairingPhase.SEARCHING -> "장치를 찾는 중입니다."
    PairingPhase.CONNECTING -> "장치에 연결하는 중입니다."
    PairingPhase.VERIFYING_IDENTITY -> "장치를 확인하는 중입니다."
    PairingPhase.AUTHENTICATING -> "인증하는 중입니다."
    PairingPhase.ENABLING_STATUS -> "장치 상태를 확인하는 중입니다."
    PairingPhase.READY -> "장치 등록을 마쳤습니다."
    else -> "장치 등록을 준비하는 중입니다."
}

// Repository and platform diagnostics can contain identifiers. Only mapped copy reaches a sheet.
internal fun pairingFailureText(message: String?): String = when {
    message?.contains("여러 대") == true ->
        "장치가 여러 대 검색되었습니다. 대상 장치만 켠 뒤 다시 시도하세요."
    message?.contains("권한") == true ->
        "가까운 장치를 찾으려면 블루투스 권한이 필요합니다."
    message?.contains("인증") == true || message?.contains("일치") == true ->
        "장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요."
    message?.contains("중단") == true ->
        "장치 등록이 중단되었습니다. 다시 시도하세요."
    else -> "장치를 찾지 못했습니다. 장치 전원과 거리를 확인하세요."
}

internal fun PairingPhase.isPairingInProgress(): Boolean = this in setOf(
    PairingPhase.SEARCHING, PairingPhase.CONNECTING, PairingPhase.VERIFYING_IDENTITY,
    PairingPhase.AUTHENTICATING, PairingPhase.ENABLING_STATUS
)
