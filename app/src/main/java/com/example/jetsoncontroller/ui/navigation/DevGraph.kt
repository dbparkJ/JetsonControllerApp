package com.example.jetsoncontroller.ui.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.diagnostics.ConnectionDiagnosticsScreen
import com.example.jetsoncontroller.ui.field.DeveloperScreen
import com.example.jetsoncontroller.ui.field.OngoingRunStrip
import com.example.jetsoncontroller.ui.pipelines.*
import com.example.jetsoncontroller.ui.storage.*
import com.example.jetsoncontroller.ui.upload.*

/** Canonical developer destinations; daily callers enter these routes directly. */
internal fun NavGraphBuilder.devGraph(state: State<AppNavigationState>) {
    devDestination(DevDestination.HUB.route, state) { app, _ ->
        with(app) {
            DeveloperScreen(fieldState, { navController.popBackStack() }, fieldToolsViewModel::execute,
                { navController.navigateSingle(DevDestination.PIPELINES.route) }, { navController.navigateSingle(DevDestination.DIAGNOSTICS.route) },
                developerModeEnabled, developerModePreference::updateEnabled,
                { navigateToSettingsRoot(navController) }, onOpenTool = { route ->
                    if (route == DevDestination.NEW_PIPELINE.route) pipelineViewModel.beginCreate()
                    navController.navigateSingle(route)
                })
        }
    }
    devDestination(DevDestination.TERMINAL.route, state, device = true) { app, _ ->
        DevTerminalScreen(app.fieldState, app.fieldToolsViewModel::execute, { app.navController.popBackStack() }, app.selectedDeviceName)
    }
    devDestination(DevDestination.FAN.route, state, device = true) { app, _ ->
        StatusPollingLifecycleEffect(app.dashboardViewModel)
        LaunchedEffect(app.selectedDeviceId, app.fullControlConnected, app.deviceDashboardState.capabilities.fanControl) {
            if (app.fullControlConnected && app.deviceDashboardState.capabilities.fanControl) app.dashboardViewModel.refreshFan()
        }
        DevFanScreen(app.deviceDashboardState, { app.navController.popBackStack() }, app.dashboardViewModel::refreshFan,
            app.dashboardViewModel::setFanAuto, app.dashboardViewModel::setFanManual, app.selectedDeviceId)
    }
    devDestination(DevDestination.NETWORK.route, state, device = true) { app, _ ->
        DevNetworkScreen(app.fieldState, app.fieldToolsViewModel::refreshNetwork, { app.navController.popBackStack() }, app.selectedDeviceName)
    }
    devDestination(DevDestination.SENSOR_TEST.route, state, device = true) { app, _ ->
        DevSensorTestScreen(app.fieldState, app.fieldToolsViewModel::testSensor,
            { app.navController.navigateSingle(Routes.CAMERA_PREVIEW) }, { app.navController.navigateSingle(Routes.GNSS_MAP) },
            { app.navController.popBackStack() }, app.selectedDeviceName)
    }
    devDestination(DevDestination.DIAGNOSTICS.route, state) { app, _ -> ConnectionDiagnosticsScreen { app.navController.popBackStack() } }
    devDestination(DevDestination.UPLOAD_TARGETS.route, state, device = true) { app, _ ->
        with(app) {
            LaunchedEffect(selectedDeviceId, fullControlConnected) { if (fullControlConnected) uploadViewModel.refreshTargets() }
            UploadTargetSettingsScreen(uploadState.targets, uploadState.isLoading || uploadState.isSavingTarget,
                uploadState.message, uploadState.error, uploadViewModel::saveTarget, uploadViewModel::deleteTarget,
                uploadViewModel::refreshTargets, uploadViewModel::clearFeedback, { navController.popBackStack() },
                fullControlConnected, selectedDeviceId, onOpenEditor = { target ->
                    navController.navigateSingle("dev/upload-targets/edit?targetId=${Uri.encode(target?.id.orEmpty())}")
                }, deviceName = selectedDeviceName)
        }
    }
    devDestination(DevDestination.UPLOAD_EDITOR.route, state, device = true,
        arguments = listOf(navArgument("targetId") { defaultValue = "" })) { app, entry ->
        with(app) {
            val targetId = entry.arguments?.getString("targetId").orEmpty()
            val target = uploadState.targets.firstOrNull { it.id == targetId }
            if (targetId.isNotEmpty() && target == null) DevPage("업로드 서버 수정", { navController.popBackStack() }) {
                DevEmpty("서버를 찾을 수 없습니다", "업로드 서버 목록을 다시 확인하세요.")
            } else UploadTargetEditorScreen(target, uploadState.isSavingTarget, fullControlConnected, selectedDeviceId,
                selectedDeviceName, uploadState.message, uploadState.error, uploadViewModel::saveTarget,
                uploadViewModel::deleteTarget, { navController.popBackStack() })
        }
    }
    devDestination(DevDestination.PROFILES.route, state) { app, _ ->
        DevServerProfilesScreen(app.directServerState, { app.navController.popBackStack() },
            { id -> app.directServerViewModel.dismissMessage(); app.navController.navigateSingle("dev/server-profiles/edit?profileId=${Uri.encode(id.orEmpty())}") },
            { id -> app.directServerViewModel.selectProfile(id); app.directServerViewModel.connect() })
    }
    devDestination(DevDestination.PROFILE_EDITOR.route, state,
        arguments = listOf(navArgument("profileId") { defaultValue = "" })) { app, entry ->
        val id = entry.arguments?.getString("profileId")?.takeIf { it.isNotEmpty() }
        DevServerProfileEditor(app.directServerState, id, { app.navController.popBackStack() },
            app.directServerViewModel::saveProfile,
            { profile -> app.directServerViewModel.selectProfile(profile); app.directServerViewModel.connect() },
            { profile -> app.directServerViewModel.selectProfile(profile); app.directServerViewModel.removeSelectedProfile() })
    }
    devDestination(DevDestination.SERVER_TRASH.route, state) { app, _ ->
        LaunchedEffect(app.directServerState.selectedProfileId, app.directServerState.capabilities) {
            if (app.directServerState.capabilities != null) app.directServerViewModel.selectSection(DirectServerSection.TRASH)
        }
        DevServerTrashScreen(app.directServerState, { app.navController.popBackStack() }, app.directServerViewModel::refreshTrash,
            app.directServerViewModel::connect, app.directServerViewModel::emptyTrash)
    }
    devDestination(DevDestination.SERVER_PROXY.route, state, device = true) { app, _ ->
        with(app) {
            val sourceTarget = serverStorageState.selectedTarget
            val sourceSession = serverStorageState.selectedSession
            LaunchedEffect(selectedDeviceId, fullControlConnected) { if (fullControlConnected) serverStorageViewModel.refresh() }
            LaunchedEffect(serverStorageState.preview) {
                serverStorageState.preview?.let { preview ->
                    if (mediaPreviewFocused(preview.mimeType, preview.name)) navController.navigateSingle(DevDestination.PROXY_PREVIEW.route)
                }
            }
            ServerStorageScreen(serverStorageState, {
                if (!serverStorageViewModel.navigateBack()) navController.popBackStack()
            }, { navController.navigateSingle(Routes.DATA) }, serverStorageViewModel::refresh, serverStorageViewModel::selectTarget,
                serverStorageViewModel::openSession, serverStorageViewModel::deleteSession, serverStorageViewModel::openDirectory,
                serverStorageViewModel::openFile, serverStorageViewModel::loadMoreSessions, onSectionSelected,
                deletionEnabled = false, thumbnailLoader = { file ->
                    val current = serverStorageViewModel.uiState.value
                    val target = current.selectedTarget
                    val session = current.selectedSession
                    if (target == null || session == null || target.id != sourceTarget?.id || session.sessionId != sourceSession?.sessionId)
                        Result.failure(IllegalStateException("수신 결과를 다시 선택하세요."))
                    else repository.getUploadLibraryFile(target.id, session.sessionId, file.relativePath)
                }, onDismissMessage = serverStorageViewModel::dismissMessage, deviceName = selectedDeviceName,
                onConnection = { navigateToDeviceRoot(navController) })
        }
    }
    devDestination(DevDestination.PROXY_PREVIEW.route, state, device = true) { app, _ ->
        val preview = app.serverStorageState.preview
        val back = { app.serverStorageViewModel.navigateBack(); app.navController.popBackStack(); Unit }
        DevFrame(preview?.name ?: "미리 보기", back) {
            if (preview != null) FilePreview(preview)
            else DevEmpty("미리 볼 파일이 없습니다", "파일 목록에서 다시 선택하세요.")
        }
    }
    listOf(DevDestination.PIPELINES.route, DevDestination.PIPELINE.route).forEach { route ->
        devDestination(route, state, device = true, arguments = if (route.contains('{')) listOf(navArgument("pipelineId") { type = NavType.StringType }) else emptyList()) { app, entry ->
            with(app) {
                val detail = entry.arguments?.getString("pipelineId")
                PipelineListScreen(pipelineState, { navController.popBackStack() }, pipelineViewModel::refresh,
                    { pipelineViewModel.beginCreate(); navController.navigateSingle(DevDestination.NEW_PIPELINE.route) },
                    { pipeline, action -> pipelineViewModel.control(pipeline, action,
                        if (action == "stop") pipeline.activeRunId ?: pipeline.execution?.runId?.takeIf { it.isNotBlank() } else null) },
                    pipelineViewModel::remove,
                    { navController.navigateSingle("dev/pipeline/${Uri.encode(it.id)}/logs") },
                    { navController.navigateSingle("dev/pipeline/${Uri.encode(it.id)}/config") },
                    { pipeline -> pipeline.outputRootId?.let { root ->
                        navController.navigateSingle("storage?rootId=${Uri.encode(root)}&path=${Uri.encode(pipeline.outputPath.orEmpty())}")
                    } }, onSectionSelected, pipelineViewModel::clearMessage, deviceName = selectedDeviceName,
                    onDetails = { navController.navigateSingle("dev/pipeline/${Uri.encode(it.id)}") }, detailId = detail,
                    startCapability = dashboardState.capabilities.pipelines && dashboardState.capabilities.mobileTimeSync,
                    onHistory = { navController.navigateSingle(Routes.TASK_HISTORY) }, onPrepareRun = openPipelinePreparation,
                    developerModeEnabled = true, onPolicy = { navController.navigateSingle("dev/pipeline/${Uri.encode(it.id)}/policy") },
                    onRelay = { navController.navigateSingle(DevDestination.RELAY.route) })
            }
        }
    }
    devDestination(DevDestination.NEW_PIPELINE.route, state, device = true) { app, _ ->
        with(app) {
            LaunchedEffect(pipelineState.registeredPipelineId) {
                pipelineState.registeredPipelineId?.let { id ->
                    navController.navigateSingle("dev/pipeline/${Uri.encode(id)}") { popUpTo(DevDestination.NEW_PIPELINE.route) { inclusive = true } }
                }
            }
            PipelineEditorScreen(pipelineState, { navController.popBackStack() },
                { pipelineViewModel.beginPick(it); navController.navigateSingle(DevDestination.PICKER.route) },
                pipelineViewModel::setLabel, pipelineViewModel::setAutostart, pipelineViewModel::register)
        }
    }
    devDestination(DevDestination.PICKER.route, state, device = true) { app, _ ->
        with(app) {
            PipelinePickerScreen(pipelineState.roots, pipelineState.picker,
                { if (!pipelineViewModel.navigatePickerBack()) navController.popBackStack() }, pipelineViewModel::refreshPicker,
                pipelineViewModel::selectPickerRoot, pipelineViewModel::openPickerDirectory,
                { if (pipelineViewModel.selectPickerFile(it)) navController.popBackStack() },
                { if (pipelineViewModel.selectCurrentPickerDirectory()) navController.popBackStack() })
        }
    }
    listOf(DevDestination.LOGS.route).forEach { route ->
        devDestination(route, state, device = true, arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })) { app, entry ->
            val id = entry.arguments?.getString("pipelineId").orEmpty()
            DisposableEffect(id) { app.pipelineViewModel.startLogStreaming(id); onDispose { app.pipelineViewModel.stopLogStreaming() } }
            PipelineLogScreen(app.pipelineState, { app.navController.popBackStack() }, app.pipelineViewModel::refreshLogs, app.pipelineViewModel::selectLogFile)
        }
    }
    listOf(DevDestination.CONFIG.route).forEach { route ->
        devDestination(route, state, device = true, arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })) { app, entry ->
            val id = entry.arguments?.getString("pipelineId").orEmpty()
            LaunchedEffect(id) { app.pipelineViewModel.loadConfig(id) }
            PipelineConfigScreen(app.pipelineState, { app.navController.popBackStack() }, app.pipelineViewModel::setConfigValue,
                app.pipelineViewModel::saveConfig, app.pipelineViewModel::reloadConfig)
        }
    }
    devDestination(DevDestination.POLICY.route, state, device = true,
        arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })) { app, entry ->
        val id = entry.arguments?.getString("pipelineId").orEmpty()
        val vm: PipelinePolicyViewModel = viewModel(key = "dev-policy/${entry.id}",
            factory = PipelinePolicyViewModel.Factory(app.repository, app.selectedDeviceId, id))
        val policy by vm.state.collectAsStateWithLifecycle()
        val locked = app.surveyRunState.contextLocked
        PipelinePolicyScreen(if (locked) policy.copy(online = false, message = "진행 중인 수집이 확인되는 동안에는 수집 정책을 바꿀 수 없습니다.") else policy,
            app.pipelineState.pipelines.firstOrNull { it.id == id }?.label ?: "수집 작업",
            app.selectedDeviceName, { app.navController.popBackStack() },
            { edit -> if (!state.value.surveyRunState.contextLocked) vm.edit(edit) },
            { if (!state.value.surveyRunState.contextLocked) vm.save() }, vm::reload)
    }
    devDestination(DevDestination.RELAY.route, state, device = true) { app, _ ->
        StatusPollingLifecycleEffect(app.dashboardViewModel)
        DevPage("보정 정보 중계", { app.navController.popBackStack() }) {
            val relay = app.pipelineState.mobileRtkRelay
            DevIdentity(app.selectedDeviceName)
            DevText(if (relay.active) "중계 등록됨" else "꺼짐")
            DevFeedback(relay.error, true)
            DevText("직접 연결 후 수집 전에도 중계를 준비합니다. 실제 보정 수신과 RTK FIX는 장치의 위치 상태에서 확인하세요.")
            val dashboard = app.deviceDashboardState
            GnssDiagnosticsSection(dashboard.status.gnssSensor,
                fresh = dashboard.isOnline && dashboard.status.sensorTelemetryFresh &&
                    dashboard.statusFreshness == com.example.jetsoncontroller.ui.dashboard.StatusFreshness.CURRENT)
        }
    }
}

private fun NavGraphBuilder.devDestination(route: String, state: State<AppNavigationState>, device: Boolean = false,
    arguments: List<NamedNavArgument> = emptyList(), content: @Composable (AppNavigationState, NavBackStackEntry) -> Unit) {
    composable(route, arguments) { entry ->
        val app = state.value
        val target = rememberSaveable(entry.id) { mutableStateOf(app.selectedDeviceId) }.value
        val name = rememberSaveable(entry.id) { app.selectedDeviceName }
        if (!app.developerModeEnabled) {
            LaunchedEffect(Unit) { navigateToSettingsRoot(app.navController) }
        } else if (device && !target.equals(app.selectedDeviceId, true)) {
            DevPage("개발자 도구", { app.navController.popBackStack() }) {
                DevIdentity(name)
                DevEmpty("대상 장치를 다시 선택하세요", "이 화면은 $name 대상으로 열었습니다. 장치 선택 후 다시 확인하세요.")
            }
        } else {
            val strip: @Composable () -> Unit = {
                if (route != DevDestination.PROXY_PREVIEW.route) OngoingRunStrip(
                    app.selectedDeviceId, app.selectedDeviceName, app.surveyRunState.activeRun,
                    onOpenRun = { target ->
                        val current = state.value
                        if (current.surveyRunState.activeRun?.runId == target.runId &&
                            current.selectedDeviceId.equals(target.deviceId, true)) current.navController.navigateSingle(Routes.ACTIVE_RUN)
                    }, awaitingStart = app.surveyRunState.pendingStart != null)
            }
            CompositionLocalProvider(LocalDevOngoingStrip provides strip) { content(app, entry) }
        }
    }
}
