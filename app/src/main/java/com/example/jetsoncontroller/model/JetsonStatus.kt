package com.example.jetsoncontroller.model

data class JetsonStatus(
    val cpuPercent: Int = 0,
    val gpuPercent: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val temperatureC: Float = 0f,
    val storagePercent: Int = 0,
    val storageUsedBytes: Long = 0,
    val storageTotalBytes: Long = 0,
    val storageAvailableBytes: Long = 0,
    val cameraRunning: Boolean = false,
    val lidarRunning: Boolean = false,
    val gnssRunning: Boolean = false,
    val imuRunning: Boolean = false,
    val mmsRunning: Boolean = false,
    val cameraConfigured: Boolean = false,
    val gnssConfigured: Boolean = false,
    val imuConfigured: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    val sensorTelemetryAvailable: Boolean = false,
    val sensorTelemetryFresh: Boolean = false,
    val sensorTelemetryUpdatedAtEpochMillis: Long? = null,
    val sensorTelemetryAgeSeconds: Float? = null,
    val pipelineSensorBridgeActive: Boolean = false,
    val pipelineSensorBridgeError: String? = null,
    val cameraSensor: CameraSensorStatus = CameraSensorStatus(),
    val gnssSensor: GnssSensorStatus = GnssSensorStatus(),
    val imuSensor: ImuSensorStatus = ImuSensorStatus()
)

data class CameraSensorStatus(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val active: Boolean = false,
    val lastFrameAtEpochMillis: Long? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val previewAvailable: Boolean = false,
    val previewUpdatedAtEpochMillis: Long? = null,
    val previewError: String? = null
)

data class GnssSensorStatus(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val active: Boolean = false,
    val lastSampleAtEpochMillis: Long? = null,
    val fixQuality: Int? = null,
    val fixType: String = "none",
    val fixName: String = "unknown",
    val rtkStatus: String = "unknown",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val satellites: Int? = null,
    val hdop: Double? = null,
    val differentialAgeS: Double? = null,
    val referenceStationId: String? = null,
    val ntripConnected: Boolean = false,
    val ntripMountpoint: String? = null,
    val rtcmBytes: Long = 0,
    val error: String? = null
)

data class ImuSensorStatus(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val active: Boolean = false,
    val lastSampleAtEpochMillis: Long? = null,
    val source: String? = null,
    val error: String? = null
)
