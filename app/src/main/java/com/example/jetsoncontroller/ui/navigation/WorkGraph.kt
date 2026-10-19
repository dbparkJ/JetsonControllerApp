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
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.survey.SurveyRunScreen
import kotlinx.coroutines.delay

internal fun NavGraphBuilder.workGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
    (listOf(Routes.WORK_BROWSE) + if (includeRoot) listOf(Routes.TASKS) else emptyList()).forEach { rootRoute ->
        composable(rootRoute) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                LaunchedEffect(selectedDeviceId, fullControlConnected) {
                    if (fullControlConnected) pipelineViewModel.refresh()
                }
                val taskProfileId = directServerState.selectedProfileId?.takeIf { id ->
                    directServerState.profiles.any { it.profile.profileId == id }
                }
                val taskCredentialRevision = directServerState.profiles.firstOrNull {
                    it.profile.profileId == taskProfileId
                }?.credentialRevision
                val taskLifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(currentRoute, taskProfileId, taskCredentialRevision, selectedDeviceId, taskLifecycleOwner) {
                    val profileId = taskProfileId
                    val deviceId = selectedDeviceId?.takeIf(String::isNotBlank)
                    if (currentRoute in setOf(Routes.TASKS, Routes.WORK_BROWSE) && profileId != null && deviceId != null) {
                        taskLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            serverTicketViewModel.refresh(profileId, deviceId)
                            while (true) {
                                delay(60_000L)
                                serverTicketViewModel.refresh(profileId, deviceId)
                            }
                        }
                    }
                }
                val tasksState = com.example.jetsoncontroller.ui.tasks.buildTasksHomeUiState(
                    com.example.jetsoncontroller.ui.tasks.TasksHomeInput(
                        deviceName = selectedRegisteredDeviceName,
                        connected = fullControlConnected,
                        survey = surveyRunState,
                        runs = fieldState.runs,
                        uploadJobs = uploadState.queue,
                        pipelines = pipelineState.pipelines,
                        developerModeEnabled = developerModeEnabled
                    )
                ).copy(ticketSection = serverTicketState.takeIf {
                    taskProfileId != null && selectedDeviceId?.isNotBlank() == true &&
                        it.profileId == taskProfileId && it.deviceId.equals(selectedDeviceId, true)
                })
                androidx.compose.material3.Scaffold(
                    containerColor = com.example.jetsoncontroller.ui.theme.LocalGeoColors.current.canvas,
                    bottomBar = { ControlNavigationBar(ControlSection.TASKS, onSectionSelected) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        com.example.jetsoncontroller.ui.tasks.TasksHomeScreen(
                            state = tasksState,
                            onSelectDevice = { navigateToDeviceRoot(navController) },
                            onStartSurvey = openSurveyPreparation,
                            onChangeSection = openSurveyPreparation,
                            onOpenActiveRun = {
                                navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true }
                            },
                            onOpenRun = { historyId ->
                                fieldState.runs.firstOrNull { it.id == historyId }?.let(openHistoryResult)
                            },
                            onOpenHistory = {
                                navController.navigateSingle(Routes.TASK_HISTORY) { launchSingleTop = true }
                            },
                            onOpenTask = { pipelineId ->
                                navController.navigateSingle("task_detail/${Uri.encode(pipelineId)}") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenTicket = { rowId ->
                                navController.navigateSingle("ticket_detail/${Uri.encode(rowId)}") {
                                    launchSingleTop = true
                                }
                            },
                            onSelectTicketUnit = serverTicketViewModel::selectUnit
                        )
                    }
                }
            }
        }
    }
        composable(
            route = Routes.TICKET_DETAIL,
            arguments = listOf(navArgument("rowId") { type = NavType.StringType })
        ) { entry ->
            with(state.value) {
                val rowId = entry.arguments?.getString("rowId").orEmpty()
                val ticket = serverTicketState.tickets.firstOrNull { it.rowId == rowId }
                if (ticket == null) {
                    Box(Modifier.fillMaxSize().background(com.example.jetsoncontroller.ui.theme.LocalGeoColors.current.canvas)) {
                        com.example.jetsoncontroller.ui.components.GeoStateBlock(
                            title = "수집 구간을 찾을 수 없습니다",
                            impact = "작업 탭에서 티켓 목록을 새로고침해 주세요.",
                            tone = StatusTone.UNKNOWN,
                            primaryLabel = "작업으로 돌아가기",
                            onPrimary = { navController.popBackStack() }
                        )
                    }
                } else {
                    com.example.jetsoncontroller.ui.tasks.TicketDetailScreen(
                        ticket = ticket,
                        role = serverTicketState.role,
                        reportMessage = serverTicketViewModel.ticketMessage(ticket.rowId),
                        onBack = { navController.popBackStack() },
                        onStart = startTicket@ {
                            val profileId = serverTicketState.profileId
                            val deviceId = selectedDeviceId?.takeIf(String::isNotBlank)
                            if (profileId != null && deviceId != null) {
                                val pipeline = surveyRunState.pipelineId?.let { id ->
                                    pipelineState.pipelines.firstOrNull { it.id == id }
                                } ?: pipelineState.pipelines.singleOrNull()
                                when (surveyOpenDecision(surveyRunViewModel.uiState.value, pipeline?.id.orEmpty())) {
                                    SurveyOpenDecision.CURRENT_ACTIVE -> {
                                        navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true }
                                        return@startTicket
                                    }
                                    SurveyOpenDecision.CURRENT_RESULT -> {
                                        surveyRunViewModel.uiState.value.latestRun?.let { run ->
                                            navController.navigateSingle(runResultRoute(run.runId, run.deviceId, run.pipelineId)) { launchSingleTop = true }
                                        }
                                        return@startTicket
                                    }
                                    SurveyOpenDecision.WAIT_FOR_RESTORE -> return@startTicket
                                    SurveyOpenDecision.REQUESTED_PIPELINE -> Unit
                                }
                                serverTicketViewModel.prepareTicket(profileId, deviceId, pipeline?.id, ticket, fieldState.runs) {
                                    if (!latestSelectedDeviceId.equals(deviceId, true) || latestTicketProfileId != profileId ||
                                        navController.currentBackStackEntry?.id != entry.id) return@prepareTicket
                                    if (pipeline == null) {
                                        navController.navigateSingle(Routes.PIPELINES) { launchSingleTop = true }
                                    } else {
                                        when (surveyOpenDecision(surveyRunViewModel.uiState.value, pipeline.id)) {
                                            SurveyOpenDecision.REQUESTED_PIPELINE -> {
                                                surveyRunViewModel.open(pipeline.id, pipeline.label)
                                                navController.navigateSingle("survey_run/${Uri.encode(pipeline.id)}") { launchSingleTop = true }
                                            }
                                            SurveyOpenDecision.CURRENT_ACTIVE -> navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true }
                                            SurveyOpenDecision.CURRENT_RESULT -> surveyRunState.latestRun?.let { run ->
                                                navController.navigateSingle(runResultRoute(run.runId, run.deviceId, run.pipelineId)) { launchSingleTop = true }
                                            }
                                            SurveyOpenDecision.WAIT_FOR_RESTORE -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) { entry ->
            with(state.value) {
                val pipelineId = entry.arguments?.getString("pipelineId").orEmpty()
                val pipeline = pipelineState.pipelines.firstOrNull { it.id == pipelineId }
                if (pipeline == null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(com.example.jetsoncontroller.ui.theme.LocalGeoColors.current.canvas)
                    ) {
                        com.example.jetsoncontroller.ui.components.GeoStateBlock(
                            title = "작업 정보를 찾을 수 없습니다",
                            impact = "장치에 연결된 상태에서 작업 목록을 다시 열어 보세요.",
                            tone = com.example.jetsoncontroller.ui.components.StatusTone.UNKNOWN,
                            primaryLabel = "작업으로 돌아가기",
                            onPrimary = { navController.popBackStack() }
                        )
                    }
                } else {
                    com.example.jetsoncontroller.ui.tasks.TaskDetailScreen(
                        pipeline = pipeline,
                        connected = fullControlConnected,
                        developerModeEnabled = developerModeEnabled,
                        onBack = { navController.popBackStack() },
                        onOpenLogs = { navController.navigateSingle("pipeline_logs/${Uri.encode(pipeline.id)}") },
                        onOpenConfig = { navController.navigateSingle("pipeline_config/${Uri.encode(pipeline.id)}") }
                    )
                }
            }
        }

        listOf(Routes.PIPELINES, Routes.PIPELINE_DETAIL).forEach { taskRoute ->
        composable(taskRoute) taskContent@ { taskEntry ->
            with(state.value) {
                if (taskRoute == Routes.PIPELINE_DETAIL && !developerModeEnabled) return@taskContent
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
                        navController.navigateSingle(Routes.PIPELINE_EDITOR)
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
                        navController.navigateSingle("pipeline_logs/${Uri.encode(pipeline.id)}")
                    },
                    onConfig = { pipeline ->
                        navController.navigateSingle("pipeline_config/${Uri.encode(pipeline.id)}")
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
                    onDetails = { navController.navigateSingle("pipeline_detail/${Uri.encode(it.id)}") },
                    detailId = taskEntry.arguments?.getString("pipelineId"),
                    startCapability = dashboardState.capabilities.pipelines && dashboardState.capabilities.mobileTimeSync,
                    developerModeEnabled = developerModeEnabled
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
                                if (ticketSelection != null && pendingStart != null) {
                                    serverTicketViewModel.armPendingSelection(
                                        ticketSelection.deviceId, ticketSelection.pipelineId, pendingStart.clientRequestId
                                    )
                                }
                                navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true }
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
                    }
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
                val receiptPresentation = serverReceiptPresentation(
                    resultRun,
                    (listOfNotNull(uploadState.currentJob) + uploadState.queue).distinctBy { it.id }
                )
                val ticketLink = remember(resultRun?.runId, serverTicketState.profileId, serverTicketState.tickets) {
                    resultRun?.let { serverTicketViewModel.linkForRun(it.runId, it.deviceId) }
                }
                LaunchedEffect(ticketLink?.profileId) {
                    ticketLink?.profileId?.let(serverTicketViewModel::refreshCapabilities)
                }
                val ticketCompletion = com.example.jetsoncontroller.ui.tasks.ticketCompletionPresentation(
                    attached = ticketLink != null,
                    serverReceiptConfirmed = receiptPresentation.tone == StatusTone.SUCCESS,
                    role = ticketLink?.profileId?.let(serverTicketViewModel::reportRole),
                    conflicted = resultRun?.let { serverTicketViewModel.ticketConflictForRun(it.runId) } == true,
                    startRejected = resultRun?.let { serverTicketViewModel.ticketStartRejected(it.runId) } == true,
                    startRejectedMessage = resultRun?.let { serverTicketViewModel.ticketStartFailureMessage(it.runId) }
                )
                val completionReportPending = resultRun?.let { serverTicketViewModel.pendingReport(it.runId, true) } == true
                val completionReportSettled = resultRun?.let { serverTicketViewModel.reportSettled(it.runId, true) } == true
                val ticketCompletionMessage = when {
                    completionReportPending -> ticketCompletion.message
                    completionReportSettled -> ticketLink?.rowId?.let(serverTicketViewModel::ticketMessage)
                        ?: "완료 보고를 전송했습니다."
                    else -> ticketCompletion.message
                }
                com.example.jetsoncontroller.ui.field.RunResultScreen(
                    deviceName = deviceState.registeredDevices.firstOrNull {
                        it.deviceId.equals(resultDeviceId, true)
                    }?.deviceName ?: "선택한 장치",
                    connectionLabel = com.example.jetsoncontroller.ui.connection
                        .deviceConnectionUi(deviceDashboardState.isOnline, deviceDashboardState.transportType).label,
                    connectionTone = com.example.jetsoncontroller.ui.connection
                        .deviceConnectionUi(deviceDashboardState.isOnline, deviceDashboardState.transportType).tone,
                    run = resultRun,
                    uploadEnabled = serverUploadEnabled && resultOnline,
                    uploadDisabledReason = serverUploadDisabledReason,
                    serverReceiptLabel = receiptPresentation.label,
                    serverReceiptTone = receiptPresentation.tone,
                    ticketTitle = ticketLink?.title,
                    ticketCompletionEnabled = ticketCompletion.enabled && !completionReportPending && !completionReportSettled,
                    ticketCompletionMessage = ticketCompletionMessage,
                    ticketCompletionPending = completionReportPending,
                    onReportTicketCompletion = {
                        if (receiptPresentation.tone == StatusTone.SUCCESS) {
                            resultRun?.let(serverTicketViewModel::reportCompletion)
                        }
                    },
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
                        resultRun?.takeIf { resultOnline }?.let { run ->
                            navController.navigateSingle(
                                "storage?rootId=${Uri.encode(run.output.rootId)}" +
                                    "&path=${Uri.encode(run.output.path)}"
                            )
                        }
                    },
                    onNewSurvey = {
                        if (latestSelectedDeviceId.equals(resultDeviceId, true)) {
                            serverTicketViewModel.clearPendingSelectionIfUnarmed(resultDeviceId)
                            val pipelineId = resultRun?.pipelineId ?: history?.pipelineId
                            val pipeline = pipelineState.pipelines.firstOrNull { it.id == pipelineId }
                            if (pipeline == null) navigateToTaskStart(navController)
                            else openPipelinePreparation(pipeline)
                        } else navigateToDeviceRoot(navController)
                    },
                    historyEntry = history,
                    resultMessage = runResultState.takeIf { target != null && it.target == target }?.error
                        ?: resultLogState.takeIf { it.target == logTarget }?.error
                        ?: fieldState.error.takeIf { fieldState.deviceId.equals(resultDeviceId, true) },
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
                    onOpenResult = openHistoryResult,
                    onRoute = {
                        requestedMapHistoryEntryId = it.id
                        requestedMapActiveRunId = null
                        navController.navigateSingle(Routes.GNSS_MAP)
                    },
                    onDismissLog = fieldToolsViewModel::dismissLog,
                    onPipeline = { navController.navigateSingle("pipeline_detail/${Uri.encode(it.id)}") },
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
                    developerModeEnabled = developerModeEnabled
                )
            }
        }

}
