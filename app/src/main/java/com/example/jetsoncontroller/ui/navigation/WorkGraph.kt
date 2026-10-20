package com.example.jetsoncontroller.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.survey.SurveyRunScreen
import com.example.jetsoncontroller.ui.survey.TaskChoiceSheet
import com.example.jetsoncontroller.ui.survey.SurveyStartSheet
import com.example.jetsoncontroller.ui.survey.optionalStartChecks
import com.example.jetsoncontroller.data.server.TicketReportDeliveryState
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.navigation.NavBackStackEntry
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.tasks.*
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.components.geo.shell.GeoPaneLayout
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.data.server.ServerTicket
import java.time.Instant

internal enum class WorkTicketEvent { OPEN_TICKET, REQUEST_START, SELECT_TASK, START_ACCEPTED }

/** Task choice and S8 stay on the ticket entry, preserving its draft and owning tab. */
internal fun ticketJourneyDestination(
    event: WorkTicketEvent, rowId: String? = null,
    accepted: com.example.jetsoncontroller.data.survey.PendingContextualStart? = null
): String? = when (event) {
    WorkTicketEvent.OPEN_TICKET -> Routes.TICKET_DETAIL.replace("{rowId}",
        java.net.URLEncoder.encode(requireNotNull(rowId), "UTF-8").replace("+", "%20"))
    WorkTicketEvent.REQUEST_START, WorkTicketEvent.SELECT_TASK -> null
    WorkTicketEvent.START_ACCEPTED -> Routes.ACTIVE_RUN.takeIf { accepted != null }
}

internal fun AppNavigationState.workRootCheckingDevice(): Boolean {
    val survey = surveyRunState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
        ?: com.example.jetsoncontroller.ui.survey.SurveyRunUiState(restoringLocalState = true)
    return survey.restoringLocalState || connectingLanDeviceId.equals(selectedDeviceId, true) ||
        (survey.pipelineId == null && survey.pendingStart == null && survey.unconfirmedRunId == null &&
            (pipelineState.observedAtMillis == null || !pipelineState.deviceId.equals(selectedDeviceId, true)))
}

internal fun AppNavigationState.workRootReachable(): Boolean = workDeviceReachable(
    (transportState as? TransportState.Connected)?.takeIf { it.deviceId.equals(selectedDeviceId, true) } != null,
    deviceDashboardState.statusFreshness
)

internal fun AppNavigationState.workRootDeviceTone(): Tone = workRootConnectionTone(
    selectedConnection.tone, workRootReachable(),
    checking = workRootCheckingDevice(),
    basicConnection = deviceDashboardState.transportType == TransportType.BLE,
    permissionMissing = !localNetworkPermissionGranted && !fullControlConnected,
    credentialsRejected = deviceDashboardState.connectionState is com.example.jetsoncontroller.model.ConnectionState.RegistrationRequired
)

private fun AppNavigationState.openWorkHistoryResult(history: com.example.jetsoncontroller.model.TaskRun) {
    val deviceId = history.deviceId ?: history.contextSnapshot?.deviceId ?: fieldState.deviceId ?: return
    navController.navigateSingle(workHistoryResultRoute(history, deviceId))
    navController.currentBackStackEntry?.savedStateHandle?.set("resultHistory", com.google.gson.Gson().toJson(history))
}

@Composable
private fun WorkRootRoute(state: State<AppNavigationState>) {
    with(state.value) {
        StatusPollingLifecycleEffect(dashboardViewModel)
        val profile = directServerState.selectedProfileId
        val credential = directServerState.profiles.firstOrNull { it.profile.profileId == profile }?.credentialRevision
        val owner = LocalLifecycleOwner.current
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(profile, credential, selectedDeviceId, owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    now = Instant.now()
                    if (profile != null && selectedDeviceId != null) serverTicketViewModel.refresh(profile, selectedDeviceId)
                    delay(60_000)
                }
            }
        }
        LaunchedEffect(selectedDeviceId, fullControlConnected) {
            if (fullControlConnected) { pipelineViewModel.refresh(); fieldToolsViewModel.refresh() }
        }
        val survey = surveyRunState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
            ?: com.example.jetsoncontroller.ui.survey.SurveyRunUiState(restoringLocalState = true)
        val run = survey.latestRun
        val receipt = serverReceiptPresentation(run, (listOfNotNull(uploadState.currentJob) + uploadState.queue)
            .distinctBy { it.id }.filter { run != null && it.rootId == run.output.rootId && it.relativePath.trim('/') == run.output.path.trim('/') }
            .map(::workUploadWithMatchingReceipt))
        val link = run?.let { serverTicketViewModel.linkForRun(it.runId, it.deviceId) }
        val reportDelivery = run?.let { serverTicketViewModel.reportDeliveryState(it.runId, true) }
        val reportFailed = reportDelivery == TicketReportDeliveryState.FAILED
        val reportPending = reportDelivery == TicketReportDeliveryState.PENDING
        LaunchedEffect(link?.profileId) { link?.profileId?.let(serverTicketViewModel::refreshCapabilities) }
        val reportPresentation = ticketCompletionPresentation(
            attached = link != null, serverReceiptConfirmed = receipt.tone == Tone.SUCCESS,
            role = link?.profileId?.let(serverTicketViewModel::reportRole),
            conflicted = run?.let { serverTicketViewModel.ticketConflictForRun(it.runId) } == true,
            startRejected = run?.let { serverTicketViewModel.ticketStartRejected(it.runId) } == true,
            startRejectedMessage = run?.let { serverTicketViewModel.ticketStartFailureMessage(it.runId) })
        val reportEnabled = reportPresentation.enabled && reportDelivery !in setOf(
            TicketReportDeliveryState.PENDING, TicketReportDeliveryState.SENT, TicketReportDeliveryState.REJECTED)
        val reportKey = link?.let { listOf(it.profileId, it.rowId, it.ticketRevision, it.runId,
            receipt.label, receipt.tone, reportDelivery).toString() }
        val tasks = buildTasksHomeUiState(TasksHomeInput(
            selectedRegisteredDeviceName, fullControlConnected, survey,
            fieldState.runs.takeIf { fieldState.deviceId.equals(selectedDeviceId, true) }.orEmpty(),
            (listOfNotNull(uploadState.currentJob) + uploadState.queue).distinctBy { it.id },
            pipelineState.pipelines.takeIf { pipelineState.deviceId.equals(selectedDeviceId, true) }.orEmpty(), developerModeEnabled,
            status = deviceDashboardState.status.takeIf { deviceDashboardState.isOnline && !survey.restoringLocalState },
            connectionWord = selectedConnection.label, connectionTone = selectedConnection.tone,
            basicConnection = deviceDashboardState.isOnline && deviceDashboardState.transportType == TransportType.BLE,
            // Until this device's task list has arrived once, the run state is being checked, not unknown.
            // A pending start or an unconfirmed run still shows its own Focus block.
            checkingDevice = workRootCheckingDevice(),
            permissionMissing = !localNetworkPermissionGranted && !fullControlConnected,
            credentialsRejected = deviceDashboardState.connectionState is com.example.jetsoncontroller.model.ConnectionState.RegistrationRequired,
            lastResponseLabel = selectedSnapshot?.lastSeenAtEpochMillis?.let { com.example.jetsoncontroller.ui.devices.formatSeenAt(it, now.toEpochMilli()) },
            receiptVerified = receipt.tone == Tone.SUCCESS, ticketLinked = link != null,
            completionReported = reportDelivery == TicketReportDeliveryState.SENT, now = now,
            tasksFresh = pipelineState.deviceId.equals(selectedDeviceId, true) &&
                com.example.jetsoncontroller.ui.pipelines.tasksAreFresh(
                    pipelineState.controlAvailable, pipelineState.observedAtMillis, System.currentTimeMillis()),
            reportStates = fieldState.runs.mapNotNull { history ->
                val id = history.runId ?: return@mapNotNull null
                val ticket = serverTicketState.tickets.firstOrNull { it.runId == id }
                val attached = serverTicketViewModel.linkForRun(id, history.deviceId ?: selectedDeviceId.orEmpty())
                val word = when {
                    serverTicketViewModel.reportDeliveryState(id, true) == TicketReportDeliveryState.FAILED -> com.example.jetsoncontroller.ui.model.CompletionReportState.FAILED
                    serverTicketViewModel.reportDeliveryState(id, true) == TicketReportDeliveryState.PENDING -> com.example.jetsoncontroller.ui.model.CompletionReportState.WAITING
                    serverTicketViewModel.reportDeliveryState(id, true) == TicketReportDeliveryState.SENT -> com.example.jetsoncontroller.ui.model.CompletionReportState.REPORTED
                    ticket != null -> ticketReportWord(ticket)
                    attached == null -> null
                    else -> com.example.jetsoncontroller.ui.model.CompletionReportState.NOT_REPORTED
                }
                word?.let { id to it }
            }.toMap(), lastObservedAt = selectedSnapshot?.lastSeenAtEpochMillis?.let(Instant::ofEpochMilli),
            storageWarningPercent = alertSettings.storageThresholdPercent,
            temperatureWarningC = alertSettings.temperatureThresholdC,
            reachable = workRootReachable()
        )).copy(ticketSection = serverTicketState.takeIf {
            profile != null && it.profileId == profile && it.deviceId.equals(selectedDeviceId, true)
        })
        var switcher by rememberSaveable(selectedDeviceId) { mutableStateOf(false) }
        var reportRun by remember { mutableStateOf<com.example.jetsoncontroller.model.PipelineRun?>(null) }
        var confirmedReportKey by remember { mutableStateOf<String?>(null) }
        val opener = remember { FocusRequester() }
        val refresh = {
            dashboardViewModel.requestStatus()
            surveyRunViewModel.refresh()
            if (profile != null && selectedDeviceId != null) serverTicketViewModel.refresh(profile, selectedDeviceId)
        }
        if (switcher) GeoSheet("장치 선택", { switcher = false }, openerFocusRequester = opener) {
            deviceState.registeredDevices.forEach { device ->
                val current = device.deviceId.equals(selectedDeviceId, true)
                val snapshot = deviceSnapshots[device.deviceId.lowercase()]
                WorkDeviceChoiceRow(device.deviceName, current, {
                    switcher = false
                    if (!current) connectRegisteredDevice(device)
                }, word = if (current) tasks.deviceStrip?.connectionWord ?: "미확인" else "오프라인",
                    tone = if (current) tasks.deviceStrip?.connectionTone ?: Tone.UNKNOWN else Tone.UNKNOWN,
                    detail = if (current) tasks.deviceStrip?.facts?.joinToString(" · ")?.takeIf(String::isNotBlank)
                    else snapshot?.lastSeenAtEpochMillis?.let {
                        "마지막 응답 ${com.example.jetsoncontroller.ui.devices.formatSeenAt(it, now.toEpochMilli())}"
                    } ?: "마지막 응답 미확인")
            }
            GeoButton("장치 관리", { switcher = false; navigateToDeviceRoot(navController) }, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
        }
        reportRun?.let { target ->
            val valid = target.deviceId.equals(selectedDeviceId, true) && target.runId == run?.runId &&
                receipt.tone == Tone.SUCCESS && link != null && reportEnabled && confirmedReportKey == reportKey
            GeoConfirmDialog("${target.contextSnapshot.surveySectionLabel} 완료를 보고할까요?",
                "서버 수신을 확인했습니다. GEO& Work 상태가 '완료'로 바뀝니다.", "완료 보고",
                onConfirm = {
                    if (valid) {
                        if (reportFailed) serverTicketViewModel.retryReport(target.runId, true)
                        else serverTicketViewModel.reportCompletion(target)
                    }
                    reportRun = null
                },
                onDismissRequest = { reportRun = null }, confirmEnabled = valid,
                disabledReason = if (valid) null else "수집 결과와 서버 수신을 다시 확인하세요.")
        }
        LaunchedEffect(selectedDeviceId) { reportRun = null }
        TasksHomeScreen(tasks, { switcher = true }, openSurveyPreparation, openSurveyPreparation,
            { navController.navigateSingle(Routes.ACTIVE_RUN) },
            { id -> fieldState.runs.firstOrNull { it.id == id }?.let { openWorkHistoryResult(it) } },
            { navController.navigateSingle(Routes.TASK_HISTORY) },
            { navController.navigateSingle("task_detail/${Uri.encode(it)}") },
            onOpenTicket = { rowId -> ticketJourneyDestination(WorkTicketEvent.OPEN_TICKET, rowId)?.let { navController.navigateSingle(it) } },
            onSelectTicketUnit = serverTicketViewModel::selectUnit,
            onOpenDevice = { navController.navigateSingle(Routes.DEVICE_DETAIL) },
            onFocusAction = { command ->
                when (command) {
                    NextActionCommand.CHECK_START, NextActionCommand.CHECK_STATE -> { surveyRunViewModel.refresh(); navController.navigateSingle(Routes.ACTIVE_RUN) }
                    NextActionCommand.VIEW_RUN -> navController.navigateSingle(Routes.ACTIVE_RUN)
                    NextActionCommand.VIEW_RESULT -> run?.let { navController.navigateSingle(runResultRoute(it.runId, it.deviceId, it.pipelineId)) }
                    NextActionCommand.REPORT -> { confirmedReportKey = reportKey; reportRun = run }
                    NextActionCommand.RECONNECT -> currentRegisteredDevice?.let(connectRegisteredDevice)
                    NextActionCommand.ALLOW_PERMISSION -> onRequestLocalNetworkPermission()
                    NextActionCommand.REGISTER, NextActionCommand.REGISTER_AGAIN -> navController.navigateSingle(Routes.QR_SCANNER)
                    NextActionCommand.WIFI_SETTINGS -> navController.navigateSingle(Routes.NETWORK_SETTINGS)
                    NextActionCommand.CONNECTION_HELP -> openConnectionMethods(navController)
                }
            }, onRefresh = refresh, onServerConnection = { navController.navigateSingle(SETTINGS_SERVER_ROUTE) },
            onAlerts = { navController.navigateSingle(Routes.ALERTS) }, unreadCount = alertCenterState.unreadCount,
            now = now, deviceOpener = opener,
            focusReportLabel = when { reportFailed -> "다시 보고"; reportPending -> "보고 대기"; else -> "완료 보고" },
            focusReportMessage = when {
                reportFailed -> "보고 실패 · 서버 연결을 확인하고 다시 보고하세요."
                reportPending -> "보고 대기"
                else -> reportPresentation.message
            }, focusReportEnabled = reportEnabled)
    }
}

@Composable
private fun WorkTicketRoute(state: State<AppNavigationState>, entry: NavBackStackEntry) {
    with(state.value) {
        StatusPollingLifecycleEffect(dashboardViewModel)
        val rowId = entry.arguments?.getString("rowId").orEmpty()
        val deviceId = rememberSaveable(entry.id) { selectedDeviceId.orEmpty() }
        val profileId = rememberSaveable(entry.id) { serverTicketState.profileId.orEmpty() }
        val deviceName = rememberSaveable(entry.id) { selectedRegisteredDeviceName ?: UnnamedDeviceLabel }
        val credential = rememberSaveable(entry.id) { mutableStateOf(serverTicketState.credentialRevision) }.value
        val gson = remember { com.google.gson.Gson() }
        val saved = remember(entry) {
            val json = entry.savedStateHandle.get<String>("work.ticket")
            if (json != null) runCatching { gson.fromJson(json, ServerTicket::class.java) }.getOrNull()
            else serverTicketState.tickets.firstOrNull { it.rowId == rowId }?.also {
                entry.savedStateHandle["work.ticket"] = gson.toJson(it)
            }
        }
        val currentScope = selectedDeviceId.equals(deviceId, true) && directServerState.selectedProfileId == profileId
        val liveList = serverTicketState.takeIf { it.profileId == profileId && it.deviceId.equals(deviceId, true) }
        val ticket = saved
        if (ticket == null || (liveList != null && !liveList.isLoading && !liveList.isCached && liveList.lastSyncedAt != null &&
                liveList.tickets.none { it.rowId == rowId })) {
            WorkMissingScreen(true, { navController.popBackStack() }, { navigateToWorkRoot(navController) })
            return
        }
        var pipelineId by rememberSaveable(entry.id) {
            mutableStateOf(surveyRunState.preparationDraft?.takeIf { it.deviceId.equals(deviceId, true) }?.pipelineId
                ?: surveyRunState.pipelineId?.takeIf { id -> pipelineState.pipelines.any { it.id == id } }
                ?: pipelineState.pipelines.singleOrNull()?.id)
        }
        val pipeline = pipelineState.pipelines.firstOrNull { it.id == pipelineId }
        var choice by rememberSaveable(entry.id) { mutableStateOf(false) }
        var afterChoice by rememberSaveable(entry.id) { mutableStateOf(false) }
        var requested by rememberSaveable(entry.id) { mutableStateOf(false) }
        var requestEpoch by remember { mutableIntStateOf(0) }
        var previousPreflight by remember { mutableStateOf<String?>(null) }
        var preflightLaunched by remember { mutableStateOf(false) }
        var preparationFailure by remember { mutableStateOf<String?>(null) }
        val latestRequested = rememberUpdatedState(requested)
        val snackbars = remember { androidx.compose.material3.SnackbarHostState() }
        val opener = remember { FocusRequester() }
        val target = pipelineId?.let { WorkTicketTarget(deviceId, profileId, rowId, ticket.revision, it,
            ticket.zoneRevision, ticket.zoneHash) }
        val survey = surveyRunState
        val liveTicket = liveList?.tickets?.firstOrNull { it.rowId == rowId }
        val changed = liveTicket != null && (liveTicket.revision != ticket.revision ||
            liveTicket.zoneRevision != ticket.zoneRevision || liveTicket.zoneHash != ticket.zoneHash)
        val activeScope = currentScope && survey.deviceId.equals(deviceId, true) && survey.pipelineId == pipelineId
        LaunchedEffect(deviceId, profileId, rowId, pipelineId, currentScope) {
            if (!currentScope || deviceId.isBlank() || profileId.isBlank()) return@LaunchedEffect
            serverTicketViewModel.prepareTicket(profileId, deviceId, pipelineId, ticket, fieldState.runs) {
                if (!state.value.latestSelectedDeviceId.equals(deviceId, true) || state.value.latestTicketProfileId != profileId) return@prepareTicket
                if (pipeline != null && surveyOpenDecision(surveyRunViewModel.uiState.value, pipeline.id) == SurveyOpenDecision.REQUESTED_PIPELINE) {
                    surveyRunViewModel.open(pipeline.id, pipeline.label)
                }
            }
        }
        LaunchedEffect(currentScope, survey.deviceId, survey.pipelineId, survey.restoringLocalState,
            survey.operation, ticket.revision, pipelineId) {
            if (!activeScope || survey.contextLocked || survey.operation != null || survey.restoringLocalState) return@LaunchedEffect
            val existing = survey.preparationDraft
            val draft = ticketDraft(ticket, deviceId, profileId, credential, pipelineId, existing, System.currentTimeMillis())
            if (draft != existing) surveyRunViewModel.setPreparationDraft(draft)
        }
        // Resolve existing context from reads only. Creating context is owned by the start transaction.
        LaunchedEffect(activeScope, survey.projects, survey.sections, survey.selectedProject, survey.operation, ticketPreparation, survey.restoringLocalState) {
            if (!activeScope || survey.contextLocked || survey.operation != null || survey.restoringLocalState) return@LaunchedEffect
            val preparation = ticketPreparation?.takeIf { it.ticket.rowId == rowId && it.profileId == profileId && it.deviceId.equals(deviceId, true) }
                ?: return@LaunchedEffect
            val project = ticketPreparationProject(preparation, survey.projects)
            if (project != null && survey.selectedProject != project) {
                surveyRunViewModel.selectProject(project)
            } else if (project != null) {
                val section = ticketPreparationSection(preparation, survey.sections)
                if (section != null && survey.selectedSection != section) surveyRunViewModel.selectSection(section)
            }
        }
        LaunchedEffect(currentScope, survey.deviceId) {
            if (!currentScope) {
                val wasOpen = requested || choice
                requested = false; choice = false; afterChoice = false
                if (wasOpen) snackbars.showSnackbar("장치를 바꿔서 열려 있던 확인 창을 닫았습니다.")
            }
        }
        LaunchedEffect(requested, requestEpoch, activeScope, survey.operation, survey.selectedSection, survey.isLoading, survey.restoringLocalState) {
            if (!requested || preflightLaunched || !activeScope || survey.restoringLocalState || survey.isLoading || survey.operation != null) return@LaunchedEffect
            val draft = survey.preparationDraft
            if (survey.canPreflight && !changed && draft?.ticketRowId == rowId &&
                draft.ticketRevision == ticket.revision && draft.serverProfileId == profileId) {
                preflightLaunched = true
                surveyRunViewModel.runPreflight()
            } else {
                preparationFailure = "수집 구간과 수집 설정을 확인한 뒤 다시 점검하세요."
            }
        }
        val beginCheck = {
            preparationFailure = null
            preflightLaunched = false
            previousPreflight = surveyRunViewModel.uiState.value.preflight?.preflightId
            requestEpoch++
            requested = true
        }
        if (choice && currentScope) TaskChoiceSheet(pipelineState, deviceId, deviceName, ticket.title,
            onSelect = { task ->
                    pipelineId = task.id
                    choice = false
                    ticketJourneyDestination(WorkTicketEvent.SELECT_TASK)?.let { navController.navigateSingle(it) }
                    if (afterChoice) { afterChoice = false; beginCheck() }
            }, onDismissRequest = { choice = false; afterChoice = false }, openerFocusRequester = opener)
        val connected = currentScope && fullControlConnected
        val blocked = when {
            !currentScope -> "화면에 표시된 장치를 다시 선택한 뒤 시작하세요."
            survey.pendingStart != null || survey.unconfirmedRunId != null || survey.contextLocked -> "진행 중인 수집과 시작 요청을 먼저 확인하세요."
            survey.restoringLocalState || !survey.runStateConfirmed -> "장치 상태를 확인한 뒤 시작할 수 있습니다."
            else -> null
        }
        val transport = when {
            !connected -> "오프라인"
            deviceDashboardState.transportType == TransportType.WIFI_DIRECT -> "직접 연결"
            else -> "같은 네트워크"
        }
        val readiness = ticketPassiveReadiness(deviceName, deviceDashboardState.status.takeIf { currentScope },
            connected, transport, Instant.now(), blocked)
        val relay by repository.mobileRtkRelayState.collectAsStateWithLifecycle()
        val readinessWithRelay = readiness.copy(remedyLabel = if (!currentScope) "장치 선택" else readiness.remedyLabel,
            relay = if (currentScope && deviceDashboardState.capabilities.mobileRtkRelay) when {
            !connected -> "미확인"
            relay.pipelineId != null && relay.pipelineId != pipelineId -> "꺼짐"
            relay.error != null -> "오류"
            relay.preparing -> "연결하는 중"
            relay.active -> "중계 중"
            else -> "꺼짐"
        } else null)
        Box(Modifier.fillMaxSize()) {
        TicketDetailScreen(ticket, liveList?.role, liveList?.reportMessages?.get(rowId),
            onBack = { navController.popBackStack() },
            onStart = {
                ticketJourneyDestination(WorkTicketEvent.REQUEST_START)?.let { navController.navigateSingle(it) }
                if (pipeline == null) { afterChoice = true; choice = true } else beginCheck()
            },
            readiness = readinessWithRelay, taskLabel = pipeline?.label ?: "수집 작업 선택",
            onSelectTask = { afterChoice = false; choice = true },
            onRemedy = {
                if (currentScope) openConnectionMethods(navController)
                else deviceState.registeredDevices.firstOrNull { it.deviceId.equals(deviceId, true) }?.let(connectRegisteredDevice)
            }, startOpener = opener,
            ongoingRun = { WorkOngoingStrip(state.value, deviceId, deviceName) })
        GeoSnackbar(snackbars, Modifier.align(Alignment.BottomCenter))
        }
        val immutableStart = remember(survey.preflight?.preflightId) { surveyRunViewModel.startTarget() }
        if (requested && currentScope) {
            val checking = preparationFailure == null && (!preflightLaunched || survey.operation in setOf("create_context", "preflight") || survey.isLoading)
            val checkedSurvey = survey.takeUnless { it.preflight?.preflightId == previousPreflight } ?: survey.copy(preflight = null)
            SurveyStartSheet(ticketStartSheet(ticket, deviceName, checkedSurvey, checking, changed,
                preparationFailure ?: survey.error,
                { navController.navigateSingle(Routes.STORAGE) }, { openConnectionMethods(navController) },
                onSensors = { navController.navigateSingle(Routes.DEVICE_DETAIL) }),
                onStart = {
                    val bound = target
                    val start = immutableStart
                    if (bound != null && start != null && start.draft.ticketRowId == bound.rowId &&
                        start.draft.ticketRevision == bound.revision && start.draft.geometryHash == ticket.zoneHash &&
                        start.draft.geometryRevision == ticket.zoneRevision) serverTicketViewModel.usePreparedTicket(profileId, deviceId, bound.pipelineId) {
                        val latest = state.value
                        val latestTicket = latest.serverTicketState.tickets.firstOrNull { it.rowId == rowId }
                        if (latestRequested.value && bound.matches(latest.latestSelectedDeviceId, latest.latestTicketProfileId, latestTicket,
                                surveyRunViewModel.uiState.value.pipelineId) && navController.currentBackStackEntry?.id == entry.id) {
                            surveyRunViewModel.start(start)?.let { pending ->
                                serverTicketViewModel.armPendingSelection(deviceId, bound.pipelineId, pending.clientRequestId)
                                requested = false
                                ticketJourneyDestination(WorkTicketEvent.START_ACCEPTED, accepted = pending)?.let { destination ->
                                    navController.navigateSingle(destination) { popUpTo(entry.destination.id) { inclusive = true } }
                                }
                            }
                        }
                    }
                }, onRecheck = {
                    if (changed) {
                        requested = false
                        serverTicketViewModel.refresh(profileId, deviceId)
                        navController.popBackStack()
                    } else beginCheck()
                }, onDismissRequest = { requested = false }, openerFocusRequester = opener,
                optionalChecks = optionalStartChecks(checkedSurvey))
        }
    }
}

@Composable
private fun WorkTaskRoute(state: State<AppNavigationState>, entry: NavBackStackEntry) {
    with(state.value) {
        val id = entry.arguments?.getString("pipelineId").orEmpty()
        val deviceId = rememberSaveable(entry.id) { selectedDeviceId.orEmpty() }
        val name = rememberSaveable(entry.id) { selectedRegisteredDeviceName ?: UnnamedDeviceLabel }
        val pipeline = remember(entry) { pipelineState.pipelines.firstOrNull { it.id == id } }
        val lastRun = fieldState.runs.firstOrNull {
            fieldState.deviceId.equals(deviceId, true) && it.pipelineId == id &&
                (it.deviceId == null || it.deviceId.equals(deviceId, true))
        }
        val outputRoot = lastRun?.output?.rootId ?: pipeline?.outputRootId
        val outputPath = lastRun?.output?.path ?: pipeline?.outputPath
        if (pipeline == null) WorkMissingScreen(false, { navController.popBackStack() }, { navigateToWorkRoot(navController) })
        else WorkTaskDetailScreen(pipeline,
            ticketPassiveReadiness(name, deviceDashboardState.status.takeIf { selectedDeviceId.equals(deviceId, true) },
                fullControlConnected && selectedDeviceId.equals(deviceId, true),
                when {
                    !fullControlConnected || !selectedDeviceId.equals(deviceId, true) -> "오프라인"
                    deviceDashboardState.transportType == TransportType.WIFI_DIRECT -> "직접 연결"
                    else -> "같은 네트워크"
                }, Instant.now(),
                if (surveyRunState.contextLocked) "진행 중인 수집과 시작 요청을 먼저 확인하세요." else null),
            onBack = { navController.popBackStack() }, onPrepare = {
                if (latestSelectedDeviceId.equals(deviceId, true)) openPipelinePreparation(pipeline)
            }, ongoingRun = { WorkOngoingStrip(state.value, deviceId, name) }, lastRun = lastRun,
            onOpenFiles = if (!outputRoot.isNullOrBlank() && !outputPath.isNullOrBlank()) ({
                if (latestSelectedDeviceId.equals(deviceId, true) && fullControlConnected) {
                    navController.navigateSingle("storage?rootId=${Uri.encode(outputRoot)}&path=${Uri.encode(outputPath)}")
                }
            }) else null)
    }
}

@Composable
private fun WorkOngoingStrip(state: AppNavigationState, deviceId: String, name: String) {
    OngoingRunStatus(state, deviceId, name)
}

internal fun NavGraphBuilder.workGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
    (listOf(Routes.WORK_BROWSE) + if (includeRoot) listOf(Routes.TASKS) else emptyList()).forEach { route ->
        composable(route) {
            if (LocalGeoWindow.current.twoPane) GeoPaneLayout(list = { WorkRootRoute(state) }, detail = {
                GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "수집 구간을 고르세요", "목록에서 수집 구간을 고르면 준비 상태를 볼 수 있습니다."), fullScreen = true)
            }) else WorkRootRoute(state)
        }
    }
    composable(Routes.TICKET_DETAIL, arguments = listOf(navArgument("rowId") { type = NavType.StringType })) { entry ->
        if (LocalGeoWindow.current.twoPane) GeoPaneLayout(list = { WorkRootRoute(state) }, detail = { WorkTicketRoute(state, entry) })
        else WorkTicketRoute(state, entry)
    }
    composable(Routes.TASK_DETAIL, arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })) { entry ->
        WorkTaskRoute(state, entry)
    }

        listOf(Routes.PIPELINES).forEach { taskRoute ->
        composable(taskRoute) taskContent@ { taskEntry ->
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                PipelineListScreen(
                    onHistory = { navController.navigateSingle(Routes.TASK_HISTORY) { launchSingleTop = true } },

                    state = pipelineState.copy(error = ticketPreparation?.let {
                        serverTicketState.reportMessages[it.ticket.rowId]
                    } ?: pipelineState.error),
                    onBack = {
                        selectedDeviceId?.let { serverTicketViewModel.clearPendingSelectionIfUnarmed(it) }
                        navController.popBackStack()
                    },
                    onRefresh = pipelineViewModel::refresh,
                    onAdd = {
                        pipelineViewModel.beginCreate()
                        navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.NEW_PIPELINE.route)
                    },
                    onControl = pipelineViewModel::control,
                    onPrepareRun = { pipeline ->
                        when (surveyOpenDecision(surveyRunState, pipeline.id)) {
                            SurveyOpenDecision.REQUESTED_PIPELINE -> {
                                val open = {
                                    if (latestSelectedDeviceId.equals(selectedDeviceId, true) &&
                                        latestTicketProfileId == directServerState.selectedProfileId &&
                                        navController.currentBackStackEntry?.id == taskEntry.id &&
                                        surveyRunViewModel.uiState.value.deviceId.equals(latestSelectedDeviceId, true) &&
                                        surveyOpenDecision(surveyRunViewModel.uiState.value, pipeline.id) == SurveyOpenDecision.REQUESTED_PIPELINE) {
                                        surveyRunViewModel.open(pipeline.id, pipeline.label)
                                        navController.navigateSingle("survey_run/${Uri.encode(pipeline.id)}")
                                    }
                                }
                                if (ticketPreparation != null) {
                                    serverTicketViewModel.usePreparedTicket(directServerState.selectedProfileId, selectedDeviceId, pipeline.id, open)
                                } else open()
                            }
                            SurveyOpenDecision.CURRENT_ACTIVE ->
                                navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true }
                            SurveyOpenDecision.CURRENT_RESULT -> surveyRunState.latestRun?.let { run ->
                                navController.navigateSingle(runResultRoute(run.runId, run.deviceId, run.pipelineId)) { launchSingleTop = true }
                            }
                            SurveyOpenDecision.WAIT_FOR_RESTORE -> Unit
                        }
                    },
                    onRemove = pipelineViewModel::remove,
                    onLogs = { pipeline ->
                        navController.navigateSingle("dev/pipeline/${Uri.encode(pipeline.id)}/logs")
                    },
                    onConfig = { pipeline ->
                        navController.navigateSingle("dev/pipeline/${Uri.encode(pipeline.id)}/config")
                    },
                    onOutput = { pipeline ->
                        val rootId = pipeline.outputRootId ?: return@PipelineListScreen
                        val path = pipeline.outputPath ?: return@PipelineListScreen
                        navController.navigateSingle(
                            "storage?rootId=${Uri.encode(rootId)}&path=${Uri.encode(path)}"
                        )
                    },
                    onSectionSelected = onSectionSelected,
                    onClearMessage = pipelineViewModel::clearMessage,
                    deviceName = selectedDeviceName,
                    unreadCount = alertCenterState.unreadCount,
                    onAlerts = { navController.navigateSingle(Routes.ALERTS) },
                    onDetails = { navController.navigateSingle("task_detail/${Uri.encode(it.id)}") },
                    detailId = taskEntry.arguments?.getString("pipelineId"),
                    startCapability = dashboardState.capabilities.pipelines && dashboardState.capabilities.mobileTimeSync,
                    developerModeEnabled = false
                )
            }
        }
        }

        composable(
            route = Routes.SURVEY_RUN,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) { entry ->
            with(state.value) {
                val pipelineId = entry.arguments?.getString("pipelineId").orEmpty()
                val pipelineLabel = pipelineState.pipelines.firstOrNull { it.id == pipelineId }?.label ?: pipelineId
                val ticketSelection = selectedDeviceId?.let {
                    serverTicketViewModel.pendingSelection(it, pipelineId)
                }?.takeIf { it.profileId == directServerState.selectedProfileId }
                val preparation = ticketPreparation?.takeIf {
                    it.deviceId.equals(selectedDeviceId, true) && it.pipelineId == pipelineId &&
                        it.profileId == directServerState.selectedProfileId
                }
                LaunchedEffect(selectedDeviceId, pipelineId, pipelineLabel) {
                    surveyRunViewModel.open(pipelineId, pipelineLabel)
                }
                LaunchedEffect(preparation, surveyRunState.projects, surveyRunState.sections,
                    surveyRunState.selectedProject?.surveyProjectId, surveyRunState.operation) {
                    if (surveyRunState.pipelineId != pipelineId || surveyRunState.contextLocked ||
                        surveyRunState.operation != null) return@LaunchedEffect
                    val matchingProject = preparation?.let {
                        com.example.jetsoncontroller.ui.tasks.ticketPreparationProject(it, surveyRunState.projects)
                    }
                    if (matchingProject != null &&
                        matchingProject.surveyProjectId != surveyRunState.selectedProject?.surveyProjectId) {
                        surveyRunViewModel.selectProject(matchingProject)
                        return@LaunchedEffect
                    }
                    val matchingSection = preparation?.let {
                        com.example.jetsoncontroller.ui.tasks.ticketPreparationSection(it, surveyRunState.sections)
                    } ?: ticketSelection?.takeIf { preparation == null }?.let { selection ->
                        surveyRunState.sections.singleOrNull { it.label == selection.title }
                    }
                    if (matchingSection != null &&
                        surveyRunState.selectedSection?.surveySectionId != matchingSection.surveySectionId
                    ) {
                        surveyRunViewModel.selectSection(matchingSection)
                    }
                }
                SurveyRunScreen(
                    state = surveyRunState,
                    onBack = {
                        ticketSelection?.let {
                            serverTicketViewModel.clearPendingSelectionIfUnarmed(it.deviceId, it.pipelineId)
                        }
                        pipelineViewModel.refresh()
                        navController.popBackStack()
                    },
                    onRefresh = surveyRunViewModel::refresh,
                    onSelectProject = surveyRunViewModel::selectProject,
                    onSelectSection = surveyRunViewModel::selectSection,
                    onCreateProject = surveyRunViewModel::createProject,
                    onCreateSection = surveyRunViewModel::createSection,
                    onSensorRequirement = surveyRunViewModel::setSensorRequirement,
                    onMinFreeBytes = surveyRunViewModel::setMinFreeBytes,
                    onOutputMinFiles = surveyRunViewModel::setOutputMinFiles,
                    onOutputMinBytes = surveyRunViewModel::setOutputMinBytes,
                    onOutputPatterns = surveyRunViewModel::setOutputPatterns,
                    onOutputRoot = surveyRunViewModel::setOutputRoot,
                    onOutputPath = surveyRunViewModel::setOutputPath,
                    onSavePolicy = surveyRunViewModel::savePolicy,
                    onPreflight = surveyRunViewModel::runPreflight,
                    // 시작 직후 갈 곳이 있다는 것이 이번 재편의 핵심입니다. 시작 요청의 결과가
                    // 아직 없어도 수집 중 화면으로 보냅니다 — 그 화면이 '결과 대기' 를 정직하게
                    // 표시하므로, 목록으로 돌려보내 같은 버튼을 다시 누르게 하는 것보다 안전합니다.
                    onStart = {
                        val expectedPreflightId = surveyRunViewModel.uiState.value.preflight?.preflightId
                        val start = {
                            val current = surveyRunViewModel.uiState.value
                            if (current.deviceId.equals(latestSelectedDeviceId, true) &&
                                latestSelectedDeviceId.equals(selectedDeviceId, true) &&
                                latestTicketProfileId == directServerState.selectedProfileId &&
                                navController.currentBackStackEntry?.id == entry.id &&
                                current.pipelineId == pipelineId && current.preflight?.preflightId == expectedPreflightId &&
                                (preparation == null || com.example.jetsoncontroller.ui.tasks.ticketPreparationContextMatches(
                                    preparation, current.selectedProject, current.selectedSection))) {
                                val pendingStart = surveyRunViewModel.start()
                                if (pendingStart != null) {
                                    ticketSelection?.let {
                                        serverTicketViewModel.armPendingSelection(it.deviceId, it.pipelineId, pendingStart.clientRequestId)
                                    }
                                    navController.navigateSingle(Routes.ACTIVE_RUN) {
                                        popUpTo(entry.destination.id) { inclusive = true }
                                    }
                                }
                            }
                        }
                        if (preparation != null) {
                            serverTicketViewModel.usePreparedTicket(directServerState.selectedProfileId, selectedDeviceId, pipelineId, start)
                        } else if (ticketPreparation == null) start()
                    },
                    onRetryPendingStart = surveyRunViewModel::retryPendingStart,
                    onDismissMessage = surveyRunViewModel::clearMessage,
                    developerModeEnabled = developerModeEnabled,
                    deviceName = selectedRegisteredDeviceName,
                    ticketTitle = ticketSelection?.title,
                    ticketPreparationKey = preparation?.let { "${it.profileId}:${it.ticket.rowId}:${it.ticket.revision}" },
                    ticketPreparationReady = ticketPreparation == null || preparation?.let {
                        com.example.jetsoncontroller.ui.tasks.ticketPreparationContextMatches(
                            it, surveyRunState.selectedProject, surveyRunState.selectedSection)
                    } == true,
                    ticketMessage = when {
                        preparation != null -> serverTicketState.reportMessages[preparation.ticket.rowId]
                            ?: if (preparation.surveySectionId == null) "티켓에 연결된 구간 식별 정보가 없어 제목으로 구간을 찾습니다." else null
                        ticketPreparation != null -> "선택한 장치와 서버에서 티켓을 다시 선택하세요."
                        ticketSelection != null -> "티켓에 연결된 구간 식별 정보가 없어 제목으로 구간을 찾습니다."
                        else -> null
                    },
                    onDeviceFiles = { navController.navigateSingle(Routes.STORAGE) },
                    onConnectionMethod = { openConnectionMethods(navController) },
                    onDeviceDetails = { navController.navigateSingle(Routes.DEVICE_DETAIL) }
                )
            }
        }

        listOf(Routes.RUN_RESULT, Routes.HISTORY_RESULT).forEach { resultRoute ->
        composable(
            resultRoute,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType },
                navArgument("historyId") {
                    type = NavType.StringType
                    if (resultRoute == Routes.RUN_RESULT) { nullable = true; defaultValue = null }
                }
            ) + if (resultRoute == Routes.RUN_RESULT) listOf(
                navArgument("runId") { type = NavType.StringType },
                navArgument("pipelineId") { type = NavType.StringType; nullable = true; defaultValue = null }
            ) else emptyList()
        ) { entry ->
            with(state.value) {
                val resultDeviceId = entry.arguments?.getString("deviceId").orEmpty()
                val historyId = entry.arguments?.getString("historyId")
                val savedHistory = remember(entry) {
                    entry.savedStateHandle.get<String>("resultHistory")?.let { json ->
                        runCatching { com.google.gson.Gson().fromJson(json, com.example.jetsoncontroller.model.TaskRun::class.java) }.getOrNull()
                    }
                }
                val history = (fieldState.runs.firstOrNull {
                    fieldState.deviceId.equals(resultDeviceId, true) && it.id == historyId
                } ?: savedHistory)?.takeIf {
                    it.id == historyId && (it.deviceId == null || it.deviceId.equals(resultDeviceId, true)) &&
                        (it.contextSnapshot == null || it.contextSnapshot.deviceId.equals(resultDeviceId, true))
                }
                val target = entry.arguments?.getString("runId")?.let {
                    com.example.jetsoncontroller.ui.survey.RunResultTarget(resultDeviceId, it, entry.arguments?.getString("pipelineId"))
                }
                LaunchedEffect(target, surveyRunState.deviceId, surveyRunState.online) {
                    target?.let { surveyRunViewModel.openResult(it, surveyRunState.latestRun) }
                }
                val resultRun = runResultState.takeIf { it.target == target && target != null }?.run
                LaunchedEffect(resultRun?.runId, resultRun?.active, surveyRunState.deviceId, surveyRunState.latestRun?.runId) {
                    resultRun?.takeIf { !it.active && it.deviceId.equals(surveyRunState.deviceId, true) }?.let {
                        surveyRunViewModel.acknowledgeResult(it.runId)
                    }
                }
                DisposableEffect(entry) {
                    surveyRunViewModel.dismissResultLog()
                    onDispose { surveyRunViewModel.dismissResultLog() }
                }
                val logEntry = resultRun?.let(::resultLogEntry) ?: history
                val logTarget = logEntry?.let {
                    com.example.jetsoncontroller.ui.survey.RunLogTarget(resultDeviceId, it.pipelineId, it.logId)
                }
                val resultOnline = deviceDashboardState.isOnline && selectedDeviceId.equals(resultDeviceId, true)
                val resultJobs = (if (uploadState.deviceId.equals(resultDeviceId, true))
                    listOfNotNull(uploadState.currentJob) + uploadState.queue
                    else uploadState.otherDeviceQueues.entries.firstOrNull { it.key.equals(resultDeviceId, true) }?.value.orEmpty())
                    .distinctBy { it.id }
                val receiptPresentation = serverReceiptPresentation(resultRun, resultJobs)
                val ticketLink = resultRun?.let { serverTicketViewModel.linkForRun(it.runId, it.deviceId) }
                LaunchedEffect(ticketLink?.profileId) {
                    ticketLink?.profileId?.let(serverTicketViewModel::refreshCapabilities)
                }
                val ticketCompletion = com.example.jetsoncontroller.ui.tasks.ticketCompletionPresentation(
                    attached = ticketLink != null,
                    serverReceiptConfirmed = receiptPresentation.tone == Tone.SUCCESS,
                    role = ticketLink?.profileId?.let(serverTicketViewModel::reportRole),
                    conflicted = resultRun?.let { serverTicketViewModel.ticketConflictForRun(it.runId) } == true,
                    startRejected = resultRun?.let { serverTicketViewModel.ticketStartRejected(it.runId) } == true,
                    startRejectedMessage = resultRun?.let { serverTicketViewModel.ticketStartFailureMessage(it.runId) }
                )
                val delivery = resultRun?.let { serverTicketViewModel.reportDeliveryState(it.runId, true) }
                val completionReportPending = delivery == TicketReportDeliveryState.PENDING
                val completionReportFailed = delivery == TicketReportDeliveryState.FAILED
                val completionReportSettled = delivery == TicketReportDeliveryState.SENT
                val completionReportEnabled = ticketCompletion.enabled &&
                    delivery !in setOf(TicketReportDeliveryState.PENDING, TicketReportDeliveryState.SENT, TicketReportDeliveryState.REJECTED)
                val ticketCompletionMessage = when {
                    completionReportFailed -> "보고 실패 · 서버 연결을 확인하고 다시 보고하세요."
                    completionReportPending -> "보고 대기"
                    completionReportSettled -> ticketLink?.rowId?.let(serverTicketViewModel::ticketMessage)
                        ?: "완료 보고를 전송했습니다."
                    else -> ticketLink?.rowId?.let(serverTicketViewModel::ticketMessage) ?: ticketCompletion.message
                }
                val boundGeometry by androidx.compose.runtime.produceState<com.example.jetsoncontroller.ui.map.GeoMapGeometry?>(null, resultDeviceId, target?.runId) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.jetsoncontroller.ui.map.GeoMapRunStore(fieldContext.noBackupFilesDir).read(resultDeviceId, target?.runId)
                    }
                }
                val geometryChanged = boundGeometry?.let { geometry -> serverTicketState.tickets.any {
                    it.rowId == geometry.ticketRowId && (it.zoneRevision != geometry.revision || it.zoneHash != geometry.hash)
                } } == true
                com.example.jetsoncontroller.ui.field.RunResultScreen(
                    deviceName = deviceState.registeredDevices.firstOrNull {
                        it.deviceId.equals(resultDeviceId, true)
                    }?.deviceName ?: "선택한 장치",
                    connectionLabel = workConnectionLabel(resultOnline, deviceDashboardState.transportType),
                    connectionTone = com.example.jetsoncontroller.ui.connection
                        .deviceConnectionUi(resultOnline, deviceDashboardState.transportType.takeIf { resultOnline }).tone,
                    run = resultRun,
                    zoneRevisionChanged = geometryChanged,
                    uploadEnabled = serverUploadEnabled && resultOnline,
                    uploadDisabledReason = serverUploadDisabledReason,
                    serverReceiptLabel = receiptPresentation.label,
                    serverReceiptTone = receiptPresentation.tone,
                    ticketTitle = ticketLink?.title,
                    ticketCompletionEnabled = completionReportEnabled,
                    ticketCompletionMessage = ticketCompletionMessage,
                    ticketCompletionPending = completionReportPending,
                    ticketCompletionReported = completionReportSettled,
                    ticketCompletionFailed = completionReportFailed,
                    ticketReportTargetKey = ticketLink?.let {
                        listOf(it.profileId, it.deviceId, it.rowId, it.ticketRevision, it.runId).toString()
                    },
                    onReportTicketCompletion = {
                        if (receiptPresentation.tone == Tone.SUCCESS && completionReportEnabled) {
                            resultRun?.let {
                                if (completionReportFailed) serverTicketViewModel.retryReport(it.runId, true)
                                else serverTicketViewModel.reportCompletion(it)
                            }
                        }
                    },
                    uploadJobs = resultJobs,
                    onOpenTransfer = { id -> resultJobs.firstOrNull { it.id == id }?.let { openTransferDetail(resultDeviceId, it) } },
                    onVerifyReceipt = { id ->
                        resultJobs.firstOrNull { it.id == id }?.let {
                            if (resultOnline && serverUploadEnabled) uploadViewModel.mutateTransfer(
                                com.example.jetsoncontroller.ui.transfers.transferActionTarget(resultDeviceId, it),
                                com.example.jetsoncontroller.ui.transfers.TransferMutation.VERIFY)
                            else openTransferDetail(resultDeviceId, it)
                        }
                    },
                    onDeviceWifi = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                    onConnectionMethods = { openConnectionMethods(navController) },
                    onOpenHistory = { navController.navigateSingle(Routes.TASK_HISTORY) },
                    onTrashRecord = logEntry?.let { record -> ({ if (resultOnline) fieldToolsViewModel.deleteRun(record) }) },
                    historyState = fieldState.takeIf { it.deviceId.equals(resultDeviceId, true) },
                    onUndoDelete = { fieldState.undoTarget?.takeIf { it.deviceId.equals(resultDeviceId, true) }
                        ?.let(fieldToolsViewModel::undoDeleteRun) },
                    onDismissMessage = fieldToolsViewModel::dismissMessage,
                    statusStrip = { OngoingRunStatus(state.value, resultDeviceId,
                        deviceState.registeredDevices.firstOrNull { it.deviceId.equals(resultDeviceId, true) }?.deviceName ?: "선택한 장치") },
                    online = resultOnline,
                    developerModeEnabled = developerModeEnabled,
                    unreadCount = alertCenterState.unreadCount,
                    onDevices = { navigateToDeviceRoot(navController) },
                    onAlerts = { navController.navigateSingle(Routes.ALERTS) },
                    onBack = {
                        val returnRoute = resultReturnRoute(navController.previousBackStackEntry?.destination?.route)
                        if (!navController.popBackStack()) navigateToTab(navController, returnRoute)
                    },
                    onRefresh = {
                        target?.let { surveyRunViewModel.openResult(it) }
                        if (resultOnline) fieldToolsViewModel.refresh(retainLoaded = true)
                    },
                    onPrepareUpload = {
                        resultRun?.takeIf {
                            resultOnline && com.example.jetsoncontroller.ui.field.runOutputStored(it)
                        }?.let { run ->
                            navController.navigateSingle(
                                "upload_confirm/${Uri.encode(run.output.rootId)}" +
                                    "?path=${Uri.encode(run.output.path)}&runId=${Uri.encode(run.runId)}"
                            )
                        }
                    },
                    onOpenFiles = {
                        val output = resultRun?.output ?: history?.output
                        output?.takeIf { resultOnline }?.let {
                            navController.navigateSingle(
                                "storage?rootId=${Uri.encode(it.rootId)}" +
                                    "&path=${Uri.encode(it.path)}"
                            )
                        }
                    },
                    onNewSurvey = {
                        if (latestSelectedDeviceId.equals(resultDeviceId, true)) {
                            serverTicketViewModel.clearPendingSelectionIfUnarmed(resultDeviceId)
                        }
                        navigateToWorkRoot(navController)
                    },
                    historyEntry = history,
                    resultMessage = runResultState.takeIf { target != null && it.target == target }?.error
                        ?: resultLogState.takeIf { it.target == logTarget }?.error
                        ?: fieldState.error.takeIf { fieldState.deviceId.equals(resultDeviceId, true) }
                        ?: uploadState.error.takeIf { uploadState.deviceId.equals(resultDeviceId, true) },
                    onOpenLog = if (developerModeEnabled && logTarget != null) ({
                        if (resultOnline) surveyRunViewModel.openResultLog(logTarget)
                    }) else null,
                    savedLog = resultLogState.takeIf { developerModeEnabled && it.target == logTarget }?.text,
                    onDismissLog = surveyRunViewModel::dismissResultLog
                )
            }
        }
        }

        composable(Routes.TASK_HISTORY) {
            with(state.value) {
                LaunchedEffect(Unit) { fieldToolsViewModel.refresh() }
                com.example.jetsoncontroller.ui.field.GeoRunDashboard(
                    state = fieldState, pipelines = pipelineState.pipelines,
                    onBack = { navController.popBackStack() }, onNew = { navigateToTaskStart(navController) },
                    onRefresh = { fieldToolsViewModel.refresh() }, onMore = { fieldToolsViewModel.refresh(true) },
                    onLog = fieldToolsViewModel::openLog,
                    onOpenResult = { openWorkHistoryResult(it) },
                    onRoute = {
                        requestedMapHistoryEntryId = it.id
                        requestedMapActiveRunId = null
                        navController.navigateSingle(Routes.GNSS_MAP)
                    },
                    onDismissLog = fieldToolsViewModel::dismissLog,
                    onPipeline = { navController.navigateSingle("dev/pipeline/${Uri.encode(it.id)}") },
                    onDeleteRun = fieldToolsViewModel::deleteRun,
                    onUndoDelete = fieldToolsViewModel::undoDeleteRun,
                    onTrash = { navController.navigateSingle(Routes.LOCAL_TRASH) },
                    onUploadOutput = { run ->
                        val output = run.output
                        val runId = com.example.jetsoncontroller.ui.field.canonicalHistoryRunId(run)
                        if (output != null && runId != null) {
                            navController.navigateSingle(
                                "upload_confirm/${Uri.encode(output.rootId)}?path=${Uri.encode(output.path)}&runId=${Uri.encode(runId)}"
                            )
                        }
                    },
                    onDismissMessage = fieldToolsViewModel::dismissMessage,
                    developerModeEnabled = developerModeEnabled,
                    uploadJobs = (listOfNotNull(uploadState.currentJob) + uploadState.queue)
                        .takeIf { uploadState.deviceId.equals(fieldState.deviceId, true) }.orEmpty().distinctBy { it.id },
                    statusStrip = { OngoingRunStatus(state.value) }
                )
            }
        }

}
