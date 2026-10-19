package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.dashboard.statusFreshness

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
    WAITING, // Waiting for trustworthy telemetry; CONNECTED is an idle sensor.
    STALE,
    DISCONNECTED
}

data class SensorPresentation(
    val activity: SensorActivity,
    val badge: String,
    val description: String
)

data class SensorPresentations(
    val camera: SensorPresentation,
    val gnss: SensorPresentation,
    val imu: SensorPresentation
)

/** Sample age uses the existing CURRENT window, independently of status response age. */
fun sensorPresentations(
    status: JetsonStatus,
    deviceOnline: Boolean,
    nowEpochMillis: Long
): SensorPresentations = SensorPresentations(
    camera = sensorPresentation(
        configured = status.cameraConfigured || status.cameraSensor.configured,
        connected = status.cameraSensor.connected,
        active = status.cameraSensor.active,
        telemetryAvailable = status.sensorTelemetryAvailable,
        telemetryFresh = status.sensorTelemetryFresh,
        legacyRunning = status.cameraRunning,
        deviceOnline = deviceOnline,
        lastSampleAtEpochMillis = status.cameraSensor.lastFrameAtEpochMillis,
        nowEpochMillis = nowEpochMillis
    ),
    gnss = sensorPresentation(
        configured = status.gnssConfigured || status.gnssSensor.configured,
        connected = status.gnssSensor.connected,
        active = status.gnssSensor.active,
        telemetryAvailable = status.sensorTelemetryAvailable,
        telemetryFresh = status.sensorTelemetryFresh,
        legacyRunning = status.gnssRunning,
        deviceOnline = deviceOnline,
        lastSampleAtEpochMillis = status.gnssSensor.lastSampleAtEpochMillis,
        nowEpochMillis = nowEpochMillis
    ),
    imu = sensorPresentation(
        configured = status.imuConfigured || status.imuSensor.configured,
        connected = status.imuSensor.connected,
        active = status.imuSensor.active,
        telemetryAvailable = status.sensorTelemetryAvailable,
        telemetryFresh = status.sensorTelemetryFresh,
        legacyRunning = status.imuRunning,
        deviceOnline = deviceOnline,
        lastSampleAtEpochMillis = status.imuSensor.lastSampleAtEpochMillis,
        nowEpochMillis = nowEpochMillis
    )
)

@Suppress("UNUSED_PARAMETER") // Retained for callers of the former service-running fallback.
fun sensorPresentation(
    configured: Boolean,
    connected: Boolean,
    active: Boolean,
    telemetryAvailable: Boolean,
    telemetryFresh: Boolean,
    legacyRunning: Boolean,
    deviceOnline: Boolean = false,
    lastSampleAtEpochMillis: Long? = null,
    nowEpochMillis: Long = 0L
): SensorPresentation = when {
    !deviceOnline || !telemetryAvailable -> unknownSensorPresentation()
    !configured -> SensorPresentation(
        SensorActivity.UNCONFIGURED,
        "미설정",
        "센서 서비스가 설정되지 않았습니다"
    )
    !telemetryFresh || (active && lastSampleAtEpochMillis != null &&
        statusFreshness(lastSampleAtEpochMillis, nowEpochMillis) == StatusFreshness.STALE) -> SensorPresentation(
        SensorActivity.STALE,
        "지연",
        "센서 데이터 수신이 지연되고 있습니다"
    )
    active && lastSampleAtEpochMillis != null && lastSampleAtEpochMillis > nowEpochMillis -> unknownSensorPresentation()
    active && lastSampleAtEpochMillis == null -> unknownSensorPresentation()
    active -> SensorPresentation(
        SensorActivity.ACTIVE,
        "수신 중",
        "센서 데이터 수신 중"
    )
    connected -> SensorPresentation(
        SensorActivity.CONNECTED,
        "대기 중",
        "센서 연결됨 · 데이터 대기 중"
    )
    else -> SensorPresentation(
        SensorActivity.DISCONNECTED,
        "미연결",
        "센서 응답이 없습니다"
    )
}

private fun unknownSensorPresentation(): SensorPresentation = SensorPresentation(
    SensorActivity.WAITING,
    "미확인",
    "장치 또는 센서 데이터를 확인할 수 없습니다"
)

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
    GnssReceptionState.GNSS_OFF -> "GNSS 수신 상태 미확인"
    GnssReceptionState.RTK_OFF -> "RTK FIX 미확인"
    GnssReceptionState.RTK_FLOAT -> "RTK FLOAT"
    GnssReceptionState.RTK_FIXED -> "RTK FIX 확인"
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
        DeviceLocationAvailability.OFF -> "위치 상태 미확인"
        DeviceLocationAvailability.NO_FIX -> "위치 없음"
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

/** Existing long-poll deadline is 2s; allow two deadlines for a received preview frame. */
fun cameraFrameIsLive(active: Boolean, hasFrame: Boolean, receivedAt: Long?, now: Long, hasError: Boolean): Boolean =
    active && hasFrame && !hasError && receivedAt != null && now >= receivedAt && now - receivedAt <= 4_000L
