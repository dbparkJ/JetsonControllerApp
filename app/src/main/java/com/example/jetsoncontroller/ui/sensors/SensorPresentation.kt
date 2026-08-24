package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus

enum class GnssReceptionState {
    GNSS_OFF,
    RTK_OFF,
    RTK_FLOAT,
    RTK_FIXED
}

enum class DeviceLocationAvailability {
    OFFLINE,
    STALE,
    OFF,
    NO_FIX,
    ACTIVE
}

enum class MobileLocationAvailability {
    PERMISSION_REQUIRED,
    PROVIDER_DISABLED,
    NO_FIX,
    STALE,
    OFF,
    ACTIVE
}

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

fun gnssReceptionState(
    gnssAvailable: Boolean,
    fixType: String,
    rtkStatus: String = "unknown"
): GnssReceptionState {
    if (!gnssAvailable) return GnssReceptionState.GNSS_OFF

    val normalizedFix = fixType.normalizedGnssStatus()
    val normalizedRtk = rtkStatus.normalizedGnssStatus()
    return when {
        normalizedRtk.isRtkFixed() -> GnssReceptionState.RTK_FIXED
        normalizedRtk.isRtkFloat() -> GnssReceptionState.RTK_FLOAT
        normalizedRtk.isExplicitRtkOff() -> GnssReceptionState.RTK_OFF
        normalizedFix.isRtkFixed() -> GnssReceptionState.RTK_FIXED
        normalizedFix.isRtkFloat() -> GnssReceptionState.RTK_FLOAT
        else -> GnssReceptionState.RTK_OFF
    }
}

fun gnssReceptionLabel(
    gnssAvailable: Boolean,
    fixType: String,
    rtkStatus: String = "unknown"
): String = when (gnssReceptionState(gnssAvailable, fixType, rtkStatus)) {
    GnssReceptionState.GNSS_OFF -> "GNSS 장치가 꺼져있습니다"
    GnssReceptionState.RTK_OFF -> "RTK가 꺼져있습니다"
    GnssReceptionState.RTK_FLOAT -> "RTK 신호가 약합니다"
    GnssReceptionState.RTK_FIXED -> "RTK 수신중"
}

fun effectiveGnssAvailability(
    deviceOnline: Boolean,
    telemetryAvailable: Boolean,
    telemetryFresh: Boolean,
    sensorConnected: Boolean,
    sensorActive: Boolean,
    legacyRunning: Boolean
): Boolean = deviceOnline && if (telemetryAvailable) {
    telemetryFresh && (sensorConnected || sensorActive)
} else {
    legacyRunning
}

fun deviceLocationAvailability(
    deviceOnline: Boolean,
    telemetryFresh: Boolean,
    gnssAvailable: Boolean,
    hasValidLocation: Boolean
): DeviceLocationAvailability = when {
    !deviceOnline -> DeviceLocationAvailability.OFFLINE
    !telemetryFresh -> DeviceLocationAvailability.STALE
    !gnssAvailable -> DeviceLocationAvailability.OFF
    !hasValidLocation -> DeviceLocationAvailability.NO_FIX
    else -> DeviceLocationAvailability.ACTIVE
}

fun deviceLocationAvailabilityLabel(availability: DeviceLocationAvailability): String =
    when (availability) {
        DeviceLocationAvailability.OFFLINE -> "장치가 오프라인입니다"
        DeviceLocationAvailability.STALE -> "장치 위치 데이터가 지연되고 있습니다"
        DeviceLocationAvailability.OFF -> "GNSS 장치가 꺼져있습니다"
        DeviceLocationAvailability.NO_FIX -> "장치 위치 수신 대기 중"
        DeviceLocationAvailability.ACTIVE -> "장치 위치 수신 중"
    }

fun mobileLocationAvailability(
    state: MobileLocationUiState,
    staleAfterMillis: Long = MOBILE_LOCATION_STALE_AFTER_MS
): MobileLocationAvailability = when {
    !state.trackingEnabled -> MobileLocationAvailability.OFF
    !state.permissionGranted -> MobileLocationAvailability.PERMISSION_REQUIRED
    !state.providerAvailable -> MobileLocationAvailability.OFF
    !state.providerEnabled -> MobileLocationAvailability.PROVIDER_DISABLED
    !state.trackerOperational -> MobileLocationAvailability.OFF
    state.fix == null || !state.fix.hasValidCoordinates() -> MobileLocationAvailability.NO_FIX
    state.nowElapsedRealtimeNanos < state.fix.elapsedRealtimeNanos ->
        MobileLocationAvailability.STALE
    state.nowElapsedRealtimeNanos - state.fix.elapsedRealtimeNanos >
        staleAfterMillis * NANOS_PER_MILLISECOND ->
        MobileLocationAvailability.STALE
    else -> MobileLocationAvailability.ACTIVE
}

fun mobileLocationAvailabilityLabel(availability: MobileLocationAvailability): String =
    when (availability) {
        MobileLocationAvailability.PERMISSION_REQUIRED -> "모바일 위치 권한이 필요합니다"
        MobileLocationAvailability.PROVIDER_DISABLED -> "모바일 위치 서비스가 꺼져있습니다"
        MobileLocationAvailability.NO_FIX -> "모바일 위치를 수신하지 못했습니다"
        MobileLocationAvailability.STALE -> "모바일 위치 데이터가 지연되고 있습니다"
        MobileLocationAvailability.OFF -> "모바일 위치 추적이 꺼져있습니다"
        MobileLocationAvailability.ACTIVE -> "모바일 위치 수신 중"
    }

fun GnssSensorStatus.hasValidLocation(): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun String.normalizedGnssStatus(): String =
    trim().lowercase().replace('-', '_').replace(' ', '_')

private fun String.isRtkFloat(): Boolean =
    this == "rtk_float" || this == "float" || endsWith("_float")

private fun String.isRtkFixed(): Boolean =
    this == "rtk_fixed" || this == "rtk_fix" || this == "fixed" || this == "fix" ||
        endsWith("_fixed")

private fun String.isExplicitRtkOff(): Boolean =
    this in setOf("off", "disabled", "invalid", "not_rtk", "none")

internal const val MOBILE_LOCATION_STALE_AFTER_MS = 5_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
