package com.example.jetsoncontroller.ui.zone

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStripState
import com.example.jetsoncontroller.ui.components.geo.GeoStripKind
import com.example.jetsoncontroller.ui.field.ResultZoneObservation
import com.example.jetsoncontroller.ui.field.RunZonePresentation
import com.example.jetsoncontroller.ui.map.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.navigation.AppNavigationState
import com.example.jetsoncontroller.ui.theme.GeoMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

private val LocalZoneEvents = staticCompositionLocalOf<PhoneObservedZoneEvents?> { null }
private val LocalZoneVersion = compositionLocalOf { 0L }

internal fun ZoneRunEvents.mapOutput(now: Long): GeoMapZoneSnapshot = GeoMapZoneSnapshot(
    state = when (monitor.state) {
        ZoneState.INSIDE -> GeoZoneState.INSIDE
        ZoneState.OUTSIDE -> GeoZoneState.OUTSIDE
        ZoneState.NEAR_BOUNDARY -> GeoZoneState.NEAR
        ZoneState.CHECKING_LOCATION -> GeoZoneState.CHECKING
        ZoneState.UNKNOWN -> GeoZoneState.UNKNOWN
    },
    distanceM = monitor.signedDistanceM?.coerceAtLeast(0.0),
    gaps = gaps.map { GeoMapGap(it.startedAtEpochMillis, it.endedAtEpochMillis) },
    outsideCount = excursions.size, outsideMillis = outsideDurationMillis(now),
    unobservedMillis = gapDurationMillis(now), phoneObserved = scope == ZoneObservationScope.PHONE_FOREGROUND
)

@Composable
internal fun rememberRunZoneOutput(deviceId: String?, runId: String?): GeoMapZoneSnapshot? {
    val source = LocalZoneEvents.current
    val version = LocalZoneVersion.current
    return remember(source, version, deviceId, runId) {
        if (deviceId.isNullOrBlank() || runId.isNullOrBlank()) null else source?.read(ZoneRunKey(deviceId.lowercase(), runId))
            ?.let { it.mapOutput(it.endedAtEpochMillis ?: source.observationTime(it.binding.key)) }
    }
}

internal fun GeoMapZoneOutput.resultObservation(): ResultZoneObservation? = outsideCount?.let {
    ResultZoneObservation(it, outsideMillis ?: 0L, unobservedMillis ?: 0L)
}

internal fun GeoMapZoneOutput.runPresentation(deviceName: String): RunZonePresentation = RunZonePresentation(
    badge = when (state) {
        GeoZoneState.INSIDE -> "구역 안"
        GeoZoneState.OUTSIDE -> "구역 밖"
        GeoZoneState.NEAR -> "경계 근처"
        GeoZoneState.CHECKING -> "위치 확인 중"
        GeoZoneState.UNKNOWN -> "위치 미확인"
    },
    tone = when (state) {
        GeoZoneState.INSIDE -> Tone.SUCCESS
        GeoZoneState.OUTSIDE -> Tone.WARNING
        GeoZoneState.NEAR -> Tone.INFO
        else -> Tone.UNKNOWN
    },
    strip = if (state == GeoZoneState.OUTSIDE) GeoStatusStripState(GeoStripKind.OUTSIDE_ZONE, deviceName,
        "수집 구역을 벗어났습니다" + (distanceM?.let { " · ${Formats.distance(it)}" } ?: "")) else null,
    readiness = "수집 중 · " + when (state) {
        GeoZoneState.INSIDE -> "구역 안"
        GeoZoneState.OUTSIDE -> "구역 밖"
        GeoZoneState.NEAR -> "경계 근처"
        GeoZoneState.CHECKING -> "위치 확인 중"
        GeoZoneState.UNKNOWN -> "위치 미확인"
    }, outsideCount = outsideCount
)

/** One foreground observer survives navigation between the run, camera and full map. */
@Composable
internal fun ZoneObservationHost(state: AppNavigationState, content: @Composable () -> Unit) {
    val source = remember(state.repository) { PhoneObservedZoneEvents(File(state.fieldContext.noBackupFilesDir, "zone-events.json")) }
    val maps = remember(state.repository) { GeoMapRunStore(state.fieldContext.noBackupFilesDir) }
    val latest by rememberUpdatedState(state)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    var version by remember { mutableLongStateOf(0) }
    var outputKey by remember { mutableStateOf<ZoneRunKey?>(null) }
    var output by remember { mutableStateOf<GeoMapZoneOutput>(GeoMapZoneSnapshot()) }
    LaunchedEffect(source, lifecycle) {
        var key: ZoneRunKey? = null
        var deviceAt = 0L
        var receivedAt = SystemClock.elapsedRealtime()
        var lastStatus: JetsonStatus? = null
        fun now() = deviceAt + (SystemClock.elapsedRealtime() - receivedAt).coerceAtLeast(0)
        suspend fun pause() {
            key?.let { target -> withContext(Dispatchers.IO) {
                source.read(target)?.takeIf { it.endedAtEpochMillis == null }?.let { source.pause(target, maxOf(now(), source.observationTime(target))) }
            } }
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                coroutineScope {
                    val changes = Channel<Unit>(Channel.CONFLATED)
                    launch { latest.repository.status.collect { changes.send(Unit) } }
                    launch { while (true) { changes.send(Unit); delay(1_000) } }
                    for (ignored in changes) {
                        val app = latest
                        app.runResultState.run?.takeIf { !it.active }?.let { ended ->
                            val endedKey = ZoneRunKey(ended.deviceId.lowercase(), ended.runId)
                            val endedAt = ended.finishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                            if (endedAt != null) withContext(Dispatchers.IO) {
                                source.read(endedKey)?.takeIf { it.endedAtEpochMillis == null }?.let {
                                    source.endRun(endedKey, maxOf(endedAt, source.observationTime(endedKey)))
                                }
                            }
                        }
                        val run = app.surveyRunState.activeRun ?: app.surveyRunState.latestRun
                        val selectedRun = run?.takeIf { it.deviceId.equals(app.selectedDeviceId, true) }
                        val nextKey = selectedRun?.takeIf { it.active }?.let { ZoneRunKey(it.deviceId.lowercase(), it.runId) }
                        val terminal = run?.takeIf { !it.active && key?.runId == it.runId && key?.deviceId.equals(it.deviceId, true) }
                        if (nextKey != key) {
                            if (terminal != null) key?.let { old -> withContext(Dispatchers.IO) {
                                source.read(old)?.takeIf { it.endedAtEpochMillis == null }?.let { source.endRun(old, maxOf(terminal.finishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now(), source.observationTime(old))) }
                            } } else pause()
                            key = nextKey
                            lastStatus = null
                            deviceAt = selectedRun?.startedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
                            receivedAt = SystemClock.elapsedRealtime()
                        }
                        val target = key
                        if (target == null || selectedRun == null) {
                            output = GeoMapZoneSnapshot()
                            version++
                            continue
                        }
                        val status = app.repository.status.value
                        val connected = app.repository.transportState.value as? TransportState.Connected
                        val matches = connected?.deviceId.equals(target.deviceId, true) &&
                            app.repository.selectedDeviceId.value.equals(target.deviceId, true)
                        val newResponse = matches && status != lastStatus && status.collectedAtEpochMillis != null
                        if (newResponse) {
                            deviceAt = maxOf(now(), status.collectedAtEpochMillis!!)
                            receivedAt = SystemClock.elapsedRealtime()
                            lastStatus = status
                        }
                        val time = now()
                        val accepted = app.surveyRunState.acceptedStarts[target.runId]
                        val candidates = app.serverTicketState.tickets + listOfNotNull(app.ticketPreparation?.ticket)
                        val result = withContext(Dispatchers.IO) {
                            if (source.read(target) == null && time > 0) {
                                val geometry = maps.read(target.deviceId, target.runId) ?: accepted?.let {
                                    maps.bindAccepted(target.deviceId, target.runId, it, candidates)
                                }
                                val contract = geometry?.contractJson?.let { com.example.jetsoncontroller.data.zone.ZoneValidation.parse(it).contract }
                                if (contract?.range != null) {
                                    val start = runCatching { Instant.parse(selectedRun.startedAt).toEpochMilli() }.getOrNull() ?: time
                                    source.startRun(target, contract, minOf(start, time).coerceAtLeast(0))
                                }
                            }
                            val events = source.read(target)
                            if (events == null || events.endedAtEpochMillis != null) null else {
                                val at = maxOf(time, source.observationTime(target))
                                val age = SystemClock.elapsedRealtime() - receivedAt
                                if (newResponse) source.observe(target, ZoneSample.fromStatus(status.gnssSensor), at)
                                else { source.streamStale(target, at, age); null }
                            }
                        }
                        if (result?.transition?.rejectHaptic == true) view.performHapticFeedback(GeoMotion.zoneExitHaptic)
                        outputKey = target
                        output = source.read(target)?.mapOutput(maxOf(time, source.observationTime(target))) ?: GeoMapZoneSnapshot()
                        version++
                    }
                }
            } finally {
                withContext(NonCancellable) { pause() }
            }
        }
    }
    val run = state.surveyRunState.activeRun
    val current = output.takeIf { run != null && outputKey == ZoneRunKey(run.deviceId.lowercase(), run.runId) }
        ?: GeoMapZoneSnapshot()
    CompositionLocalProvider(LocalZoneEvents provides source, LocalZoneVersion provides version,
        LocalGeoMapZoneOutput provides current, content = content)
}
