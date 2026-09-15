package com.example.jetsoncontroller.data.quality

import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RunQuality

enum class QualitySampleAvailability {
    NOT_RECORDED,
    NO_SAMPLES,
    UNKNOWN,
    INSUFFICIENT_TIMING,
    OBSERVED
}

data class RtkQualityMeasurement(
    val availability: QualitySampleAvailability,
    val fixRatio: Double?,
    val fixDurationMillis: Long?,
    val observedDurationMillis: Long?,
    val unknownDurationMillis: Long?
)

object FieldQualityInterpreter {
    fun rtkMeasurement(summary: RunQuality?): RtkQualityMeasurement {
        if (summary == null) {
            return RtkQualityMeasurement(
                QualitySampleAvailability.NOT_RECORDED,
                null,
                null,
                null,
                null
            )
        }
        val availability = when (summary.sampleState) {
            "OBSERVED" -> QualitySampleAvailability.OBSERVED
            "INSUFFICIENT_TIMING" -> QualitySampleAvailability.INSUFFICIENT_TIMING
            "UNKNOWN" -> QualitySampleAvailability.UNKNOWN
            else -> QualitySampleAvailability.NO_SAMPLES
        }
        val denominator = summary.rtkObservedDurationMillis?.takeIf { it > 0 }
        val ratio = summary.rtkFixRatio?.takeIf { it.isFinite() && it in 0.0..1.0 && denominator != null }
        return RtkQualityMeasurement(
            availability = availability,
            fixRatio = ratio,
            fixDurationMillis = summary.rtkFixDurationMillis?.takeIf { denominator != null && it >= 0 },
            observedDurationMillis = denominator,
            unknownDurationMillis = summary.rtkUnknownDurationMillis?.takeIf { it >= 0 }
        )
    }

    fun locatedRouteProblems(summary: RunQuality?, pointCount: Int): List<QualityProblemInterval> {
        if (summary == null || pointCount <= 0) return emptyList()
        return summary.problemIntervals.filter { interval ->
            val start = interval.startRoutePointIndex
            val end = interval.endRoutePointIndex
            start != null && end != null && start in 0 until pointCount &&
                end in start until pointCount
        }
    }
}
