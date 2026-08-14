package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus

enum class SensorActivity {
    UNCONFIGURED,
    ACTIVE,
    CONNECTED,
    WAITING,
    STALE,
    DISCONNECTED
}

data class SensorPresentation(
    val activity: SensorActivity,
    val badge: String,
    val description: String
)

fun sensorPresentation(
    configured: Boolean,
    connected: Boolean,
    active: Boolean,
    telemetryAvailable: Boolean,
    telemetryFresh: Boolean,
    legacyRunning: Boolean
): SensorPresentation = when {
    !configured -> SensorPresentation(
        SensorActivity.UNCONFIGURED,
        "미설정",
        "센서 서비스가 설정되지 않았습니다"
    )
    telemetryAvailable && !telemetryFresh -> SensorPresentation(
        SensorActivity.STALE,
        "지연",
        "센서 상태 응답이 지연되고 있습니다"
    )
    telemetryAvailable && active -> SensorPresentation(
        SensorActivity.ACTIVE,
        "활성",
        "센서 데이터 수신 중"
    )
    telemetryAvailable && connected -> SensorPresentation(
        SensorActivity.CONNECTED,
        "연결",
        "센서 연결됨 · 데이터 대기 중"
    )
    telemetryAvailable -> SensorPresentation(
        SensorActivity.DISCONNECTED,
        "미연결",
        "센서 응답이 없습니다"
    )
    legacyRunning -> SensorPresentation(
        SensorActivity.ACTIVE,
        "실행",
        "센서 서비스 실행 중"
    )
    else -> SensorPresentation(
        SensorActivity.WAITING,
        "대기",
        "센서 서비스 대기 또는 중지"
    )
}

fun gnssFixLabel(fixType: String): String = when (fixType.lowercase()) {
    "gps" -> "GPS"
    "dgps" -> "DGPS"
    "pps" -> "PPS"
    "rtk_fixed" -> "RTK Fix"
    "rtk_float" -> "RTK Float"
    "estimated" -> "추정 위치"
    "manual" -> "수동 위치"
    "simulation" -> "시뮬레이션"
    "none" -> "미고정"
    else -> "상태 미확인"
}

fun GnssSensorStatus.hasValidLocation(): Boolean =
    latitude != null && longitude != null &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
