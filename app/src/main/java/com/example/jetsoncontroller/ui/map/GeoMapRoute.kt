package com.example.jetsoncontroller.ui.map

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.StatusPollingLifecycleEffect
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.*
import com.example.jetsoncontroller.ui.matchingStopTarget
import com.example.jetsoncontroller.ui.navigateSingle
import com.example.jetsoncontroller.ui.navigation.AppNavigationState
import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.runResultRoute
import com.example.jetsoncontroller.ui.theme.GeoMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberRunGeometry(state: AppNavigationState, run: PipelineRun?): GeoMapGeometry? {
    val root = LocalContext.current.noBackupFilesDir
    val preparation = state.ticketPreparation
    val accepted = run?.runId?.let { state.surveyRunState.acceptedStarts[it] }
    val candidates = state.serverTicketState.tickets + listOfNotNull(preparation?.ticket)
    val geometry by produceState<GeoMapGeometry?>(null, run?.deviceId, run?.runId, accepted, candidates) {
        value = withContext(Dispatchers.IO) {
            val store = GeoMapRunStore(root)
            if (run == null) null else store.read(run.deviceId, run.runId) ?: accepted?.takeIf {
                it.pipelineId == run.pipelineId && it.surveySectionId == run.contextSnapshot.surveySectionId && it.surveyProjectId == run.contextSnapshot.surveyProjectId
            }?.let { store.bindAccepted(run.deviceId, run.runId, it, candidates) }
        }
    }
    return geometry
}

private fun historyRun(run: PipelineRun): TaskRun = TaskRun(id = "map:${run.runId}", pipelineId = run.pipelineId,
    label = run.contextSnapshot.surveySectionLabel, logId = run.logId, startedAt = run.startedAt,
    finishedAt = run.finishedAt, state = run.state, runId = run.runId, deviceId = run.deviceId,
    contextSnapshot = run.contextSnapshot, quality = run.quality, active = run.active)

/** Poll only while this entry is resumed, so a hidden card cannot cancel the full map's polling. */
@Composable
private fun MapRoutePolling(state: AppNavigationState, run: TaskRun?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(run)
    DisposableEffect(lifecycle, state.fieldToolsViewModel, run?.id) {
        var owned = false
        fun sync() {
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !owned) { state.fieldToolsViewModel.selectRun(latest); owned = true }
            if (!resumed && owned) { state.fieldToolsViewModel.stopRoutePolling(); owned = false }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer); sync()
        onDispose {
            lifecycle.removeObserver(observer)
            // A newer map entry may already own the polling job by the time this entry disposes.
            if (owned) state.fieldToolsViewModel.stopRoutePolling()
        }
    }
}

@Composable
internal fun GeoActiveRunMapCard(state: AppNavigationState) {
    val active = (state.surveyRunState.activeRun ?: state.surveyRunState.latestRun)?.takeIf {
        it.deviceId.equals(state.selectedDeviceId, true)
    }
    val geometry = rememberRunGeometry(state, active)
    val run = remember(active?.runId, active?.active) { active?.let(::historyRun) }
    MapRoutePolling(state, run)
    val matchingRoute = state.fieldState.selectedRun?.runId == active?.runId && active != null &&
        state.fieldState.deviceId.equals(active.deviceId, true)
    val gnss = state.deviceDashboardState.status.gnssSensor
    val zone = LocalGeoMapZoneOutput.current
    val data = GeoMapData(geometry = geometry, trail = if (matchingRoute) state.fieldState.route else emptyList(),
        device = mapDevice(gnss), deviceStale = mapDeviceStale(gnss, state.deviceDashboardState.isOnline,
            state.deviceDashboardState.status.sensorTelemetryFresh, state.deviceDashboardState.status.collectedAtEpochMillis) ||
            (zone.outsideCount != null && zone.state == GeoZoneState.UNKNOWN),
        uncertaintyM = mapUncertainty(gnss), zone = zone,
        quality = if (matchingRoute) state.fieldState.routeQuality ?: active?.quality else active?.quality,
        lastPositionAtMillis = gnss.lastSampleAtEpochMillis,
        geometryChanged = geometry != null && state.serverTicketState.tickets.any {
            it.rowId == active?.runId?.let { id -> state.surveyRunState.acceptedStarts[id]?.ticketRowId } && it.zoneHash != geometry.hash
        })
    GeoMapCard(GeoMapMode.RUN, data, onOpen = {
        state.requestedMapHistoryEntryId = null
        state.requestedMapActiveRunId = active?.runId
        state.navController.navigateSingle(Routes.GNSS_MAP)
    })
}

internal fun mapUncertainty(gnss: GnssSensorStatus): Double =
    com.example.jetsoncontroller.ui.zone.ZoneMonitor.uncertainty(
        com.example.jetsoncontroller.ui.zone.ZoneSample.fromStatus(gnss))?.metres ?: 5.0

/** A focused window gives the map the whole safe pane, including landscape wider than the browsing column. */
@Composable
internal fun GeoMapRouteWindow(
    state: AppNavigationState, mode: GeoMapMode, device: String?, run: String?, ticket: String?, history: String?, onBack: () -> Unit
) {
    GeoMapWindow(onBack) {
        GeoMapRoute(state, mode, device, run, ticket, history, onBack)
    }
}

@Composable
internal fun GeoMapRoute(
    state: AppNavigationState, mode: GeoMapMode,
    targetDeviceId: String?, targetRunId: String?, targetTicketId: String?, targetHistoryId: String?,
    onBack: () -> Unit
) {
    val sameDevice = targetDeviceId.equals(state.selectedDeviceId, true)
    val survey = state.surveyRunState
    val run = listOfNotNull(survey.activeRun, survey.latestRun).firstOrNull {
        it.runId == targetRunId && it.deviceId.equals(targetDeviceId, true)
    }
    val history = state.fieldState.runs.firstOrNull {
        (targetHistoryId != null && it.id == targetHistoryId || targetRunId != null && it.runId == targetRunId) &&
            (it.deviceId == null && sameDevice || it.deviceId.equals(targetDeviceId, true))
    }
    val routeRun = remember(run?.runId, run?.active, history?.id) { run?.let(::historyRun) ?: history }
    if (sameDevice) MapRoutePolling(state, routeRun)
    StatusPollingLifecycleEffect(state.dashboardViewModel)
    val frozen = rememberRunGeometry(state, run)
    val root = LocalContext.current.noBackupFilesDir
    val canonicalRunId = targetRunId ?: history?.runId
    val stored = remember(targetDeviceId, canonicalRunId) { GeoMapRunStore(root).read(targetDeviceId, canonicalRunId) }
    val ticket = state.serverTicketState.tickets.firstOrNull { it.rowId == targetTicketId }
    val ticketGeometry = remember(ticket) { ticket?.let(::mapGeometry) }
    val geometry = if (mode == GeoMapMode.ZONE) ticketGeometry else frozen ?: stored
    val currentGnss = state.deviceDashboardState.status.gnssSensor
    var lastGnss by remember(targetDeviceId) { mutableStateOf(GnssSensorStatus()) }
    SideEffect { if (sameDevice) lastGnss = currentGnss }
    val gnss = if (sameDevice) currentGnss else lastGnss
    val matchedRoute = sameDevice && routeRun != null && state.fieldState.selectedRun?.runId == routeRun.runId &&
        state.fieldState.selectedRun?.id == routeRun.id
    val zone = if (mode == GeoMapMode.ZONE || mode == GeoMapMode.LOCATION) GeoMapZoneSnapshot()
        else com.example.jetsoncontroller.ui.zone.rememberRunZoneOutput(targetDeviceId, canonicalRunId) ?: GeoMapZoneSnapshot()
    val data = GeoMapData(geometry = geometry, trail = if (matchedRoute) state.fieldState.route else emptyList(),
        device = mapDevice(gnss), deviceStale = !sameDevice || mapDeviceStale(gnss, state.deviceDashboardState.isOnline,
            state.deviceDashboardState.status.sensorTelemetryFresh, state.deviceDashboardState.status.collectedAtEpochMillis) ||
            (zone.outsideCount != null && zone.state == GeoZoneState.UNKNOWN),
        uncertaintyM = mapUncertainty(gnss), quality = if (matchedRoute) state.fieldState.routeQuality else run?.quality ?: history?.quality,
        zone = zone,
        lastPositionAtMillis = gnss.lastSampleAtEpochMillis,
        geometryChanged = geometry != null && state.serverTicketState.tickets.any {
            it.rowId == geometry.ticketRowId && (it.zoneRevision != geometry.revision || it.zoneHash != geometry.hash)
        })
    var flow by rememberSaveable(targetDeviceId, targetRunId, stateSaver = RunStopFlowSaver) { mutableStateOf(RunStopFlow()) }
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val deviceName = remember(targetDeviceId) {
        state.deviceState.registeredDevices.firstOrNull { it.deviceId.equals(targetDeviceId, true) }?.deviceName ?: state.selectedDeviceName
    }
    val online = sameDevice && state.deviceDashboardState.isOnline
    val available = sameDevice && state.fullControlConnected && matchingStopTarget(run, state.pipelineState.pipelines) != null
    val awaiting = survey.pendingStart != null || survey.unconfirmedRunId != null
    val busy = survey.operation == "stop"
    val observed by rememberUpdatedState(run)
    val isOnline by rememberUpdatedState(online)
    val mayStop by rememberUpdatedState(available)
    val pending by rememberUpdatedState(awaiting)
    val stopping by rememberUpdatedState(busy)
    fun event(event: RunStopEvent) {
        val next = reduceRunStop(flow, event, observed, isOnline, mayStop, pending, stopping)
        flow = next.flow
        next.command?.let { state.surveyRunViewModel.clearMessage(); state.surveyRunViewModel.stop(it) }
        next.openResult?.let {
            view.performHapticFeedback(GeoMotion.acceptedHaptic)
            state.navController.navigateSingle(runResultRoute(it.runId, it.deviceId, it.pipelineId)) {
                popUpTo(Routes.ACTIVE_RUN) { inclusive = true }
            }
        }
    }
    LaunchedEffect(run, online, available, awaiting, busy) { event(RunStopEvent.OBSERVE) }
    LaunchedEffect(sameDevice) { if (!sameDevice) event(RunStopEvent.DISMISS) }
    LaunchedEffect(survey.error) { if (survey.error != null) event(RunStopEvent.FAILURE) }
    if (flow.step == RunStopStep.CONFIRMING && flow.target?.matches(run) == true && run != null) {
        GeoConfirmDialog("${run.contextSnapshot.surveySectionLabel} 수집을 종료할까요?",
            "${deviceName}에서 진행 중인 수집을 종료합니다. 종료되면 장치에 저장된 결과를 확인합니다.",
            "수집 종료", { event(RunStopEvent.CONFIRM) }, { event(RunStopEvent.DISMISS) },
            kind = GeoConfirmKind.DANGER, dismissLabel = "계속 수집", confirmEnabled = online && available && !busy && !awaiting,
            disabledReason = if (!online) "연결되면 종료할 수 있습니다." else "수집 상태를 다시 확인하세요.", openerFocusRequester = focus)
    }
    GeoMapScreen(mode, data, onBack, gnss = gnss,
        runElapsed = run?.telemetry?.durationMillis?.let(::runDurationLabel),
        onStopRequest = if (mode == GeoMapMode.RUN) ({ event(RunStopEvent.REQUEST) }) else null,
        stopEnabled = online && available && !awaiting, stopBusy = busy || flow.step == RunStopStep.STOPPING,
        stopError = survey.error, stopOpener = focus,
        developerMode = state.developerModeEnabled, deviceName = deviceName)
}
