package com.example.jetsoncontroller.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.jetsoncontroller.ui.components.geo.shell.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.pipelines.*
import com.example.jetsoncontroller.ui.settings.SettingsHubScreen
import com.example.jetsoncontroller.ui.storage.DataHubScreen
import com.example.jetsoncontroller.ui.storage.DeviceStorageUiState
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

/** Debug source set only. No repository, radios, commands, or sample state in release. */
class CobaltGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,
                intent.getFloatExtra("fontScale", density.fontScale))) {
                CobaltGallery(intent.getStringExtra("screen") ?: "home", intent.getBooleanExtra("dark", false))
            }
        }
}
}

@Composable
fun CobaltGallery(initialScreen: String = "home", dark: Boolean = false) {
    var screen by remember(initialScreen) { mutableStateOf(initialScreen) }
    val now = remember { System.currentTimeMillis() }
    val sample = remember {
        listOf(
            ManagedPipeline("inspection", "야간 라인 검사", state = PipelineState.RUNNING,
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv",
                outputRootId = "recordings", outputPath = "inspection", activeRunId = "run-demo"),
            ManagedPipeline("gnss", "정밀 측위 로그", state = PipelineState.STARTING,
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv", outputRootId = "recordings", outputPath = "gnss"),
            ManagedPipeline("surface", "표면 결함 수집", state = PipelineState.FAILED, result = "인증 확인 실패",
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        )
    }
    val sampleFiles = remember {
        listOf(
            RemoteFileEntry("야간 라인 검사", "inspection", RemoteEntryType.DIRECTORY,
                2_400_000_000, "2026-09-15T13:24:00Z"),
            RemoteFileEntry("정밀 측위 로그", "gnss", RemoteEntryType.DIRECTORY,
                1_800_000_000, "2026-09-15T11:08:00Z"),
            RemoteFileEntry("route.csv", "route.csv", RemoteEntryType.FILE,
                3_200_000, "2026-09-14T16:40:00Z")
        )
    }
    val offline = screen == "offline"
    val recentRuns = remember {
        val context = SurveyContextSnapshot(
            deviceId = "demo", surveyProjectId = "project", surveyProjectLabel = "서울 도로 조사",
            surveyProjectRevision = 1, surveySectionId = "section",
            surveySectionLabel = "강남대로 1구간", surveySectionRevision = 1,
            capturedAt = "2026-09-15T13:00:00Z"
        )
        listOf(
            TaskRun(
                id = "run-recent", pipelineId = "inspection", label = "야간 라인 검사",
                logId = "log-recent", startedAt = "2026-09-15T13:00:00Z",
                finishedAt = "2026-09-15T13:24:00Z", state = "FINISHED", exitCode = 0,
                runId = "run-recent", deviceId = "demo", contextSnapshot = context,
                output = PipelineRunOutput(
                    rootId = "recordings", path = "강남대로-1구간", outputId = "output-recent",
                    manifestState = "FINAL",
                    manifest = PipelineOutputManifest(
                        runId = "run-recent", generatedAt = "2026-09-15T13:24:00Z",
                        finishedAt = "2026-09-15T13:24:00Z", fileCount = 12,
                        bytesTotal = 2_400_000_000L, expectationState = "PASSED"
                    )
                )
            )
        )
    }
    val state = DashboardUiState(deviceName = "JETSON-DEMO · 시안", isOnline = !offline,
        fullControlAvailable = !offline, transportType = if (offline) null else TransportType.LAN,
        capabilities = ControlCapabilities(pipelines = true, mobileTimeSync = true, uploads = true, fileBrowsing = true),
        statusFreshness = if (offline) StatusFreshness.STALE else StatusFreshness.CURRENT,
        status = JetsonStatus(
            metricValidity = mapOf(
                "temperatureC" to MetricValidity("valid"),
                "storagePercent" to MetricValidity("valid"),
                "storageAvailableBytes" to MetricValidity("valid")
            ),
            temperatureC = 45f, storagePercent = 68, storageAvailableBytes = 84_000_000_000L,
            sensorTelemetryAvailable = true,
            sensorTelemetryFresh = !offline, cameraSensor = CameraSensorStatus(active = true),
            gnssSensor = GnssSensorStatus(active = true), imuSensor = ImuSensorStatus(active = true)))
    val select: (GeoTab) -> Unit = { screen = when (it) {
        GeoTab.DEVICES -> "home"
        GeoTab.TASKS -> "tasks"
        GeoTab.TRANSFERS -> "data"
        else -> "settings"
    } }
    JetsonControllerTheme(darkTheme = dark) {
        GeoAppShell(
            selected = when (screen) {
                "tasks", "detail" -> GeoTab.TASKS
                "data" -> GeoTab.TRANSFERS
                "settings" -> GeoTab.SETTINGS
                else -> GeoTab.DEVICES
            },
            onSelect = select,
            navigationVisible = screen != "welcome",
            topBar = {
            Text("DEBUG 시안 · 장치 통신 없음", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(4.dp), color = MaterialTheme.colorScheme.error)
            }
        ) {
            when (screen) {
                    "shell" -> ShellGallery()
                    "controls" -> com.example.jetsoncontroller.ui.gallery.ControlsGallery()
                    "content" -> com.example.jetsoncontroller.ui.gallery.ContentGallery()
                    "overlays" -> com.example.jetsoncontroller.ui.gallery.OverlayGallery()
                    "device_detail" -> com.example.jetsoncontroller.ui.devices.DeviceDetailScreen(
                        deviceName = "GEO-X-현장지원차량-2", dashboard = state, snapshot = null,
                        connection = com.example.jetsoncontroller.ui.connection.deviceConnectionUi(true, TransportType.LAN),
                        lastSeenText = null, freshness = "방금", runActive = true, developerModeEnabled = false,
                        onBack = { screen = "home" }, onDisconnect = {}, onStartCollection = {}, onViewRun = {},
                        onCamera = {}, onGnss = {}, onSameNetwork = {}, onDirectConnect = {}, onQrRegister = {},
                        onSaveDiagnostics = {}, onNetwork = {}, onFiles = {}, onReboot = {}, onShutdown = {},
                        onPipelines = {}, onFan = {}, onDiagnostics = {}
                    )
                    "welcome" -> com.example.jetsoncontroller.ui.startup.StartupLoadingScene()
                    "tasks", "detail" -> PipelineListScreen(
                        state = PipelineUiState(deviceId = "demo", controlAvailable = true, pipelines = sample,
                            observedAtMillis = now, pendingActions = mapOf("gnss" to "start")),
                        onBack = { screen = "home" }, onRefresh = {}, onAdd = {}, onControl = { _, _ -> },
                        onRemove = {}, onLogs = {}, onConfig = {}, onOutput = { screen = "data" },
                        onSectionSelected = select, onClearMessage = {}, deviceName = state.deviceName,
                        onDetails = { screen = "detail" }, detailId = if (screen == "detail") "inspection" else null,
                        startCapability = true, nowMillis = now)
                    "data" -> DataHubScreen(
                        state = DeviceStorageUiState(
                            deviceId = "demo", controlAvailable = true,
                            currentRoot = RemoteRoot("recordings", "수집 자료", null),
                            entries = sampleFiles
                        ),
                        serverUploadEnabled = true,
                        unavailableReason = "",
                        unreadCount = 0,
                        onDevices = {},
                        onAlerts = {},
                        onRefresh = {},
                        onNavigateBack = {},
                        onDirectoryClick = {},
                        onFileClick = {},
                        onDeleteClick = {},
                        onHistory = {},
                        onTransfer = { _, _ -> },
                        onSection = select
                    )
                    "settings" -> SettingsHubScreen(state, "demo", 0, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, select)
                    else -> LaunchedEffect(screen) { screen = "device_detail" }
            }
        }
    }
}

@Composable
private fun ShellGallery() {
    val window = LocalGeoWindow.current
    GeoActionArea(
        visibilityKey = "shell",
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GEO-X-현장지원차량-2 · 같은 네트워크 · 시작할 수 있습니다.")
                com.example.jetsoncontroller.ui.components.geo.GeoButton("수집 시작", {}, Modifier.fillMaxWidth())
            }
        }
    ) { inlineAction ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("셸과 내비게이션", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(window.gutterDp.dp))
            GeoPaneLayout(
                list = { Text("작업 · 장치 · 전송 · 설정", modifier = Modifier.padding(vertical = 24.dp)) },
                detail = {
                    Column {
                        Text("구간 목록과 상세", style = MaterialTheme.typography.titleLarge)
                        repeat(12) { Text("신월4거리 북측 보조 구간 2차 재수집", modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
            )
            inlineAction()
        }
    }
}
