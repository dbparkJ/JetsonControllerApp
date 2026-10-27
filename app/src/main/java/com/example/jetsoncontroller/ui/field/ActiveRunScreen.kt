package com.example.jetsoncontroller.ui.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.StopTarget
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.model.CorrectionRelayState
import com.example.jetsoncontroller.ui.model.SensorState
import com.example.jetsoncontroller.ui.navigation.AppNavigationState
import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.navigateSingle
import com.example.jetsoncontroller.ui.navigateToDeviceRoot
import com.example.jetsoncontroller.ui.runResultRoute
import com.example.jetsoncontroller.ui.matchingStopTarget
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.sensors.SensorActivity
import com.example.jetsoncontroller.ui.sensors.sensorPresentations
import com.example.jetsoncontroller.ui.theme.*
import com.example.jetsoncontroller.ui.tasks.ticketPassiveReadiness
import com.example.jetsoncontroller.ui.zone.runPresentation
import kotlinx.coroutines.delay

private val SystemElapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }

internal const val WorkStopRequestKey = "work.stop.request"

/** Phase 6 supplies observations from the run's bound geometry. No geometry is assumed here. */
internal data class RunZonePresentation(
    val badge: String? = null,
    val tone: Tone = Tone.UNKNOWN,
    val strip: GeoStatusStripState? = null,
    val readiness: String = "수집 중 · 수집 구역 없음",
    val outsideCount: Int? = null
)

internal val RunStopFlowSaver = listSaver<RunStopFlow, String>(
    save = { listOf(it.step.name, it.target?.deviceId.orEmpty(), it.target?.pipelineId.orEmpty(), it.target?.runId.orEmpty()) },
    restore = {
        RunStopFlow(RunStopStep.valueOf(it[0]),
            if (it[1].isBlank()) null else StopTarget(it[1], it[2], it[3]))
    }
)

/** FocusedRoutes can delegate its ACTIVE_RUN body here without discarding terminal evidence. */
@Composable
internal fun ActiveRunRoute(
    state: AppNavigationState,
    mapCard: @Composable () -> Unit = { com.example.jetsoncontroller.ui.map.GeoActiveRunMapCard(state) },
    zone: RunZonePresentation = RunZonePresentation()
) {
    val zoneOutput = com.example.jetsoncontroller.ui.map.LocalGeoMapZoneOutput.current
    val monitoredZone = if (zoneOutput.outsideCount != null) zoneOutput.runPresentation(state.selectedDeviceName) else zone
    val survey = state.surveyRunState
    val stopRequestHandle = state.navController.currentBackStackEntry?.savedStateHandle
    val stopRequest = stopRequestHandle?.getStateFlow<ArrayList<String>?>(WorkStopRequestKey, null)
        ?.collectAsStateWithLifecycle()?.value
    val requestedStopTarget = stopRequest?.takeIf { it.size == 3 }?.let { StopTarget(it[0], it[1], it[2]) }
    val deviceId = state.selectedDeviceId ?: survey.deviceId
    val active = survey.activeRun?.takeIf { it.deviceId.equals(deviceId, true) }
    val latest = survey.latestRun?.takeIf { it.deviceId.equals(deviceId, true) }
    val dashboard = state.deviceDashboardState
    val connection = deviceConnectionUi(dashboard.isOnline, dashboard.transportType)
    val camera = sensorPresentations(dashboard.status, dashboard.isOnline, System.currentTimeMillis()).camera
    val cameraWord = when (camera.activity) {
        SensorActivity.ACTIVE -> SensorState.RECEIVING
        SensorActivity.STALE -> SensorState.DELAYED
        SensorActivity.DISCONNECTED -> SensorState.DISCONNECTED
        SensorActivity.UNCONFIGURED -> SensorState.NOT_CONFIGURED
        SensorActivity.CONNECTED, SensorActivity.WAITING -> SensorState.WAITING
    }
    val relay = state.pipelineState.mobileRtkRelay.takeIf { it.pipelineId == (active ?: latest)?.pipelineId && it.pipelineId != null }
    val position = if (dashboard.statusFreshness == StatusFreshness.CURRENT) ticketPassiveReadiness(
        state.selectedDeviceName, dashboard.status, dashboard.isOnline, connection.label, java.time.Instant.now()
    ).position else "위치 미확인"
    ActiveRunScreen(
        deviceName = state.selectedDeviceName, connectionLabel = connection.label, connectionTone = connection.tone,
        run = active, latestRun = latest, deviceScopeKey = deviceId ?: state.selectedDeviceName,
        telemetryReceivedAtElapsedRealtime = survey.telemetryReceivedAtElapsedRealtime,
        elapsedLabel = null,
        lastObservedLabel = state.pipelineState.observedAtMillis?.let {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(it))
        },
        sensorSummary = null, storageSummary = null,
        projectLabel = survey.selectedProject?.label, sectionLabel = survey.selectedSection?.label,
        stopInProgress = survey.operation == "stop",
        requestedStopTarget = requestedStopTarget,
        onStopRequestHandled = { stopRequestHandle?.set<ArrayList<String>?>(WorkStopRequestKey, null) },
        online = dashboard.isOnline,
        stopAvailable = state.fullControlConnected && matchingStopTarget(active, state.pipelineState.pipelines) != null,
        pendingStart = survey.pendingStart != null, unconfirmedRunId = survey.unconfirmedRunId,
        onRetryPendingStart = state.surveyRunViewModel::retryPendingStart,
        developerModeEnabled = state.developerModeEnabled, unreadCount = state.alertCenterState.unreadCount,
        onDevices = { navigateToDeviceRoot(state.navController) },
        onAlerts = { state.navController.navigateSingle(Routes.ALERTS) },
        onBack = { state.navController.popBackStack() },
        onRefresh = { state.surveyRunViewModel.refresh(); state.pipelineViewModel.refresh() },
        onStop = {}, onStopTarget = { target ->
            state.surveyRunViewModel.clearMessage()
            state.surveyRunViewModel.stop(target)
        }, stopError = survey.error,
        onOpenResultTarget = { target ->
            state.navController.navigateSingle(runResultRoute(target.runId, target.deviceId, target.pipelineId)) {
                popUpTo(Routes.ACTIVE_RUN) { inclusive = true }
                launchSingleTop = true
            }
        },
        onCamera = { state.navController.navigateSingle(Routes.CAMERA_PREVIEW) },
        onMap = {
            state.requestedMapHistoryEntryId = null
            state.requestedMapActiveRunId = (active ?: latest)?.runId
            state.fieldToolsViewModel.refresh(retainLoaded = true)
            state.navController.navigateSingle(Routes.GNSS_MAP)
        },
        cameraStateLabel = if (dashboard.statusFreshness == StatusFreshness.CURRENT) cameraWord.word else SensorState.UNKNOWN.word,
        cameraStateTone = if (dashboard.statusFreshness == StatusFreshness.CURRENT) cameraWord.tone else Tone.UNKNOWN,
        correctionRelay = relay?.let {
            when {
                it.error != null -> CorrectionRelayState.ERROR
                it.preparing -> CorrectionRelayState.CONNECTING
                it.active -> CorrectionRelayState.RELAYING
                else -> CorrectionRelayState.OFF
            }
        },
        mapCard = mapCard, zone = monitoredZone,
        positionLabel = position, positionTone = when (position) {
            "정밀 위치" -> Tone.SUCCESS
            "대략 위치", "위치 없음" -> Tone.WARNING
            else -> Tone.UNKNOWN
        }
    )
}

/** The shell owns system insets. Legacy callbacks remain usable until the focused graph is wired. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ActiveRunScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: Tone,
    run: PipelineRun?,
    telemetryReceivedAtElapsedRealtime: Long? = null,
    elapsedLabel: String?,
    lastObservedLabel: String?,
    sensorSummary: String?,
    storageSummary: String?,
    stopInProgress: Boolean,
    online: Boolean,
    stopAvailable: Boolean = true,
    pendingStart: Boolean = false,
    unconfirmedRunId: String? = null,
    onRetryPendingStart: () -> Unit = {},
    developerModeEnabled: Boolean = false,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onCamera: () -> Unit,
    onMap: () -> Unit,
    onHome: () -> Unit = {},
    onOpenResult: () -> Unit = {},
    elapsedRealtimeMillis: () -> Long = SystemElapsedRealtimeMillis,
    mapCard: @Composable () -> Unit = {},
    zone: RunZonePresentation = RunZonePresentation(),
    correctionRelay: CorrectionRelayState? = null,
    onCorrectionRelay: (() -> Unit)? = null,
    cameraStateLabel: String? = null,
    cameraStateTone: Tone = Tone.UNKNOWN,
    onConnectionHelp: () -> Unit = onDevices,
    /** Include terminal evidence; absence of an active run alone never means it ended. */
    latestRun: PipelineRun? = null,
    deviceScopeKey: String = deviceName,
    stopError: String? = null,
    onStopTarget: ((StopTarget) -> Unit)? = null,
    onOpenResultTarget: ((StopTarget) -> Unit)? = null,
    projectLabel: String? = null,
    sectionLabel: String? = null,
    positionLabel: String? = null,
    positionTone: Tone = Tone.UNKNOWN,
    requestedStopTarget: StopTarget? = null,
    onStopRequestHandled: () -> Unit = {}
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val window = LocalGeoWindow.current
    val view = LocalView.current
    val observedRun = run ?: latestRun
    val awaitingStart = pendingStart || unconfirmedRunId != null
    var flow by rememberSaveable(deviceScopeKey, stateSaver = RunStopFlowSaver) { mutableStateOf(RunStopFlow()) }
    val stopFocus = remember { FocusRequester() }
    val relayFocus = remember { FocusRequester() }
    var relayInfo by rememberSaveable(deviceScopeKey) { mutableStateOf(false) }
    val openResult by rememberUpdatedState(onOpenResult)
    val openTargetResult by rememberUpdatedState(onOpenResultTarget)
    val telemetry = verifiedRunTelemetry(observedRun)
    var nowElapsed by remember(observedRun?.runId) { mutableLongStateOf(elapsedRealtimeMillis()) }
    var displayedDuration by remember(observedRun?.runId, telemetry?.observedAtEpochMillis, telemetry?.durationMillis) {
        mutableStateOf(telemetry?.durationMillis)
    }
    val stopping = stopInProgress || flow.step == RunStopStep.STOPPING
    val state = activeRunState(observedRun, online, awaitingStart, stopping, stopAvailable)

    LaunchedEffect(observedRun, online, stopAvailable, awaitingStart, stopInProgress, flow.step) {
        val next = reduceRunStop(flow, RunStopEvent.OBSERVE, observedRun, online, stopAvailable, awaitingStart, stopInProgress)
        flow = next.flow
        next.openResult?.let { target ->
            view.performHapticFeedback(GeoMotion.acceptedHaptic)
            openTargetResult?.invoke(target) ?: openResult()
        }
    }
    LaunchedEffect(stopError) {
        if (stopError != null) flow = reduceRunStop(flow, RunStopEvent.FAILURE, observedRun,
            online, stopAvailable, awaitingStart, stopInProgress).flow
    }
    LaunchedEffect(observedRun?.runId, telemetry?.observedAtEpochMillis, telemetryReceivedAtElapsedRealtime, state) {
        do {
            nowElapsed = elapsedRealtimeMillis()
            if (telemetry != null && telemetryReceivedAtElapsedRealtime != null && state == ActiveRunState.COLLECTING) {
                displayedDuration = smoothedDurationMillis(telemetry.durationMillis,
                    telemetryReceivedAtElapsedRealtime, nowElapsed, mayAdvance = true)
            }
            delay(1_000L)
        } while (telemetry != null)
    }
    val telemetryAge = telemetryReceivedAtElapsedRealtime?.let { (nowElapsed - it).coerceAtLeast(0L) }
    val figureState = if (!online && !awaitingStart) ActiveRunState.DISCONNECTED else state
    val figures = activeRunFigures(observedRun, displayedDuration, elapsedLabel, figureState, telemetryAge, zone.outsideCount)
    val observedLabel = telemetry?.takeIf { telemetryAge != null }?.let { telemetryObservationLabel(it, telemetryAge!!) }
        ?: lastObservedLabel ?: "미확인"
    val latestRunInput by rememberUpdatedState(observedRun)
    val latestOnline by rememberUpdatedState(online)
    val latestStopAvailable by rememberUpdatedState(stopAvailable)
    val latestAwaiting by rememberUpdatedState(awaitingStart)
    val latestBusy by rememberUpdatedState(stopInProgress)
    val latestStop by rememberUpdatedState(onStop)
    val latestTargetStop by rememberUpdatedState(onStopTarget)

    fun stopEvent(event: RunStopEvent) {
        val next = reduceRunStop(flow, event, latestRunInput, latestOnline, latestStopAvailable, latestAwaiting, latestBusy)
        flow = next.flow
        next.command?.let { target -> latestTargetStop?.invoke(target) ?: latestStop() }
    }
    LaunchedEffect(requestedStopTarget) {
        if (requestedStopTarget != null) {
            flow = reduceRunStop(flow, RunStopEvent.REQUEST, latestRunInput, latestOnline,
                latestStopAvailable, latestAwaiting, latestBusy, requestedTarget = requestedStopTarget).flow
            onStopRequestHandled()
        }
    }
    BackHandler(onBack = onBack)

    if (relayInfo && correctionRelay != null) {
        GeoInfoDialog("보정 정보 중계", onDismissRequest = { relayInfo = false }, openerFocusRequester = relayFocus) {
            Text(deviceName, color = colors.text, style = type.titleSm)
            GeoBadge(correctionRelay.word, correctionRelay.tone)
        }
    }

    if (flow.step == RunStopStep.CONFIRMING && flow.target?.matches(observedRun) == true && observedRun != null) {
        val enabled = online && stopAvailable && !stopInProgress && !awaitingStart && observedRun.active
        GeoConfirmDialog(
            title = "${observedRun.contextSnapshot.surveySectionLabel} 수집을 종료할까요?",
            body = "${deviceName}에서 진행 중인 수집을 종료합니다. 종료되면 장치에 저장된 결과를 확인합니다.",
            confirmLabel = "수집 종료", dismissLabel = "계속 수집", kind = GeoConfirmKind.DANGER,
            onConfirm = { stopEvent(RunStopEvent.CONFIRM) },
            onDismissRequest = { stopEvent(RunStopEvent.DISMISS) },
            confirmEnabled = enabled,
            disabledReason = if (!online) "연결되면 종료할 수 있습니다." else "수집 상태를 다시 확인하세요.",
            openerFocusRequester = stopFocus,
            modifier = Modifier.testTag("confirm-stop"),
            facts = {
                GeoPropertyRow("장치", deviceName)
                GeoPropertyRow("구간", observedRun.contextSnapshot.surveySectionLabel)
                GeoCopyableValue(observedRun.runId, label = "수집 ID")
            }
        )
    }

    val controlScroll = rememberScrollState()
    val currentMap by rememberUpdatedState(mapCard)
    val map = remember { movableContentOf {
        CompositionLocalProvider(com.example.jetsoncontroller.ui.map.LocalGeoMapBackground provides true) { currentMap() }
    } }
    val showResult = { observedRun?.let { openTargetResult?.invoke(runTarget(it)) ?: openResult() }; Unit }
    val primaryAction = when (state) {
        ActiveRunState.START_CHECKING -> GeoButtonAction("시작 결과 확인", if (pendingStart) onRetryPendingStart else onRefresh,
            enabled = online, disabledReason = "연결되면 시작 결과를 확인할 수 있습니다.")
        ActiveRunState.UNKNOWN -> GeoButtonAction("상태 다시 확인", onRefresh)
        ActiveRunState.ENDED -> GeoButtonAction("결과 보기", showResult)
        else -> GeoButtonAction("수집 종료", { stopEvent(RunStopEvent.REQUEST) }, GeoButtonStyle.DangerFilled,
            enabled = state == ActiveRunState.COLLECTING || state == ActiveRunState.STOP_CHECKING,
            busy = state == ActiveRunState.STOP_CHECKING, busyLabel = "종료하는 중", icon = GeoIcons.stop)
    }
    val content: @Composable () -> Unit = {
        val endedReceipt = pipelineRunReceiptCells(observedRun, Tone.UNKNOWN).first()
        val disconnected = if (!online) GeoStatusStripState(GeoStripKind.DISCONNECTED, deviceName,
            "연결이 끊겼습니다. 수집은 장치에서 계속될 수 있습니다.",
            listOf(GeoOverlayAction("다시 연결", onDevices), GeoOverlayAction("연결 방법", onConnectionHelp))) else null
        GeoStatusStrip(listOfNotNull(disconnected, zone.strip?.takeIf { !awaitingStart && observedRun?.active == true }))
        GeoBadge(when (state) {
            ActiveRunState.COLLECTING -> "수집 중"
            ActiveRunState.START_CHECKING -> "시작 확인 중"
            ActiveRunState.STOP_CHECKING -> "종료 확인 중"
            ActiveRunState.ENDED -> endedReceipt.value
            ActiveRunState.DISCONNECTED -> "연결 끊김"
            ActiveRunState.UNKNOWN -> "수집 미확인"
        }, when (state) {
            ActiveRunState.COLLECTING -> Tone.SUCCESS
            ActiveRunState.ENDED -> endedReceipt.tone
            ActiveRunState.DISCONNECTED -> Tone.WARNING
            ActiveRunState.UNKNOWN -> Tone.UNKNOWN
            else -> Tone.PENDING
        })
        RunHeading(observedRun, deviceName, state, projectLabel, sectionLabel)
        Spacer(Modifier.height(24.dp))
        val note = when (state) {
            ActiveRunState.START_CHECKING -> "같은 수집의 시작 결과를 확인합니다. 확인이 끝날 때까지 새 수집을 시작할 수 없습니다."
            ActiveRunState.UNKNOWN -> "마지막 확인 $observedLabel · 상태를 확인할 때까지 새 수집을 시작할 수 없습니다."
            ActiveRunState.ENDED -> "결과 보기에서 장치 저장과 서버 수신 상태를 확인하세요."
            else -> null
        }
        note?.let { GeoInlineNote(it, tone = if (awaitingStart) Tone.PENDING else Tone.UNKNOWN) }
        stopError?.let { GeoInlineNote(it, tone = Tone.ERROR, kind = GeoNoteKind.LIVE_ERROR) }
        ActiveRunClockCard(figures.take(2))
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            positionLabel?.let { GeoBadge(it, positionTone, variant = GeoBadgeVariant.INLINE) }
            zone.badge?.let { GeoBadge(it, zone.tone, variant = GeoBadgeVariant.INLINE) }
            cameraStateLabel?.let { GeoBadge("카메라 $it", cameraStateTone, variant = GeoBadgeVariant.INLINE) }
        }
        if (observedRun != null && !awaitingStart) {
            GeoRowList(GeoRowTrailing.Status) {
                GeoRow("카메라 영상", onCamera, enabled = online && observedRun.active && !stopping,
                    disabledReason = "장치의 수집 상태가 확인되면 영상을 볼 수 있습니다.",
                    leading = { GeoIconTile(GeoIcons.camera) }, divider = correctionRelay != null,
                    badge = cameraStateLabel?.let { label -> { GeoBadge(label, cameraStateTone) } })
                correctionRelay?.let { relay ->
                    GeoRow("보정 정보 중계", { if (onCorrectionRelay != null) onCorrectionRelay() else relayInfo = true },
                        modifier = Modifier.focusRequester(relayFocus),
                        leading = { GeoIconTile(GeoIcons.link) }, divider = false, badge = { GeoBadge(relay.word, relay.tone) })
                }
            }
            GeoPropertyList(figures.drop(2).map { it.label }, Modifier.padding(top = 16.dp)) {
                figures.drop(2).forEach { figure ->
                    val shown = figurePresentation(figure)
                    GeoPropertyRow(figure.label, shown.value, numeric = true, supportingText = shown.detail)
                }
            }
            Text("관찰한 시간 기준", color = colors.textSecondary, style = type.caption)
        }
        Text("마지막 확인 $observedLabel", Modifier.padding(top = 8.dp), color = colors.textSecondary, style = type.caption)
    }
    if (window.twoPane) {
        GeoPaneLayout(policy = GeoPanePolicy.ACTIVE_RUN, singlePane = GeoPaneSelection.LIST, detailFill = true,
            list = {
                GeoLargeTitleScaffold("수집 중", onBack = onBack) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(controlScroll)) { content() }
                        RunActionArea(state, deviceName, connectionLabel, zone, online, stopFocus,
                            { stopEvent(RunStopEvent.REQUEST) }, if (pendingStart) onRetryPendingStart else onRefresh,
                            onRefresh, showResult)
                    }
                }
            }, detail = { map() })
    } else {
        GeoMapSheetScaffold("수집 중", primaryAction,
            primaryActionModifier = Modifier.focusRequester(stopFocus).testTag("request-stop"),
            controls = { com.example.jetsoncontroller.ui.map.GeoMapControl(GeoIcons.back, "뒤로", onBack,
                Modifier.align(Alignment.TopStart)) },
            map = { map() }, sheetContent = { content() })
    }
}

@Composable
private fun ActiveRunClockCard(figures: List<GeoFigure>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colors = LocalGeoColors.current
        val type = LocalGeoType.current
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val elapsed = figures.firstOrNull()?.let(::figurePresentation)
        val capacityFigure = figures.getOrNull(1)
        val capacityShown = capacityFigure?.let(::figurePresentation)
        val capacityStyle = if (LocalGeoWindow.current.widthDp >= 600f) type.tabletFigure else type.phoneFigure
        val clockWidth = with(density) {
            measurer.measure(AnnotatedString(elapsed?.value ?: "미확인"), type.clock, softWrap = false).size.width.toDp()
        }
        val capacityWidth = with(density) { maxOf(
            measurer.measure(AnnotatedString(capacityShown?.value.orEmpty()), capacityStyle, softWrap = false).size.width,
            measurer.measure(AnnotatedString(capacityFigure?.label.orEmpty()), type.caption, softWrap = false).size.width
        ).toDp() }
        val clock: @Composable () -> Unit = {
            Column {
                Text("경과 시간", color = colors.textSecondary, style = type.caption)
                Text(elapsed?.value ?: "미확인", color = colors.text, style = type.clock)
                elapsed?.detail?.let { Text(it, color = colors.textSecondary, style = type.caption) }
            }
        }
        val capacity: @Composable () -> Unit = {
            figures.getOrNull(1)?.let { figure ->
                val shown = figurePresentation(figure)
                Column {
                    Text(figure.label, color = colors.textSecondary, style = type.caption)
                    Text(shown.value, color = colors.text, style = capacityStyle)
                    shown.detail?.let { Text(it, color = colors.textSecondary, style = type.caption) }
                }
            }
        }
        if (runClockStacks(maxWidth.value, density.fontScale, clockWidth.value, capacityWidth.value)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { clock(); capacity() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { clock() }
                Box(Modifier.width(capacityWidth)) { capacity() }
            }
        }
    }
}

@Composable
private fun RunHeading(
    run: PipelineRun?, deviceName: String, state: ActiveRunState,
    projectLabel: String?, sectionLabel: String?
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val section = if (state == ActiveRunState.START_CHECKING) sectionLabel ?: run?.contextSnapshot?.surveySectionLabel
        else run?.contextSnapshot?.surveySectionLabel ?: sectionLabel
    val project = if (state == ActiveRunState.START_CHECKING) projectLabel ?: run?.contextSnapshot?.surveyProjectLabel
        else run?.contextSnapshot?.surveyProjectLabel ?: projectLabel
    val title = when (state) {
        ActiveRunState.START_CHECKING -> "시작 확인 중"
        ActiveRunState.UNKNOWN -> "수집 상태를 확인할 수 없습니다"
        ActiveRunState.STOP_CHECKING -> "종료 확인 중"
        ActiveRunState.ENDED -> "수집이 끝났습니다"
        else -> section ?: if (state == ActiveRunState.DISCONNECTED) "연결이 끊겼습니다" else "수집 중"
    }
    val meta = listOfNotNull(deviceName, project,
        section?.takeIf { title != it }).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.semantics { heading() }, style = type.sectionTitle, color = c.text)
        Text(meta, style = type.caption, color = c.textSecondary)
    }
}

@Composable
private fun RunActionArea(
    state: ActiveRunState, deviceName: String, connectionLabel: String, zone: RunZonePresentation,
    online: Boolean, stopFocus: FocusRequester, onRequestStop: () -> Unit,
    onCheckStart: () -> Unit, onRefresh: () -> Unit, onResult: () -> Unit
) {
    val readiness = when (state) {
        ActiveRunState.COLLECTING -> "$deviceName · ${zone.readiness}."
        ActiveRunState.DISCONNECTED -> "$deviceName · 연결되면 종료할 수 있습니다."
        ActiveRunState.STOP_CHECKING, ActiveRunState.ENDED -> "$deviceName · 종료되면 장치에 저장된 결과를 확인합니다."
        else -> if (online) "$deviceName · $connectionLabel · 지금 확인할 수 있습니다."
            else "$deviceName · 연결되면 확인할 수 있습니다."
    }
    val warning = !online || state == ActiveRunState.DISCONNECTED ||
        (state == ActiveRunState.COLLECTING && zone.tone == Tone.WARNING)
    val pending = state == ActiveRunState.STOP_CHECKING
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.space4)) {
        GeoReadinessLine(readiness, if (warning) Tone.WARNING else if (pending) Tone.PENDING else Tone.SUCCESS)
        val action = when (state) {
            ActiveRunState.START_CHECKING -> GeoButtonAction("시작 결과 확인", onCheckStart,
                enabled = online, disabledReason = "연결되면 시작 결과를 확인할 수 있습니다.")
            ActiveRunState.UNKNOWN -> GeoButtonAction("상태 다시 확인", onRefresh)
            ActiveRunState.ENDED -> GeoButtonAction("결과 보기", onResult)
            else -> GeoButtonAction("수집 종료", onRequestStop, style = GeoButtonStyle.DangerFilled, icon = GeoIcons.stop,
                enabled = state == ActiveRunState.COLLECTING || state == ActiveRunState.STOP_CHECKING,
                busy = state == ActiveRunState.STOP_CHECKING, busyLabel = "종료하는 중")
        }
        GeoFixedActionBar(action, Modifier.focusRequester(stopFocus).testTag("request-stop"))
    }
}
