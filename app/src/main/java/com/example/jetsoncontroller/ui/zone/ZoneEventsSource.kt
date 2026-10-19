package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.zone.ZoneContract
import com.example.jetsoncontroller.data.zone.ZoneValidation

data class ZoneRunKey(val deviceId: String, val runId: String)

data class ZoneRunBinding(
    val key: ZoneRunKey,
    val geometryRevision: String,
    val geometryHash: String,
    val geometryJson: String,
    val startedAtEpochMillis: Long
) {
    fun contract(): ZoneContract = requireNotNull(ZoneValidation.parse(geometryJson).contract)
        .copy(hash = geometryHash)
}

enum class ZoneObservationScope { PHONE_FOREGROUND, DEVICE }

enum class ZoneExcursionEnd { RETURNED, OBSERVATION_GAP, RUN_ENDED }

data class ZoneExcursion(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val maxDistanceM: Double,
    val endReason: ZoneExcursionEnd? = null
)

enum class ZoneGapReason { AWAITING_SAMPLE, BACKGROUND, STALE_SAMPLE, STALE_STREAM, NO_FIX, INVALID_SAMPLE, PROCESS_RESTART }

data class ZoneObservationGap(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val reason: ZoneGapReason
)

data class ZoneRunEvents(
    val binding: ZoneRunBinding,
    val scope: ZoneObservationScope,
    val monitor: ZoneMonitorState,
    val excursions: List<ZoneExcursion> = emptyList(),
    val gaps: List<ZoneObservationGap> = emptyList(),
    val endedAtEpochMillis: Long? = null
) {
    val observationQualifier: String? get() = if (scope == ZoneObservationScope.PHONE_FOREGROUND) "관찰한 시간 기준" else null

    fun outsideDurationMillis(asOfEpochMillis: Long): Long = excursions.sumOf {
        ((it.endedAtEpochMillis ?: minOf(asOfEpochMillis, endedAtEpochMillis ?: asOfEpochMillis)) - it.startedAtEpochMillis)
            .coerceAtLeast(0)
    }

    fun gapDurationMillis(asOfEpochMillis: Long): Long = gaps.sumOf {
        ((it.endedAtEpochMillis ?: minOf(asOfEpochMillis, endedAtEpochMillis ?: asOfEpochMillis)) - it.startedAtEpochMillis)
            .coerceAtLeast(0)
    }
}

/** UI seam: Phase 2's DeviceZoneEvents supplies the same run-bound records with DEVICE scope. */
interface ZoneEventsSource {
    fun read(key: ZoneRunKey): ZoneRunEvents?
}
