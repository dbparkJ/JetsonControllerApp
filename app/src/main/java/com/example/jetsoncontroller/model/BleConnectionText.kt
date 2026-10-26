package com.example.jetsoncontroller.model

internal const val BLE_CONNECTION_FAILED_TEXT =
    "장치를 찾았지만 연결하지 못했습니다. 장치 가까이에서 다시 시도하세요."

internal fun bleConnectionFailureText(message: String): String = when {
    message.contains("권한") -> "블루투스 연결 권한이 필요합니다."
    message.contains("인증") || message.contains("일치") ||
        message.contains("Authentication", ignoreCase = true) ->
        "장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요."
    else -> BLE_CONNECTION_FAILED_TEXT
}

internal fun bleConnectionProgressText(attempt: Int, maxAttempts: Int = 3): String =
    if (attempt > 1) "다시 연결하는 중 ($attempt/$maxAttempts)" else "장치에 연결하는 중입니다."
