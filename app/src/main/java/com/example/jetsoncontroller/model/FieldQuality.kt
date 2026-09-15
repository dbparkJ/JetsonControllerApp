package com.example.jetsoncontroller.model

data class RunQuality(
    val schemaVersion: Int = 1,
    val sampleState: String = "NO_SAMPLES",
    val firstObservedAtEpochMillis: Long? = null,
    val lastObservedAtEpochMillis: Long? = null,
    val elapsedDurationMillis: Long? = null,
    val observedDurationMillis: Long? = null,
    val unknownDurationMillis: Long? = null,
    val rtkObservedDurationMillis: Long? = null,
    val rtkUnknownDurationMillis: Long? = null,
    val rtkFixDurationMillis: Long? = null,
    val rtkFixRatio: Double? = null,
    val observationCount: Int = 0,
    val truncated: Boolean = false,
    val locationPrecision: List<LocationPrecisionDuration> = emptyList(),
    val problemIntervals: List<QualityProblemInterval> = emptyList(),
    val sensors: List<SensorQualitySummary> = emptyList()
)

data class LocationPrecisionDuration(
    val fixState: String,
    val durationMillis: Long,
    val ratio: Double
)

data class QualityProblemInterval(
    val kind: String,
    val sensor: String? = null,
    val requirement: String = "UNSPECIFIED",
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val durationMillis: Long? = null,
    val startRoutePointIndex: Int? = null,
    val endRoutePointIndex: Int? = null
)

data class SensorQualitySummary(
    val sensor: String,
    val requirement: String = "UNSPECIFIED",
    val sampleState: String = "NO_SAMPLES",
    val observedDurationMillis: Long? = null,
    val activeDurationMillis: Long? = null,
    val staleDurationMillis: Long? = null,
    val lostDurationMillis: Long? = null,
    val unknownDurationMillis: Long? = null,
    val notConfiguredDurationMillis: Long? = null,
    val problemCount: Int = 0
)
