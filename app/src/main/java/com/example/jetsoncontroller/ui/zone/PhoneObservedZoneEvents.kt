package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.zone.ZoneContract
import com.example.jetsoncontroller.data.zone.ZoneValidation
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max

private data class StoredZoneRun(
    val events: ZoneRunEvents,
    val lastObservedAtEpochMillis: Long,
    val lastAcceptedSampleTimeEpochMillis: Long? = null
)

private data class StoredZoneEvents(val schemaVersion: Int = 1, val runs: List<StoredZoneRun> = emptyList())

/**
 * Called by the foreground (lifecycle STARTED) status consumer. All event/lifecycle timestamps use
 * the device clock; a lifecycle/timer timestamp is last device observedAt + locally elapsed time.
 * This source does not poll, send notifications or control a run.
 */
class PhoneObservedZoneEvents(private val file: File, private val gson: Gson = Gson()) : ZoneEventsSource {
    private var runs: List<StoredZoneRun> = restore()

    init {
        if (runs.isNotEmpty()) persist(runs)
    }

    @Synchronized
    override fun read(key: ZoneRunKey): ZoneRunEvents? = runs.firstOrNull { it.events.binding.key == key }?.events
        ?.let { it.copy(excursions = it.excursions.toList(), gaps = it.gaps.toList()) }

    @Synchronized
    fun observationTime(key: ZoneRunKey): Long = runs.firstOrNull { it.events.binding.key == key }?.lastObservedAtEpochMillis ?: 0L

    /** Rebinding an existing run always returns its frozen start geometry. */
    @Synchronized
    fun startRun(key: ZoneRunKey, contract: ZoneContract, startedAtEpochMillis: Long): ZoneRunBinding {
        require(key.deviceId.isNotBlank() && key.runId.isNotBlank() && startedAtEpochMillis >= 0)
        read(key)?.let { return it.binding }
        val frozen = requireNotNull(ZoneValidation.parse(contract.canonicalJson).contract)
        val binding = ZoneRunBinding(key, frozen.revision, contract.hash, frozen.canonicalJson, startedAtEpochMillis)
        val events = ZoneRunEvents(binding, ZoneObservationScope.PHONE_FOREGROUND, ZoneMonitorState(),
            gaps = listOf(ZoneObservationGap(startedAtEpochMillis, reason = ZoneGapReason.AWAITING_SAMPLE)))
        replace(StoredZoneRun(events, startedAtEpochMillis))
        return binding
    }

    @Synchronized
    fun observe(
        key: ZoneRunKey,
        sample: ZoneSample?,
        observedAtEpochMillis: Long,
        statusAgeMillis: Long = 0
    ): ZoneMonitorResult {
        val stored = active(key)
        require(observedAtEpochMillis >= stored.lastObservedAtEpochMillis) { "Device observation time must not move backwards" }
        val result = ZoneMonitor.evaluate(stored.events.binding.contract().range, sample, observedAtEpochMillis,
            stored.events.monitor, statusAgeMillis, runActive = true)
        val reason = when (result.monitor.issue) {
            ZoneObservationIssue.STALE_SAMPLE -> ZoneGapReason.STALE_SAMPLE
            ZoneObservationIssue.STALE_STREAM -> ZoneGapReason.STALE_STREAM
            ZoneObservationIssue.NO_FIX -> ZoneGapReason.NO_FIX
            ZoneObservationIssue.INVALID_POSITION, ZoneObservationIssue.INVALID_SAMPLE_TIME -> ZoneGapReason.INVALID_SAMPLE
            else -> null
        }
        if (reason != null) {
            val start = when (reason) {
                ZoneGapReason.STALE_STREAM -> observedAtEpochMillis - statusAgeMillis.coerceAtLeast(0) + ZoneMonitor.STALE_AFTER_MILLIS
                ZoneGapReason.STALE_SAMPLE -> (sample?.sampleTimeEpochMillis ?: observedAtEpochMillis) + ZoneMonitor.STALE_AFTER_MILLIS
                else -> observedAtEpochMillis
            }.coerceIn(stored.lastObservedAtEpochMillis, observedAtEpochMillis)
            val interrupted = openGap(stored, start, reason)
            replace(interrupted.copy(
                events = interrupted.events.copy(monitor = result.monitor),
                lastObservedAtEpochMillis = observedAtEpochMillis
            ))
            return result
        }
        var events = stored.events.copy(monitor = result.monitor)
        var acceptedAt = stored.lastAcceptedSampleTimeEpochMillis
        if (result.acceptedSample) {
            val sampleTime = requireNotNull(sample?.sampleTimeEpochMillis).coerceAtLeast(events.binding.startedAtEpochMillis)
            acceptedAt = sampleTime
            events = closeGap(events, sampleTime)
            val transition = result.transition
            if (transition?.to == ZoneState.OUTSIDE) {
                events = events.copy(excursions = events.excursions + ZoneExcursion(
                    transition.firstSampleTimeEpochMillis.coerceAtLeast(events.binding.startedAtEpochMillis),
                    maxDistanceM = transition.maxDistanceM
                ))
            } else if (transition != null) {
                events = closeExcursion(events, transition.firstSampleTimeEpochMillis, ZoneExcursionEnd.RETURNED)
            }
            val distance = result.monitor.signedDistanceM ?: 0.0
            events = events.copy(excursions = events.excursions.map {
                if (it.endedAtEpochMillis == null) it.copy(maxDistanceM = max(it.maxDistanceM, distance)) else it
            })
        }
        replace(StoredZoneRun(events, observedAtEpochMillis, acceptedAt))
        return result
    }

    /** Call at lifecycle STOPPED; no samples are consumed until the next foreground observation. */
    @Synchronized
    fun pause(key: ZoneRunKey, atEpochMillis: Long) {
        val stored = active(key)
        require(atEpochMillis >= stored.lastObservedAtEpochMillis)
        replace(openGap(stored, atEpochMillis, ZoneGapReason.BACKGROUND).copy(lastObservedAtEpochMillis = atEpochMillis))
    }

    /** Called by a foreground timer even if no status response arrives. */
    @Synchronized
    fun streamStale(key: ZoneRunKey, deviceNowEpochMillis: Long, statusAgeMillis: Long) {
        if (statusAgeMillis <= ZoneMonitor.STALE_AFTER_MILLIS) return
        observe(key, null, deviceNowEpochMillis, statusAgeMillis)
    }

    @Synchronized
    fun endRun(key: ZoneRunKey, endedAtEpochMillis: Long) {
        val stored = active(key)
        require(endedAtEpochMillis >= stored.lastObservedAtEpochMillis)
        val events = closeGap(closeExcursion(stored.events,
            stored.lastAcceptedSampleTimeEpochMillis ?: endedAtEpochMillis, ZoneExcursionEnd.RUN_ENDED), endedAtEpochMillis)
            .copy(endedAtEpochMillis = endedAtEpochMillis)
        replace(stored.copy(events = events, lastObservedAtEpochMillis = endedAtEpochMillis))
    }

    private fun active(key: ZoneRunKey): StoredZoneRun = requireNotNull(runs.firstOrNull { it.events.binding.key == key }) {
        "The run must be bound before observation"
    }.also { require(it.events.endedAtEpochMillis == null) { "The run has ended" } }

    private fun openGap(stored: StoredZoneRun, at: Long, reason: ZoneGapReason): StoredZoneRun {
        val events = closeExcursion(stored.events, stored.lastAcceptedSampleTimeEpochMillis ?: at,
            ZoneExcursionEnd.OBSERVATION_GAP)
        val gaps = if (events.gaps.lastOrNull()?.endedAtEpochMillis == null && events.gaps.isNotEmpty()) events.gaps
            else events.gaps + ZoneObservationGap(at.coerceAtLeast(events.binding.startedAtEpochMillis), reason = reason)
        return stored.copy(events = events.copy(gaps = gaps, monitor = ZoneMonitorState(
            ZoneState.UNKNOWN, lastSampleTimeEpochMillis = events.monitor.lastSampleTimeEpochMillis
        )))
    }

    private fun closeGap(events: ZoneRunEvents, at: Long): ZoneRunEvents = events.copy(gaps = events.gaps.map {
        if (it.endedAtEpochMillis == null) it.copy(endedAtEpochMillis = max(at, it.startedAtEpochMillis)) else it
    })

    private fun closeExcursion(events: ZoneRunEvents, at: Long, reason: ZoneExcursionEnd): ZoneRunEvents =
        events.copy(excursions = events.excursions.map {
            if (it.endedAtEpochMillis == null) it.copy(endedAtEpochMillis = max(at, it.startedAtEpochMillis), endReason = reason) else it
        })

    private fun replace(stored: StoredZoneRun) {
        val next = runs.filterNot { it.events.binding.key == stored.events.binding.key } + stored
        persist(next)
        runs = next
    }

    private fun restore(): List<StoredZoneRun> {
        if (!file.isFile || file.length() == 0L) return emptyList()
        val data = requireNotNull(gson.fromJson(file.readText(Charsets.UTF_8), StoredZoneEvents::class.java))
        require(data.schemaVersion == 1) { "Unsupported zone events schema" }
        return data.runs.map { stored ->
            if (stored.events.endedAtEpochMillis == null) openGap(stored, stored.lastObservedAtEpochMillis, ZoneGapReason.PROCESS_RESTART)
            else stored
        }
    }

    private fun persist(next: List<StoredZoneRun>) {
        val target = file.absoluteFile
        target.parentFile?.mkdirs()
        check(target.parentFile?.isDirectory == true) { "Zone event storage is unavailable" }
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(gson.toJson(StoredZoneEvents(runs = next)).toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
