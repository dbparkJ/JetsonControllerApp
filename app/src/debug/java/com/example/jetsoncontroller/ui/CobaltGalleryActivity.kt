package com.example.jetsoncontroller.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.pipelines.*
import com.example.jetsoncontroller.ui.settings.SettingsHubScreen
import com.example.jetsoncontroller.ui.storage.DataHubScreen
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import com.example.jetsoncontroller.ui.upload.UploadUiState

/** Debug source set only. No repository, radios, commands, or sample state in release. */
class CobaltGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv", outputRootId = "recordings", outputPath = "inspection"),
            ManagedPipeline("gnss", "정밀 측위 로그", state = PipelineState.STARTING,
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv", outputRootId = "recordings", outputPath = "gnss"),
            ManagedPipeline("surface", "표면 결함 수집", state = PipelineState.FAILED, result = "인증 확인 실패",
                entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        )
    }
    val offline = screen == "offline"
    val state = DashboardUiState(deviceName = "JETSON-DEMO · 시안", isOnline = !offline,
        fullControlAvailable = !offline, transportType = if (offline) null else TransportType.LAN,
        capabilities = ControlCapabilities(pipelines = true, mobileTimeSync = true, uploads = true, fileBrowsing = true),
        statusFreshness = if (offline) StatusFreshness.STALE else StatusFreshness.CURRENT,
        status = JetsonStatus(temperatureC = 45f, storagePercent = 68, sensorTelemetryAvailable = true,
            sensorTelemetryFresh = !offline, cameraSensor = CameraSensorStatus(active = true),
            gnssSensor = GnssSensorStatus(active = true), imuSensor = ImuSensorStatus(active = true)))
    val select: (ControlSection) -> Unit = { screen = when (it) {
        ControlSection.OVERVIEW -> "home"
        ControlSection.PIPELINES -> "tasks"
        ControlSection.DATA -> "data"
        else -> "settings"
    } }
    JetsonControllerTheme(darkTheme = dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Text("DEBUG 시안 · 장비 통신 없음", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(4.dp), color = MaterialTheme.colorScheme.error)
            Box(Modifier.weight(1f)) {
                when (screen) {
                    "tasks", "detail" -> PipelineListScreen(
                        state = PipelineUiState(deviceId = "demo", controlAvailable = true, pipelines = sample,
                            observedAtMillis = now, pendingActions = mapOf("gnss" to "start")),
                        onBack = { screen = "home" }, onRefresh = {}, onAdd = {}, onControl = { _, _ -> },
                        onRemove = {}, onLogs = {}, onConfig = {}, onOutput = { screen = "data" },
                        onSectionSelected = select, onClearMessage = {}, deviceName = state.deviceName,
                        onDetails = { screen = "detail" }, detailId = if (screen == "detail") "inspection" else null,
                        startCapability = true, nowMillis = now)
                    "data" -> DataHubScreen(state.deviceName, sample, UploadUiState(deviceId = "demo"),
                        true, "", 0, {}, {}, {}, {}, {}, { _, _ -> }, select)
                    "settings" -> SettingsHubScreen(state, "demo", 0, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, select)
                    else -> DashboardScreen(state, sample, emptyList(), 0, {}, {}, {}, {}, {}, {}, {},
                        { screen = "data" }, {}, { screen = "data" }, { screen = "tasks" }, select, {}, {},
                        tasksConfirmed = !offline, taskObservedAt = now, pendingTaskActions = mapOf("gnss" to "start"))
                }
            }
        }
    }
}
