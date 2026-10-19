package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.zone.ZonePosition
import com.example.jetsoncontroller.data.zone.ZoneRange
import com.example.jetsoncontroller.model.GnssSensorStatus
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ZoneState(val label: String) {
    CHECKING_LOCATION("위치 확인 중"), INSIDE("구역 안"), OUTSIDE("구역 밖"),
    NEAR_BOUNDARY("경계 근처"), UNKNOWN("위치 미확인")
}

enum class ZoneFixCategory { RTK_FIXED, RTK_FLOAT, STANDALONE, NONE }

enum class ZoneObservationIssue { NO_RANGE, NO_FIX, INVALID_POSITION, INVALID_SAMPLE_TIME, STALE_SAMPLE, STALE_STREAM }

data class ZoneSample(
    val latitude: Double?,
    val longitude: Double?,
    val fixCategory: ZoneFixCategory,
    val sampleTimeEpochMillis: Long?,
    val hdop: Double? = null,
    val reportedAccuracyM: Double? = null
) {
    companion object {
        /** Read from the 1 s device status, never the slower run telemetry. */
        fun fromStatus(status: GnssSensorStatus, reportedAccuracyM: Double? = null): ZoneSample = ZoneSample(
            status.latitude, status.longitude, fixCategory(status), status.lastSampleAtEpochMillis,
            status.hdop, reportedAccuracyM
        )

        private fun fixCategory(status: GnssSensorStatus): ZoneFixCategory {
            fun String.normalized() = trim().lowercase().replace('-', '_').replace(' ', '_')
            val fix = status.fixType.normalized()
            val rtk = status.rtkStatus.normalized()
            return when {
                status.fixQuality == 0 -> ZoneFixCategory.NONE
                rtk in setOf("fixed", "fix", "rtk_fixed", "rtk_fix") ||
                    fix in setOf("fixed", "fix", "rtk_fixed", "rtk_fix") || status.fixQuality == 4 -> ZoneFixCategory.RTK_FIXED
                rtk in setOf("float", "rtk_float") || fix in setOf("float", "rtk_float") ||
                    status.fixQuality == 5 -> ZoneFixCategory.RTK_FLOAT
                fix in setOf("single", "standalone", "sps", "dgps", "gps", "gnss", "2d", "3d") ||
                    status.fixQuality in setOf(1, 2, 3) -> ZoneFixCategory.STANDALONE
                else -> ZoneFixCategory.NONE
            }
        }
    }
}

data class ZoneUncertainty(val metres: Double, val estimated: Boolean) {
    val qualifier: String? get() = if (estimated) "추정" else null
}

data class ZoneMonitorState(
    val state: ZoneState = ZoneState.CHECKING_LOCATION,
    val lastSampleTimeEpochMillis: Long? = null,
    val pendingCandidate: ZoneState? = null,
    val pendingCount: Int = 0,
    val pendingSinceEpochMillis: Long? = null,
    val pendingMaxDistanceM: Double = 0.0,
    val signedDistanceM: Double? = null,
    val uncertainty: ZoneUncertainty? = null,
    val issue: ZoneObservationIssue? = null
)

data class ZoneTransition(
    val from: ZoneState,
    val to: ZoneState,
    val firstSampleTimeEpochMillis: Long,
    val confirmedAtEpochMillis: Long,
    val maxDistanceM: Double,
    val exitWarning: Boolean
) {
    val rejectHaptic: Boolean get() = exitWarning
}

data class ZoneMonitorResult(
    val monitor: ZoneMonitorState,
    val acceptedSample: Boolean = false,
    val transition: ZoneTransition? = null
) {
    fun warningVisible(runActive: Boolean): Boolean = runActive && monitor.state == ZoneState.OUTSIDE
}

object ZoneMonitor {
    const val STALE_AFTER_MILLIS = 10_000L
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun uncertainty(sample: ZoneSample): ZoneUncertainty? {
        val reported = sample.reportedAccuracyM?.takeIf { it.isFinite() && it >= 0 }
        val floor = when (sample.fixCategory) {
            ZoneFixCategory.RTK_FIXED -> 0.5
            ZoneFixCategory.RTK_FLOAT -> 2.0
            ZoneFixCategory.STANDALONE -> if (reported == null) {
                max(5.0, sample.hdop?.takeIf { it.isFinite() && it >= 0 && (it * 3).isFinite() }?.times(3) ?: 5.0)
            } else 5.0
            ZoneFixCategory.NONE -> return null
        }
        return ZoneUncertainty(max(floor, reported ?: 0.0), estimated = reported == null)
    }

    /** observedAt and sample time share the device clock; statusAgeMillis is elapsed local time. */
    fun evaluate(
        range: ZoneRange?,
        sample: ZoneSample?,
        observedAtEpochMillis: Long?,
        previous: ZoneMonitorState = ZoneMonitorState(),
        statusAgeMillis: Long = 0,
        runActive: Boolean = false
    ): ZoneMonitorResult {
        val sampleTime = sample?.sampleTimeEpochMillis
        val issue = when {
            range == null || range.polygons.isEmpty() -> ZoneObservationIssue.NO_RANGE
            statusAgeMillis > STALE_AFTER_MILLIS || statusAgeMillis < 0 -> ZoneObservationIssue.STALE_STREAM
            sample == null || sample.fixCategory == ZoneFixCategory.NONE -> ZoneObservationIssue.NO_FIX
            sampleTime == null || observedAtEpochMillis == null || sampleTime < 0 ||
                observedAtEpochMillis < sampleTime -> ZoneObservationIssue.INVALID_SAMPLE_TIME
            observedAtEpochMillis - sampleTime > STALE_AFTER_MILLIS -> ZoneObservationIssue.STALE_SAMPLE
            sample.latitude == null || sample.longitude == null || !sample.latitude.isFinite() ||
                !sample.longitude.isFinite() || sample.latitude !in -90.0..90.0 ||
                sample.longitude !in -180.0..180.0 -> ZoneObservationIssue.INVALID_POSITION
            else -> null
        }
        if (issue != null) {
            val consumed = sampleTime?.takeIf { it >= 0 && observedAtEpochMillis != null && it <= observedAtEpochMillis }
            return ZoneMonitorResult(ZoneMonitorState(
                state = ZoneState.UNKNOWN,
                lastSampleTimeEpochMillis = listOfNotNull(previous.lastSampleTimeEpochMillis, consumed).maxOrNull(),
                issue = issue
            ))
        }
        val time = requireNotNull(sampleTime)
        if (previous.lastSampleTimeEpochMillis != null && time <= previous.lastSampleTimeEpochMillis) {
            return ZoneMonitorResult(previous)
        }
        val fix = requireNotNull(sample)
        val distance = signedDistanceM(requireNotNull(range), requireNotNull(fix.latitude), requireNotNull(fix.longitude))
        val uncertainty = requireNotNull(uncertainty(fix))
        val candidate = when {
            distance <= -uncertainty.metres -> ZoneState.INSIDE
            distance >= uncertainty.metres -> ZoneState.OUTSIDE
            else -> ZoneState.NEAR_BOUNDARY
        }
        val shown = if (previous.state == ZoneState.UNKNOWN) ZoneState.CHECKING_LOCATION else previous.state
        val pending = candidate != shown
        val count = if (!pending) 0 else if (previous.pendingCandidate == candidate) previous.pendingCount + 1 else 1
        val since = if (pending && previous.pendingCandidate == candidate) previous.pendingSinceEpochMillis ?: time else time
        val maximum = if (pending && previous.pendingCandidate == candidate) {
            max(previous.pendingMaxDistanceM, max(0.0, distance))
        } else max(0.0, distance)
        val confirmed = pending && count >= 2
        return ZoneMonitorResult(
            ZoneMonitorState(
                state = if (confirmed) candidate else shown,
                lastSampleTimeEpochMillis = time,
                pendingCandidate = candidate.takeIf { pending && !confirmed },
                pendingCount = if (confirmed) 0 else count,
                pendingSinceEpochMillis = since.takeIf { pending && !confirmed },
                pendingMaxDistanceM = if (pending && !confirmed) maximum else 0.0,
                signedDistanceM = distance,
                uncertainty = uncertainty
            ),
            acceptedSample = true,
            transition = if (confirmed) ZoneTransition(
                shown, candidate, since, time, maximum,
                exitWarning = runActive && candidate == ZoneState.OUTSIDE
            ) else null
        )
    }

    /** Local equirectangular projection at the sample latitude; rings retain their holes. */
    fun signedDistanceM(range: ZoneRange, latitude: Double, longitude: Double): Double {
        val longitudeScale = Math.toRadians(1.0) * EARTH_RADIUS_M * cos(Math.toRadians(latitude))
        val latitudeScale = Math.toRadians(1.0) * EARTH_RADIUS_M
        fun project(position: ZonePosition): Pair<Double, Double> {
            val delta = ((position.longitude - longitude + 540.0) % 360.0) - 180.0
            return delta * longitudeScale to (position.latitude - latitude) * latitudeScale
        }
        var nearest = Double.POSITIVE_INFINITY
        var inside = false
        for (polygon in range.polygons) {
            val ringContainment = polygon.rings.map { ring ->
                var contained = false
                for (index in 0 until ring.lastIndex) {
                    val (ax, ay) = project(ring[index])
                    val (bx, by) = project(ring[index + 1])
                    val dx = bx - ax
                    val dy = by - ay
                    val lengthSquared = dx * dx + dy * dy
                    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
                    nearest = min(nearest, hypot(ax + t * dx, ay + t * dy))
                    if ((ay > 0) != (by > 0) && ax + (bx - ax) * (-ay) / (by - ay) > 0) contained = !contained
                }
                contained
            }
            if (ringContainment.firstOrNull() == true && ringContainment.drop(1).none { it }) inside = true
        }
        require(nearest.isFinite()) { "A validated range with closed rings is required" }
        return when {
            abs(nearest) < 0.000001 -> 0.0
            inside -> -nearest
            else -> nearest
        }
    }
}
