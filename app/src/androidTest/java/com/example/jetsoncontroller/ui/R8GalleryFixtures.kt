package com.example.jetsoncontroller.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.espresso.Espresso
import com.example.jetsoncontroller.data.alerts.*
import com.example.jetsoncontroller.data.network.*
import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.alerts.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.connection.*
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.devices.*
import com.example.jetsoncontroller.ui.field.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.network.*
import com.example.jetsoncontroller.ui.onboarding.*
import com.example.jetsoncontroller.ui.pairing.*
import com.example.jetsoncontroller.ui.pipelines.*
import com.example.jetsoncontroller.ui.sensors.*
import com.example.jetsoncontroller.ui.settings.*
import com.example.jetsoncontroller.ui.startup.*
import com.example.jetsoncontroller.ui.storage.*
import com.example.jetsoncontroller.ui.survey.*
import com.example.jetsoncontroller.ui.tasks.*
import com.example.jetsoncontroller.ui.theme.*
import com.example.jetsoncontroller.ui.transfers.*
import com.example.jetsoncontroller.ui.upload.*
import com.example.jetsoncontroller.ui.wifi.*
import java.time.Instant

internal data class R8GalleryScene(
    val id: String,
    val source: String,
    val description: String,
    val tab: GeoTab = GeoTab.TASKS,
    val navigation: Boolean = true,
    val panes: Boolean = false,
    val overlay: Boolean = false,
    val darkPalettes: Boolean = false,
    val media: Boolean = false,
    val camera: Boolean = false,
    val activeRun: Boolean = false,
    val prepare: ((ComposeContentTestRule) -> Unit)? = null,
    val content: @Composable () -> Unit
)

/** Data only: no ViewModel, repository, DataStore, radio or HTTP clients. */
private object G {
    const val DEVICE = "geo-x-1"
    const val NAME = "GEO-X-1"
    const val TIME = "2026-10-27T03:30:00Z"
    const val FILE = "중앙로_동측_교차로부터_보행교까지_정밀측량_20261027_123000_camera_front_000001.jpg"
    val now: Instant = Instant.parse(TIME)
    val root = RemoteRoot("results", "수집 결과", "/data/results", 1_000_000_000_000L, 436_000_000_000L, 564_000_000_000L)
    val entries = listOf(
        RemoteFileEntry("중앙로 동측 · 교차로에서 보행교까지 긴 구간 이름", "중앙로-동측", RemoteEntryType.DIRECTORY, null, TIME),
        RemoteFileEntry(FILE, FILE, RemoteEntryType.FILE, 12_345_678L, TIME),
        RemoteFileEntry("위치와_관성센서_측정기록_20261027.csv", "sensor.csv", RemoteEntryType.FILE, 2_345_678L, TIME),
        RemoteFileEntry("수집_실행_점검.log", "run.log", RemoteEntryType.FILE, 16_384L, TIME))
    val pipeline = ManagedPipeline("survey", "현장 도로 정밀 수집", state = PipelineState.STOPPED,
        entrypoint = "capture.py", config = "capture.yaml", virtualenv = ".venv", outputRootId = root.id, outputPath = "중앙로-동측")
    val project = SurveyProject("project-demo", "도심 도로 시설물 정밀측량", 1, TIME, TIME)
    val section = SurveySection("section-demo", project.surveyProjectId, "중앙로 동측", 1, TIME, TIME)
    val context = SurveyContextSnapshot(deviceId = DEVICE, surveyProjectId = project.surveyProjectId,
        surveyProjectLabel = project.label, surveyProjectRevision = 1, surveySectionId = section.surveySectionId,
        surveySectionLabel = section.label, surveySectionRevision = 1, capturedAt = TIME)
    val policy = PipelineRunPolicy(pipelineId = pipeline.id, revision = "fixture-policy",
        requiredSensors = listOf("camera", "gnss", "imu"), minFreeBytes = 10_000_000_000L,
        outputRootId = root.id, outputPath = "중앙로-동측", updatedAt = TIME)
    val preflight = PipelinePreflight(preflightId = "fixture-check", pipelineId = pipeline.id, deviceId = DEVICE,
        surveyProject = SurveyProjectSummary(project.surveyProjectId, project.label, 1),
        surveySection = SurveySectionSummary(section.surveySectionId, project.surveyProjectId, section.label, 1),
        contextSnapshot = context, policy = policy, sourceRevision = "fixture-source", configRevision = "fixture-config",
        checkedAt = TIME, checkedAtEpochMillis = now.toEpochMilli(), ready = true,
        checks = PipelinePreflightChecks(time = PreflightCheck("PASSED"),
            storage = PreflightStorageCheck("PASSED", root.id, "중앙로-동측", 564_000_000_000L),
            sensors = listOf("camera", "gnss", "imu").map { PreflightSensorCheck(it, "REQUIRED", "PASSED", now.toEpochMilli(), 100L) }))
    val output = PipelineRunOutput(root.id, "중앙로-동측/20261027-123000", "output-demo")
    val uploadContext = UploadContext(surveyProjectId = project.surveyProjectId, surveySectionId = section.surveySectionId,
        runId = "run-demo", deviceId = DEVICE, pipelineId = pipeline.id, sourceRevision = "fixture-source",
        configSha256 = "fixture-config", outputId = output.outputId, createdAt = TIME)
    val active = PipelineRun(runId = "run-demo", logId = "log-demo", pipelineId = pipeline.id, deviceId = DEVICE,
        state = "RUNNING", startedAt = "2026-10-27T03:17:26Z", contextSnapshot = context, policySnapshot = policy,
        preflightSnapshot = preflight, sourceRevision = "fixture-source", configRevision = "fixture-config",
        output = output, uploadContext = uploadContext, active = true,
        telemetry = RunTelemetry(runId = "run-demo", outputId = output.outputId, sourceRevision = "fixture-source",
            observedAtEpochMillis = now.toEpochMilli(), bytesObservedAtEpochMillis = now.toEpochMilli(),
            durationMillis = 754_000L, collectedBytes = 12_800_000_000L, collectedFileCount = 1280, collectionBytesState = "OBSERVED"))
    val finished = active.copy(state = "COMPLETED", active = false, finishedAt = TIME, exitCode = 0)
    val history = TaskRun("history-demo", pipeline.id, "중앙로 동측", "log-demo", active.startedAt,
        finishedAt = TIME, state = "COMPLETED", exitCode = 0, runId = active.runId, deviceId = DEVICE,
        contextSnapshot = context, policySnapshot = policy, preflightSnapshot = preflight, output = output)
    val ticket = ServerTicket("ticket-demo", "중앙로 동측", project = project.label, unit = "현장 1팀",
        dueDate = "2026-10-27", status = ServerTicketStatus("할 일", "OPEN"), revision = "fixture-1")
    val targets = listOf(UploadTarget("target-demo", "검수용 수신 서버", baseUrl = "https://receiver.example.invalid", editable = true))
    val summary = UploadSourceSummary(root.id, output.path, section.label, "20261027-123000", "directory", 12_800_000_000L, 1280, TIME)
    val sending = UploadJob("upload-demo", root.id, output.path, targets.first().id, UploadJobState.UPLOADING,
        12_800_000_000L, 4_320_000_000L, 1280, 432, FILE, null, sourceName = section.label,
        folderName = "20261027-123000", throughputBytesPerSecond = 32_000_000L, etaSeconds = 265, context = uploadContext)
    val failed = sending.copy(id = "upload-failed", state = UploadJobState.FAILED, errorMessage = "서버 응답을 받지 못했습니다. 연결을 확인하고 다시 보내세요.")
    val completed = sending.copy(state = UploadJobState.COMPLETED, bytesTransferred = 12_800_000_000L, filesTransferred = 1280)
    val verified = completed.copy(deletionEligible = true, verification = UploadVerification(
        jobId = completed.id, targetId = completed.targetId, state = "VERIFIED", matched = true,
        deletionAllowed = true, bytesTotal = 12_800_000_000L, filesTotal = 1280, verifiedAt = TIME))
    val storage = DeviceStorageUiState(deviceId = DEVICE, controlAvailable = true, roots = listOf(root),
        currentRoot = root, entries = entries, deviceName = NAME, loadedAtEpochMillis = now.toEpochMilli())
    val status = JetsonStatus(collectedAtEpochMillis = now.toEpochMilli(), cpuPercent = 31, gpuPercent = 18,
        ramUsedMb = 4100, ramTotalMb = 8192, temperatureC = 55f, storagePercent = 44,
        storageUsedBytes = 436_000_000_000L, storageTotalBytes = 1_000_000_000_000L, storageAvailableBytes = 564_000_000_000L,
        cameraConfigured = true, gnssConfigured = true, imuConfigured = true, cameraRunning = true,
        gnssRunning = true, imuRunning = true, sensorTelemetryAvailable = true, sensorTelemetryFresh = true,
        sensorTelemetryUpdatedAtEpochMillis = now.toEpochMilli(), wifiConnected = true, wifiSsid = "GEO-DEMO-NET")
    val dashboard = DashboardUiState(isOnline = true, fullControlAvailable = true, deviceName = NAME,
        transportType = TransportType.LAN, statusFreshness = StatusFreshness.CURRENT, status = status,
        capabilities = ControlCapabilities(fanControl = true, powerCommandsEnabled = true,
            systemControlConfigured = true, fileBrowsing = true, uploads = true, pipelines = true),
        fanStatus = FanStatus(true, "MANUAL", percent = 40, rpm = 2150, autoAvailable = true))
    val survey = SurveyRunUiState(deviceId = DEVICE, online = true, runStateConfirmed = true, pipelineId = pipeline.id,
        pipelineLabel = pipeline.label, projects = listOf(project, project.copy(surveyProjectId = "long-project", label = "도심 간선도로와 보행교 주변 시설물의 장기 정밀측량 프로젝트")),
        sections = listOf(section, section.copy(surveySectionId = "long-section", label = "교차로에서 보행교까지 이어지는 중앙로 동측의 긴 검수 구간 이름")),
        selectedProject = project, selectedSection = section, roots = listOf(root), policy = policy, preflight = preflight,
        preparationTaskState = PipelineState.STOPPED)
    val field = FieldState(deviceId = DEVICE, online = true, runs = listOf(history), historyCurrent = true,
        terminalOutput = "/data/results\n중앙로-동측/20261027-123000\n측정 파일 1,280개 · 예시 출력")
    val pipelines = PipelineUiState(deviceId = DEVICE, controlAvailable = true, pipelines = listOf(pipeline),
        roots = listOf(root), observedAtMillis = now.toEpochMilli(), detailPipelineId = pipeline.id,
        logFiles = listOf(PipelineLogFile("log-demo", TIME, TIME, 16384)), selectedLogId = "log-demo",
        logContent = "[12:30:00] 수집 준비 완료\n[12:30:01] 카메라 · 위치 · 관성 센서 수신\n[12:42:34] 수집 종료, 파일 1,280개 저장",
        configPath = "capture.yaml", configRevision = "fixture-config", deviceName = NAME,
        configFields = listOf(PipelineConfigField("camera.fps", "카메라 초당 프레임", PipelineConfigValueType.INTEGER, "15")),
        originalConfigValues = mapOf("camera.fps" to "15"))
    val profile = StoredServerProfile(ServerEndpointProfile("profile-demo", "검수용 수신 서버", ServerEnvironment.TEST,
        "https://receiver.example.invalid/", "operator-demo", "project-demo"), "fixture-revision")
    val receipt = ServerReceipt("test", "project-demo", "session-demo", sending.id, section.label,
        "20261027-123000", "COMPLETED", 12_800_000_000L, 1280, "0".repeat(64), TIME, true, TIME, TIME, uploadContext)
    val serverJob = ServerJob("session-demo", sending.id, "project-demo", DEVICE, section.label, "COMPLETED", null,
        12_800_000_000L, 12_800_000_000L, 1280, TIME, TIME, TIME, ServerPathSummary(listOf(FILE), false, 1280, 0), uploadContext)
    val server = DirectServerUiState(profiles = listOf(profile), selectedProfileId = profile.profile.profileId,
        jobs = listOf(serverJob), refreshedAt = TIME,
        capabilities = ServerCapabilities(1, "test", ServerEmployee("operator-demo", "검수용 작업자", "ADMIN"),
            listOf(ServerProject("project-demo", project.label)), 16_000_000L, TIME),
        trash = listOf(ServerTrashJob("trash-session", "old-upload", "서문로 서측", 320_000_000L, 32, "TRASHED", TIME,
            purgeSupported = true, restoreSupported = true)), trashEmptySupported = true, trashTotal = 1)
    val trash = LocalTrashUiState(deviceId = DEVICE, online = true, deviceName = NAME, hasLoaded = true,
        refreshedAt = TIME, emptySupported = true, entries = listOf(TrashEntry("trash-demo", "file", "TRASHED",
            rootId = root.id, relativePath = FILE, name = FILE, entryType = "file", trashedAt = TIME, restoreSupported = true, purgeSupported = true)))
    val librarySession = UploadLibrarySession("session-demo", section.label, 12_800_000_000L, 1280,
        TIME, TIME, "20261027-123000")
    val serverStorage = ServerStorageUiState(deviceId = DEVICE, controlAvailable = true, targets = targets,
        selectedTarget = targets.first(), sessions = listOf(librarySession))
    val network = NetworkSettingsUiState(deviceId = DEVICE, ssid = "GEO-DEMO-NEXT", hidden = true,
        currentWifiSsid = "GEO-DEMO-NET", mobileWifiSsid = "GEO-DEMO-NET", wifiConnected = true, transportType = TransportType.LAN)
    val direct = WifiDirectState(enabled = true, discoveryAttempted = true,
        peers = listOf(WifiDirectPeer(NAME, "02:00:00:00:00:01", 3)))
    val alerts = AlertCenterUiState(listOf(AlertRecord("alert-demo", "GEO-X-1 저장 공간 확인", "남은 공간을 확인하고 수집을 준비하세요.",
        AlertDestination.STORAGE, AlertSeverity.WARNING, now.toEpochMilli())), unreadCount = 1)
    fun connection(online: Boolean) = deviceConnectionUi(online, if (online) TransportType.LAN else null)
    fun home(kind: String): TasksHomeUiState {
        val base = buildTasksHomeUiState(TasksHomeInput(NAME, true, survey, listOf(history), emptyList(), listOf(pipeline), false,
            now = now, status = status, tasksFresh = true)).copy(focus = null, ticketSection = ServerTicketUiState(tickets = listOf(ticket)))
        return when (kind) {
            "running" -> buildTasksHomeUiState(TasksHomeInput(NAME, true, survey.copy(activeRun = active), listOf(history), emptyList(), listOf(pipeline), false,
                now = now, status = status)).copy(ticketSection = base.ticketSection)
            "server-unset" -> base.copy(ticketSection = null)
            "unassigned" -> base.copy(ticketSection = ServerTicketUiState())
            "loading" -> base.copy(ticketSection = ServerTicketUiState(isLoading = true), checkingDevice = true)
            "error" -> base.copy(ticketSection = ServerTicketUiState(quietError = "수집 구간을 불러오지 못했습니다. 서버 연결을 확인하세요."))
            else -> base
        }
    }
    fun transfer(kind: String): TransfersHomeUiState {
        val base = TransfersHomeUiState("GEO-X-1 · 같은 네트워크", Tone.SUCCESS, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        return when (kind) {
            "sending" -> base.copy(active = listOf(ActiveTransferState(sending.id, section.label, "GEO-X-1 → 검수용 수신 서버", "보내는 중", Tone.INFO,
                .3375f, "4.32 GB / 12.80 GB", "32 MB/s", "약 4분 25초", true, transferActionTarget(DEVICE, sending))))
            "attention" -> base.copy(failed = listOf(FailedTransferState(failed.id, "중앙로 동측 · 교차로에서 보행교까지 긴 구간 이름", "확인 필요", failed.errorMessage.orEmpty())),
                awaitingVerification = listOf(TransferRowState("pending-demo", "서문로 서측", "수신 확인 전", Tone.PENDING)))
            else -> base.copy(emptyText = "아직 전송 내역이 없습니다. 보낼 파일을 선택하세요.")
        }
    }
    fun start(phase: GeoStartPhase) = GeoStartSheetState(section.label, project.label, NAME, "구역 안 · 장치 위치 확인", phase,
        listOf(GeoStartCheck("장치 연결", GeoStartPhase.PASSED, "연결됨"),
            GeoStartCheck("저장 공간", GeoStartPhase.PASSED, "564 GB 남음"),
            GeoStartCheck("카메라", phase, if (phase == GeoStartPhase.PASSED) "수신 중" else null,
                if (phase == GeoStartPhase.FAILED) "최근 영상을 받지 못했습니다. 카메라 연결을 확인하세요." else null)),
        ticketLinked = true, insideZone = true)
    // A synthetic frame is drawn locally. It is never fetched from a device or decoded from user data.
    val cameraFrame: Bitmap by lazy {
        Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.rgb(51, 67, 77))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(134, 150, 160); strokeWidth = 5f }
            for (x in 0..1280 step 160) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 800f, paint)
            for (y in 0..800 step 160) canvas.drawLine(0f, y.toFloat(), 1280f, y.toFloat(), paint)
            paint.color = AndroidColor.WHITE; paint.textSize = 42f
            canvas.drawText("GEO-X-1 / DEMO FRAME / 1280 x 800", 80f, 400f, paint)
        }
    }
    fun camera(kind: String) = CameraPreviewUiState(deviceId = DEVICE, deviceName = NAME, targetConnected = true,
        targetStillCurrent = { true }, frame = if (kind in listOf("frame", "delayed")) cameraFrame else null,
        checkedAtElapsedMillis = 10_000L, frameFreshness = CameraFrameFreshness(receivedAt = if (kind == "delayed") 0L else 10_000L),
        updatedAtEpochMillis = now.toEpochMilli(), error = if (kind == "error") "카메라 영상을 받지 못했습니다. 다시 시도하세요." else null)
}

// Some screens read capture defaults or persist their initial technical-info toggle. All such
// access stays in this per-scene memory map; it never reaches Android's installed preferences.
private class GalleryPreferences : SharedPreferences {
    private val values = mutableMapOf<String?, Any?>()
    override fun getAll(): MutableMap<String, *> = values.entries.filter { it.key != null }.associate { it.key!! to it.value }.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = key in values
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String?, Any?>()
        private var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String?, value: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = value?.toSet() }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { pending[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() { commit() }
    }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

@Composable internal fun R8GalleryEnvironment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isolated = remember(context) { object : ContextWrapper(context) {
        private val preferences = mutableMapOf<String?, GalleryPreferences>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences.getOrPut(name) { GalleryPreferences() }
    } }
    CompositionLocalProvider(LocalContext provides isolated, content = content)
}

private fun click(rule: ComposeContentTestRule, text: String) {
    R8GalleryDeadline.check()
    // File labels add zero-width break opportunities without changing their visible text.
    val visibleText = hasVisibleText(text)
    val matcher = visibleText and hasClickAction()
    var candidates = rule.onAllNodes(matcher)
    if (candidates.fetchSemanticsNodes().isEmpty()) {
        val scrolls = rule.onAllNodes(hasScrollToIndexAction())
        // Bound both the number of scroll containers and attempts per container.
        for (index in 0 until minOf(scrolls.fetchSemanticsNodes().size, 4)) {
            for (attempt in 0 until 3) {
                R8GalleryDeadline.check()
                if (rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) break
                try { scrolls[index].performScrollToNode(visibleText) } catch (_: AssertionError) { }
            }
            if (rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) break
        }
        candidates = rule.onAllNodes(matcher)
    }
    check(candidates.fetchSemanticsNodes().isNotEmpty()) { "버튼을 찾지 못했습니다: $text" }
    R8GalleryDeadline.check()
    val target = candidates[0]
    try { target.performScrollTo() } catch (_: AssertionError) { }
    target.performClick()
    rule.mainClock.advanceTimeBy(600L)
    rule.waitForIdle()
}
private fun icon(rule: ComposeContentTestRule, description: String) {
    R8GalleryDeadline.check()
    val candidates = rule.onAllNodesWithContentDescription(description)
    check(candidates.fetchSemanticsNodes().isNotEmpty()) { "아이콘을 찾지 못했습니다: $description" }
    candidates[0].performClick()
    rule.mainClock.advanceTimeBy(600L); rule.waitForIdle()
}

internal object R8GalleryDeadline {
    var deadlineNanos: Long = Long.MAX_VALUE

    fun check() {
        kotlin.check(System.nanoTime() <= deadlineNanos) { "장면 준비 제한 시간 초과" }
    }
}

@Composable private fun Work(kind: String = "wait") {
    TasksHomeScreen(
        state = G.home(kind),
        onSelectDevice = {},
        onStartSurvey = {},
        onChangeSection = {},
        onOpenActiveRun = {},
        onOpenRun = {},
        onOpenHistory = {},
        onOpenTask = {},
        now = G.now,
        onCamera = {},
        onSendFiles = {},
        onDeviceMap = {},
        onDeviceFiles = {},
        stopAction = GeoButtonAction("수집 종료", {}, style = GeoButtonStyle.DangerOutline)
    )
}

@Composable private fun Ticket() {
    TicketDetailScreen(
        ticket = G.ticket,
        role = "ADMIN",
        reportMessage = null,
        onBack = {},
        onStart = {},
        taskLabel = G.pipeline.label,
        mapCard = {}
    )
}

@Composable private fun WorkTask() {
    WorkTaskDetailScreen(
        pipeline = G.pipeline,
        readiness = TicketPassiveReadiness(),
        onBack = {},
        onPrepare = {},
        lastRun = G.history
    )
}

@Composable private fun Prepare(state: SurveyRunUiState = G.survey) {
    SurveyRunScreen(
        state = state,
        onBack = {},
        onRefresh = {},
        onSelectProject = {},
        onSelectSection = {},
        onCreateProject = {},
        onCreateSection = {},
        onSensorRequirement = { _, _ -> },
        onMinFreeBytes = {},
        onOutputMinFiles = {},
        onOutputMinBytes = {},
        onOutputPatterns = {},
        onOutputRoot = {},
        onOutputPath = {},
        onSavePolicy = {},
        onPreflight = {},
        onStart = {},
        onRetryPendingStart = {},
        onDismissMessage = {},
        deviceName = G.NAME,
        zoneLine = "구역 안 · 장치 위치 확인"
    )
}

@Composable private fun Collect(online: Boolean = true, pending: Boolean = false) {
    ActiveRunScreen(
        deviceName = G.NAME,
        connectionLabel = if (online) "연결됨" else "오프라인",
        connectionTone = if (online) Tone.SUCCESS else Tone.UNKNOWN,
        run = if (pending) null else G.active,
        elapsedLabel = "12:34",
        lastObservedLabel = "12:30:00",
        sensorSummary = "카메라 · 위치 · 관성 센서 수신 중",
        storageSummary = "12.8 GB 수집 · 564 GB 남음",
        stopInProgress = false,
        online = online,
        pendingStart = pending,
        unreadCount = 1,
        onDevices = {},
        onAlerts = {},
        onBack = {},
        onRefresh = {},
        onStop = {},
        onCamera = {},
        onMap = {},
        elapsedRealtimeMillis = { 10_000L },
        projectLabel = G.project.label,
        sectionLabel = G.section.label,
        positionLabel = "정밀 위치",
        positionTone = Tone.SUCCESS
    )
}

@Composable private fun Result() {
    RunResultScreen(
        deviceName = G.NAME,
        connectionLabel = "연결됨",
        connectionTone = Tone.SUCCESS,
        run = G.finished,
        uploadEnabled = true,
        uploadDisabledReason = "",
        serverReceiptLabel = "서버 수신 확인 전",
        serverReceiptTone = Tone.PENDING,
        ticketTitle = G.ticket.title,
        ticketCompletionEnabled = true,
        online = true,
        unreadCount = 0,
        onDevices = {},
        onAlerts = {},
        onBack = {},
        onRefresh = {},
        onPrepareUpload = {},
        onOpenFiles = {},
        onNewSurvey = {},
        historyEntry = G.history
    )
}

@Composable private fun History() {
    HistoryEntryScreen(
        entry = G.history,
        deviceName = G.NAME,
        online = true,
        developerModeEnabled = true,
        onBack = {},
        onOpenFiles = {},
        onOpenLog = {},
        savedLog = null,
        onDismissLog = {}
    )
}

@Composable private fun DeviceList(kind: String = "connected") {
    DevicesHomeScreen(
        registeredDevices = if (kind == "empty") emptyList() else listOf(RegisteredDevice(G.DEVICE, G.NAME), RegisteredDevice("geo-x-2", "GEO-X-2 · 보행교 주변 정밀측량 장치")),
        selectedDeviceId = G.DEVICE,
        currentDashboard = G.dashboard.copy(isOnline = kind == "connected" || kind == "attention", fullControlAvailable = kind == "connected" || kind == "attention"),
        snapshots = emptyMap(),
        availableDeviceIds = emptySet(),
        connectingDeviceId = null,
        connectFailedDeviceId = if (kind == "error") G.DEVICE else null,
        runActive = false,
        attention = if (kind == "attention") DeviceAttention("저장 공간 확인", "남은 공간이 8 GB입니다. 보내기를 마친 파일을 정리하세요.") else null,
        unreadAlertCount = 1,
        nowEpochMillis = G.now.toEpochMilli(),
        developerModeEnabled = true,
        onSectionSelected = {},
        onAlertsClick = {},
        onAddDevice = {},
        onServerFiles = {},
        onOpenDevice = {},
        onReconnect = {},
        onStartCollection = {},
        onViewRun = {},
        onSelectDevice = {},
        onAttentionClick = {},
        onSameNetwork = {},
        onDirectConnect = {},
        onQrRegister = {},
        onSaveDiagnostics = {},
        loadingDevices = kind == "loading"
    )
}

@Composable private fun DeviceDetail(online: Boolean = true, attention: Boolean = false) {
    DeviceDetailScreen(
        deviceName = G.NAME,
        dashboard = G.dashboard.copy(isOnline = online, fullControlAvailable = online, status = if (attention) G.status.copy(storagePercent = 99, storageAvailableBytes = 8_000_000_000L, temperatureC = 83f) else G.status),
        snapshot = null,
        connection = G.connection(online),
        lastSeenText = "12:30",
        freshness = "방금 갱신",
        runActive = false,
        developerModeEnabled = true,
        onBack = {},
        onDisconnect = {},
        onStartCollection = {},
        onViewRun = {},
        onCamera = {},
        onGnss = {},
        onSameNetwork = {},
        onDirectConnect = {},
        onQrRegister = {},
        onSaveDiagnostics = {},
        onNetwork = {},
        onFiles = {},
        onReboot = {},
        onShutdown = {},
        onPipelines = {},
        onFan = {},
        onDiagnostics = {},
        deviceId = G.DEVICE,
        selectedDeviceId = G.DEVICE,
        onForget = {}
    )
}

@Composable private fun Connections() {
    ConnectMethodSheet(
        deviceName = G.NAME,
        connection = G.connection(false),
        lastSeenText = "12:30",
        developerModeEnabled = true,
        onDismiss = {},
        onSameNetwork = {},
        onDirectConnect = {},
        onQrRegister = {},
        onSaveDiagnostics = {}
    )
}

@Composable private fun Sensors() {
    SensorScreen(
        status = G.status,
        deviceOnline = true,
        fullControlAvailable = true,
        onCameraClick = {},
        onGnssClick = {},
        onSectionSelected = {},
        deviceName = G.NAME
    )
}

@Composable private fun Camera(kind: String = "frame") {
    CameraPreviewScreen(
        state = G.camera(kind),
        camera = CameraSensorStatus(frameWidth = 1280, frameHeight = 800),
        onBack = {},
        onRefresh = {}
    )
}

@Composable private fun Scanner() {
    QrScannerScreen(
        cameraPermissionGranted = false,
        errorMessage = null,
        onRequestCameraPermission = {},
        onQrScanned = { false },
        onBack = {}
    )
}

@Composable private fun Network(state: NetworkSettingsUiState = G.network) {
    NetworkSettingsScreen(
        state = state,
        onBack = {},
        onSsidChange = {},
        onPasswordChange = {},
        onHiddenChange = {},
        onSubmit = {},
        wifiScanPermissionGranted = true,
        onRequestWifiScanPermission = {},
        onScanAccessPoints = {},
        onSelectAccessPoint = {},
        deviceName = G.NAME
    )
}

@Composable private fun Direct(state: WifiDirectState = G.direct) {
    WifiDirectScreen(
        state = state,
        permissionGranted = true,
        onBack = {},
        onPermissionClick = {},
        onDiscoveryClick = {},
        onConnectClick = {},
        onRetryApi = {}
    )
}

@Composable private fun Transfers(kind: String = "sending") {
    TransfersHomeScreen(
        state = G.transfer(kind),
        onPickFiles = {},
        onOpenServerFiles = {},
        onOpenTransfer = {},
        onCancelTransfer = {},
        onRetryTransfer = {},
        onSendRun = {},
        onOpenHistory = {},
        deviceId = G.DEVICE,
        deviceName = G.NAME,
        connectionTone = Tone.SUCCESS
    )
}

@Composable private fun SendFiles(state: DeviceStorageUiState = G.storage) {
    TransferFilesScreen(
        state = state,
        targets = G.targets,
        summary = G.summary,
        calculating = false,
        sending = false,
        sendEnabled = true,
        disabledReason = "",
        onBack = {},
        onParent = {},
        onDirectory = {},
        onFilePreview = {},
        onSelect = { _, _ -> },
        onStart = { _, _, _ -> },
        onRefresh = {},
        onRemedy = {}
    )
}

@Composable private fun Queue(kind: String = "list") {
    UploadQueueScreen(
        queue = if (kind == "list") listOf(G.sending, G.failed, G.completed.copy(id = "upload-completed")) else emptyList(),
        targets = G.targets,
        isLoading = kind == "loading",
        error = if (kind == "error") "전송 목록을 불러오지 못했습니다." else null,
        onRefresh = {},
        onManageTargets = {},
        onJobClick = {},
        onDeleteJob = {},
        onBack = {},
        deviceId = G.DEVICE,
        developerModeEnabled = true
    )
}

@Composable private fun ConfirmUpload() {
    UploadConfirmScreen(
        rootId = G.root.id,
        path = G.output.path,
        targets = G.targets,
        sourceSummary = G.summary,
        isCalculatingSource = false,
        serverUploadEnabled = true,
        serverUploadDisabledReason = null,
        isLoading = false,
        error = null,
        onBack = {},
        onRefresh = {},
        onManageTargets = {},
        onConfirm = {},
        deviceId = G.DEVICE,
        deviceName = G.NAME
    )
}

@Composable private fun Progress(job: UploadJob? = G.sending, loading: Boolean = false, error: String? = null, report: Boolean = false) {
    UploadProgressScreen(
        job = job,
        verification = job?.verification,
        isLoading = loading,
        message = null,
        error = error,
        onCancel = {},
        onRetry = {},
        onVerify = {},
        onDeleteSource = {},
        onRestoreSource = {},
        onBack = {},
        developerModeEnabled = true,
        linkedToTicket = report,
        ticketTitle = G.ticket.title,
        targetLabel = G.targets.first().label,
        deviceId = G.DEVICE,
        deviceName = G.NAME
    )
}

@Composable private fun Targets(kind: String = "list") {
    UploadTargetSettingsScreen(
        targets = if (kind == "list") G.targets else emptyList(),
        isLoading = kind == "loading",
        message = null,
        error = if (kind == "error") "서버 목록을 불러오지 못했습니다." else null,
        onSave = { _, _, _, _ -> },
        onDelete = {},
        onRefresh = {},
        onClearFeedback = {},
        onBack = {},
        deviceId = G.DEVICE,
        onOpenEditor = {},
        deviceName = G.NAME
    )
}

@Composable private fun TargetEditor() {
    UploadTargetEditorScreen(
        target = G.targets.first(),
        isSaving = false,
        controlAvailable = true,
        deviceId = G.DEVICE,
        deviceName = G.NAME,
        message = null,
        error = null,
        onSave = { _, _, _, _ -> },
        onDelete = {},
        onBack = {}
    )
}

@Composable private fun Data(state: DeviceStorageUiState = G.storage) {
    DataHubScreen(
        state = state,
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
        onSection = {}
    )
}

@Composable private fun Files(state: DeviceStorageUiState = G.storage) {
    DeviceStorageScreen(
        state = state,
        serverUploadEnabled = true,
        serverUploadDisabledReason = null,
        onBack = {},
        onRefresh = {},
        onDirectoryClick = {},
        onFileClick = {},
        onDeleteClick = {},
        onUploadClick = { _, _ -> },
        onSectionSelected = {},
        onTrash = {}
    )
}

@Composable private fun Server(state: DirectServerUiState = G.server) {
    DirectServerScreen(
        state = state,
        onBack = {},
        onSection = {},
        onSelectProfile = {},
        onSaveProfile = { _, _ -> },
        onConnect = {},
        onRefreshJobs = {},
        onLoadMoreJobs = {},
        onOpenJob = {},
        onOpenDirectory = {},
        onOpenFile = {},
        onReceipt = {},
        onMoveToTrash = {},
        onRestore = {},
        onUndoTrash = {},
        onRefreshTrash = {},
        onEmptyTrash = { _, _ -> },
        onRemoveProfile = {},
        onDismissMessage = {},
        developerModeEnabled = true
    )
}

@Composable private fun Trash(state: LocalTrashUiState = G.trash) {
    LocalTrashScreen(
        state = state,
        onBack = {},
        onRefresh = {},
        onRestore = {},
        onEmptyTrash = { _, _ -> },
        onDismissMessage = {},
        developerModeEnabled = true
    )
}

@Composable private fun ServerFiles(state: ServerStorageUiState = G.serverStorage) {
    ServerStorageScreen(
        state = state,
        onBack = {},
        onDeviceDataClick = {},
        onRefresh = {},
        onTargetSelected = {},
        onSessionClick = {},
        onDeleteSession = {},
        onDirectoryClick = {},
        onFileClick = {},
        onLoadMore = {},
        onSectionSelected = {},
        deletionEnabled = true,
        deviceName = G.NAME
    )
}

@Composable private fun Settings() {
    // SettingsHubScreen explicitly offers a store-free Appearance() branch for previews.
    CompositionLocalProvider(LocalInspectionMode provides true) {
    SettingsHubScreen(
        state = G.dashboard,
        deviceId = G.DEVICE,
        unreadCount = 1,
        onDevices = {},
        onAlerts = {},
        onNetwork = {},
        onSensors = {},
        onTargets = {},
        onDiagnostics = {},
        onAlertSettings = {},
        onServerStorage = {},
        onRefreshFan = {},
        onFanAuto = {},
        onFanManual = {},
        onReboot = {},
        onShutdown = {},
        onDismissMessage = {},
        onSection = {},
        developerModeEnabled = true,
        onTheme = {},
        onServerConnection = {},
        onAbout = {},
        onHelp = {},
        serverProfileName = G.profile.profile.displayName,
        serverStatusWord = "연결됨",
        serverStatusTone = Tone.SUCCESS,
        selectedPage = "settings/about"
    )
    }
}

@Composable private fun AlertsSettings() {
    AlertSettingsScreen(
        settings = AlertSettings(),
        notificationPermissionGranted = true,
        onRequestNotificationPermission = {},
        onStorageEnabledChange = {},
        onStorageThresholdChange = {},
        onTemperatureEnabledChange = {},
        onTemperatureThresholdChange = {},
        onPipelineStartedEnabledChange = {},
        onPipelineFailedEnabledChange = {},
        onUploadStartedEnabledChange = {},
        onUploadEndedEnabledChange = {}
    )
}

@Composable private fun ServerConnection(login: Boolean = false) {
    ServerConnectionScreen(
        profile = G.profile,
        state = ServerConnectionUiState(status = if (login) ServerConnectionStatus.LOGIN_REQUIRED else ServerConnectionStatus.UNKNOWN),
        onBack = {},
        onCheck = {},
        onRelogin = { _, _ -> }
    )
}

@Composable private fun Alerts(state: AlertCenterUiState = G.alerts) {
    AlertCenterScreen(
        state = state,
        onBack = {},
        onAlertClick = {},
        onDelete = {},
        onMarkAllRead = {},
        onClear = {}
    )
}

@Composable private fun Developer() {
    DeveloperScreen(
        state = G.field,
        onBack = {},
        onExecute = {},
        onLogs = {},
        onDiagnostics = {},
        developerModeEnabled = true
    )
}

@Composable private fun DevNetwork() {
    DevNetworkScreen(
        state = G.field,
        onRefresh = {},
        onBack = {},
        deviceName = G.NAME
    )
}

@Composable private fun DevSensors(error: Boolean = false, results: Boolean = false) {
    DevSensorTestScreen(
        state = G.field.copy(sensorTestError = if (error) "센서 응답을 받지 못했습니다." else null,
            sensorTests = if (results) listOf("camera", "gps").associateWith {
                SensorTestResult(it, "PASSED", "5초 동안 새 데이터를 수신했습니다.", durationSeconds = 5.0, checkedAtEpochMillis = G.now.toEpochMilli())
            } else emptyMap()),
        onTest = {},
        onCamera = {},
        onGps = {},
        onBack = {},
        deviceName = G.NAME
    )
}

@Composable private fun Fan() {
    DevFanScreen(
        state = G.dashboard,
        onBack = {},
        onRefresh = {},
        onAuto = {},
        onManual = {},
        deviceId = G.DEVICE
    )
}

@Composable private fun Terminal() {
    DevTerminalScreen(
        state = G.field,
        onExecute = {},
        onBack = {},
        deviceName = G.NAME
    )
}

@Composable private fun Profiles(state: DirectServerUiState = G.server) {
    DevServerProfilesScreen(
        state = state,
        onBack = {},
        onEdit = {},
        onConnect = {}
    )
}

@Composable private fun ProfileEditor() {
    DevServerProfileEditor(
        state = G.server,
        profileId = G.profile.profile.profileId,
        onBack = {},
        onSave = { _, _ -> },
        onConnect = {},
        onRemove = {}
    )
}

@Composable private fun ServerTrash(state: DirectServerUiState = G.server) {
    DevServerTrashScreen(
        state = state,
        onBack = {},
        onRefresh = {},
        onConnect = {},
        onPurge = { _, _ -> }
    )
}

@Composable private fun Pipelines(state: PipelineUiState = G.pipelines, details: Boolean = false) {
    PipelineListScreen(
        state = state,
        onBack = {},
        onRefresh = {},
        onAdd = {},
        onControl = { _, _ -> },
        onRemove = {},
        onLogs = {},
        onConfig = {},
        onOutput = {},
        onSectionSelected = {},
        onClearMessage = {},
        deviceName = G.NAME,
        detailId = if (details) G.pipeline.id else null,
        startCapability = true,
        nowMillis = G.now.toEpochMilli(),
        developerModeEnabled = true
    )
}

@Composable private fun PipelineEditor() {
    PipelineEditorScreen(
        state = G.pipelines,
        onBack = {},
        onPick = {},
        onLabelChange = {},
        onAutostartChange = {},
        onRegister = {}
    )
}

@Composable private fun Picker(kind: String = "list") {
    PipelinePickerScreen(
        roots = listOf(G.root),
        state = PipelinePickerState(root = G.root, target = PipelinePickerTarget.REPOSITORY, entries = if (kind == "list") G.entries else emptyList(), isLoading = kind == "loading", error = if (kind == "error") "파일 목록을 불러오지 못했습니다." else null),
        onBack = {},
        onRefresh = {},
        onRootClick = {},
        onDirectoryClick = {},
        onFileClick = {},
        onSelectCurrentDirectory = {}
    )
}

@Composable private fun Logs() {
    PipelineLogScreen(
        state = G.pipelines,
        onBack = {},
        onRefresh = {},
        onLogSelected = {}
    )
}

@Composable private fun Config() {
    PipelineConfigScreen(
        state = G.pipelines,
        onBack = {},
        onValueChange = { _, _ -> },
        onSave = {}
    )
}

@Composable private fun Policy() {
    PipelinePolicyScreen(
        state = PipelinePolicyState(policy = G.policy, roots = listOf(G.root), online = true),
        label = G.pipeline.label,
        deviceName = G.NAME,
        onBack = {},
        onEdit = {},
        onSave = {},
        onReload = {}
    )
}

@Composable private fun TaskChoices(state: PipelineUiState = G.pipelines) {
    TaskChoiceSheet(state = state, deviceId = G.DEVICE, deviceName = G.NAME, ticketTitle = G.ticket.title,
        onSelect = {}, onDismissRequest = {})
}
@Composable private fun DeviceBoard(kind: String) {
    GeoPaneLayout(singlePane = GeoPaneSelection.LIST,
        list = { DeviceList(kind) }, detail = { DeviceDetail(online = kind == "connected" || kind == "attention", attention = kind == "attention") })
}
@Composable private fun TransferBoard(kind: String) {
    GeoPaneLayout(singlePane = GeoPaneSelection.LIST, list = { Transfers(kind) },
        detail = { Progress(if (kind == "sending") G.sending else if (kind == "attention") G.failed else null) })
}
@Composable private fun SettingsBoard() {
    GeoPaneLayout(singlePane = GeoPaneSelection.LIST, list = { Settings() },
        detail = { SettingsAboutScreen(onBack = {}, developerModeEnabled = true, onDeveloperUnlocked = {}) })
}

internal fun r8WorkScenes(): List<R8GalleryScene> = buildList {
    listOf("wait" to "구간 대기", "running" to "수집 중", "server-unset" to "서버 미설정",
        "unassigned" to "배정 없음", "loading" to "불러오는 중", "error" to "목록 오류").forEach { (kind, label) ->
        add(R8GalleryScene("home-$kind", "tasks/TasksHomeScreen.kt · TasksHomeScreen", label,
            panes = true, darkPalettes = kind == "wait") { Work(kind) })
    }
    add(R8GalleryScene("ticket", "tasks/TicketDetailScreen.kt · TicketDetailScreen", "구간 상세 · 지도 자료 없음") { Ticket() })
    add(R8GalleryScene("task", "tasks/TicketDetailScreen.kt · WorkTaskDetailScreen", "작업 상세 · 최근 수집") { WorkTask() })
    add(R8GalleryScene("missing-ticket", "tasks/TicketDetailScreen.kt · WorkMissingScreen", "구간 없음") {
        WorkMissingScreen(ticket = true, onBack = {}, onWork = {})
    })
    add(R8GalleryScene("missing-task", "tasks/TicketDetailScreen.kt · WorkMissingScreen", "작업 없음") {
        WorkMissingScreen(ticket = false, onBack = {}, onWork = {})
    })
    listOf("ready", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("prepare-$kind", "survey/SurveyRunScreen.kt · SurveyRunScreen", "준비 · ${galleryStateLabel(kind)}", navigation = false) {
            Prepare(when (kind) {
                "empty" -> G.survey.copy(projects = emptyList(), sections = emptyList(), selectedProject = null, selectedSection = null)
                "loading" -> G.survey.copy(isLoading = true, sections = emptyList())
                "error" -> G.survey.copy(error = "장치에서 수집 구간을 받지 못했습니다. 다시 확인하세요.")
                else -> G.survey
            })
        })
    }
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("pipelines-$kind", "pipelines/PipelineScreens.kt · PipelineListScreen", "개발자 작업 목록 · ${galleryStateLabel(kind)}") {
            Pipelines(when (kind) {
                "empty" -> G.pipelines.copy(pipelines = emptyList())
                "loading" -> G.pipelines.copy(pipelines = emptyList(), isLoading = true)
                "error" -> G.pipelines.copy(pipelines = emptyList(), error = "작업 목록을 불러오지 못했습니다.")
                else -> G.pipelines
            })
        })
    }
    add(R8GalleryScene("pipeline-editor", "pipelines/PipelineScreens.kt · PipelineEditorScreen", "작업 등록 폼") { PipelineEditor() })
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("pipeline-picker-$kind", "pipelines/PipelineScreens.kt · PipelinePickerScreen", "파일 선택 · ${galleryStateLabel(kind)}") { Picker(kind) })
    }
    add(R8GalleryScene("pipeline-log", "pipelines/PipelineScreens.kt · PipelineLogScreen", "예시 실행 로그") { Logs() })
    add(R8GalleryScene("pipeline-config", "pipelines/PipelineScreens.kt · PipelineConfigScreen", "설정 입력") { Config() })
    add(R8GalleryScene("pipeline-policy", "pipelines/PipelinePolicyScreen.kt · PipelinePolicyScreen", "수집 정책 입력") { Policy() })
}

internal fun r8RunScenes(): List<R8GalleryScene> = listOf(
    R8GalleryScene("collecting", "field/ActiveRunScreen.kt · ActiveRunScreen", "수집 중 · 시간 고정 · 지도 슬롯 비움",
        navigation = false, panes = true, activeRun = true, darkPalettes = true) { Collect() },
    R8GalleryScene("offline", "field/ActiveRunScreen.kt · ActiveRunScreen", "수집 중 연결 끊김 · 마지막 상태",
        navigation = false, panes = true, activeRun = true) { Collect(online = false) },
    R8GalleryScene("pending-start", "field/ActiveRunScreen.kt · ActiveRunScreen", "시작 결과 확인 중",
        navigation = false, panes = true, activeRun = true) { Collect(pending = true) },
    R8GalleryScene("result", "field/RunResultScreen.kt · RunResultScreen", "수집 완료 · 서버 수신 확인 전") { Result() },
    R8GalleryScene("history", "field/HistoryEntryScreen.kt · HistoryEntryScreen", "저장된 수집 기록") { History() },
    R8GalleryScene("saved-log", "field/HistoryEntryScreen.kt · SavedHistoryLogScreen", "저장된 긴 로그") {
        SavedHistoryLogScreen(label = "중앙로 동측 수집 로그", text = G.pipelines.logContent, onBack = {})
    },
    R8GalleryScene("quality-evidence", "field/QualityPresentation.kt · RunQualityEvidence", "위치 품질 · 관찰 기록 없음") {
        Column(Modifier.padding(24.dp)) { RunQualityEvidence(quality = null) }
    }
)

private fun galleryStateLabel(value: String): String = when (value) {
    "list", "peers" -> "목록 있음"
    "ready", "passed" -> "준비 완료"
    "empty" -> "빈 목록"
    "loading", "checking" -> "불러오는 중"
    "error", "failed" -> "오류"
    "connected" -> "연결됨"
    "offline" -> "오프라인"
    "attention" -> "확인 필요"
    "sending" -> "보내는 중"
    "form" -> "입력 폼"
    "frame" -> "예시 영상 수신"
    "delayed" -> "영상 지연"
    "waiting" -> "영상 대기"
    "qr_scanned" -> "QR 확인"
    "connecting" -> "연결 중"
    "authenticating" -> "장치 인증 중"
    else -> value
}

internal fun r8DeviceScenes(): List<R8GalleryScene> = buildList {
    listOf("connected", "offline", "attention", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("home-$kind", "devices/DevicesHomeScreen.kt · DevicesHomeScreen; devices/DeviceDetailScreen.kt · DeviceDetailScreen",
            "장치 목록·상세 · ${galleryStateLabel(kind)}", tab = GeoTab.DEVICES, panes = true, darkPalettes = kind == "connected") { DeviceBoard(kind) })
    }
    listOf("connected", "offline", "attention").forEach { kind ->
        add(R8GalleryScene("detail-$kind", "devices/DeviceDetailScreen.kt · DeviceDetailScreen", "장치 상세 · ${galleryStateLabel(kind)}", tab = GeoTab.DEVICES) {
            DeviceDetail(online = kind != "offline", attention = kind == "attention")
        })
    }
    add(R8GalleryScene("sensors", "sensors/SensorScreen.kt · SensorScreen", "센서 목록", tab = GeoTab.DEVICES) { Sensors() })
    listOf("frame", "delayed", "waiting", "error").forEach { kind ->
        add(R8GalleryScene("camera-$kind", "sensors/CameraPreviewScreen.kt · CameraPreviewScreen", "카메라 · 합성 격자 영상 · ${galleryStateLabel(kind)}",
            tab = GeoTab.DEVICES, navigation = false, media = true, camera = true) { Camera(kind) })
    }
    add(R8GalleryScene("qr-permission", "pairing/QrScannerScreen.kt · QrScannerScreen", "카메라 권한 안내 · 실제 스캐너 시작 안 함",
        tab = GeoTab.DEVICES, navigation = false, media = true) { Scanner() })
    add(R8GalleryScene("onboarding", "onboarding/FirstDeviceOnboardingScreen.kt · FirstDeviceOnboardingScreen", "첫 장치 등록 안내",
        tab = GeoTab.DEVICES, navigation = false) {
        FirstDeviceOnboardingScreen(onScanQr = {}, onBack = {}, onOpenSettings = {}, onViewServerFiles = {})
    })
    listOf("form", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("network-$kind", "network/NetworkSettingsScreen.kt · NetworkSettingsScreen", "Wi-Fi 설정 · ${galleryStateLabel(kind)}", tab = GeoTab.DEVICES,
            prepare = if (kind == "form") ({ click(it, "숨겨진 네트워크 추가") }) else null) {
            Network(when (kind) {
                "empty" -> G.network.copy(ssid = "", hidden = false)
                "loading" -> G.network.copy(scanningAccessPoints = true)
                "error" -> G.network.copy(accessPointError = "주변 네트워크를 불러오지 못했습니다.")
                else -> G.network
            })
        })
    }
    listOf("peers", "empty", "loading", "error", "connected").forEach { kind ->
        add(R8GalleryScene("direct-$kind", "wifi/WifiDirectScreen.kt · WifiDirectScreen", "직접 연결 · ${galleryStateLabel(kind)}", tab = GeoTab.DEVICES) {
            Direct(when (kind) {
                "empty" -> G.direct.copy(peers = emptyList())
                "loading" -> G.direct.copy(peers = emptyList(), discovering = true)
                "error" -> G.direct.copy(peers = emptyList(), error = "장치를 찾지 못했습니다. 다시 시도하세요.")
                "connected" -> G.direct.copy(connected = true, apiStatus = WifiDirectApiStatus.READY, apiDeviceName = G.NAME)
                else -> G.direct
            })
        })
    }
}

internal fun r8TransferScenes(): List<R8GalleryScene> = buildList {
    listOf("sending", "attention", "empty").forEach { kind ->
        add(R8GalleryScene("home-$kind", "transfers/TransfersHomeScreen.kt · TransfersHomeScreen; upload/UploadProgressScreen.kt · UploadProgressScreen",
            "전송 홈 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS, panes = true, darkPalettes = kind == "sending") { TransferBoard(kind) })
    }
    listOf("list", "empty", "loading", "error").forEach { kind ->
        val storage = when (kind) {
            "empty" -> G.storage.copy(entries = emptyList())
            "loading" -> G.storage.copy(entries = emptyList(), isLoading = true)
            "error" -> G.storage.copy(entries = emptyList(), error = "파일 목록을 불러오지 못했습니다.")
            else -> G.storage
        }
        add(R8GalleryScene("pick-files-$kind", "transfers/TransferFilesScreen.kt · TransferFilesScreen", "보낼 파일 선택 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS, panes = true) { SendFiles(storage) })
        add(R8GalleryScene("queue-$kind", "upload/UploadQueueScreen.kt · UploadQueueScreen", "전송 기록 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) { Queue(kind) })
        add(R8GalleryScene("data-$kind", "storage/DataHubScreen.kt · DataHubScreen", "데이터 허브 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS, panes = true) { Data(storage) })
        add(R8GalleryScene("files-$kind", "storage/DeviceStorageScreen.kt · DeviceStorageScreen", "장치 파일 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS, panes = true) { Files(storage) })
        add(R8GalleryScene("server-$kind", "storage/DirectServerScreen.kt · DirectServerScreen", "서버 수신 결과 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) {
            Server(when (kind) {
                "empty" -> G.server.copy(jobs = emptyList())
                "loading" -> G.server.copy(jobs = emptyList(), isLoading = true)
                "error" -> G.server.copy(jobs = emptyList(), message = "서버 목록을 불러오지 못했습니다.", messageIsError = true)
                else -> G.server
            })
        })
        add(R8GalleryScene("trash-$kind", "storage/LocalTrashScreen.kt · LocalTrashScreen", "장치 휴지통 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) {
            Trash(when (kind) {
                "empty" -> G.trash.copy(entries = emptyList())
                "loading" -> G.trash.copy(entries = emptyList(), loading = true, hasLoaded = false)
                "error" -> G.trash.copy(entries = emptyList(), error = "휴지통을 불러오지 못했습니다.")
                else -> G.trash
            })
        })
        add(R8GalleryScene("server-storage-$kind", "storage/ServerStorageScreen.kt · ServerStorageScreen", "장치를 통한 서버 목록 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) {
            ServerFiles(G.serverStorage.copy(sessions = if (kind == "list") G.serverStorage.sessions else emptyList(),
                isLoading = kind == "loading", error = if (kind == "error") "서버 수신 목록을 불러오지 못했습니다." else null))
        })
    }
    add(R8GalleryScene("confirm", "upload/UploadConfirmScreen.kt · UploadConfirmScreen", "보내기 전 확인", tab = GeoTab.TRANSFERS) { ConfirmUpload() })
    add(R8GalleryScene("progress", "upload/UploadProgressScreen.kt · UploadProgressScreen", "전송 중", tab = GeoTab.TRANSFERS) { Progress() })
    add(R8GalleryScene("progress-complete", "upload/UploadProgressScreen.kt · UploadProgressScreen", "전송 완료 · 수신 검증 전", tab = GeoTab.TRANSFERS) { Progress(G.completed) })
    add(R8GalleryScene("progress-verified", "upload/UploadProgressScreen.kt · UploadProgressScreen", "수신 검증 일치 · 완료 보고 전", tab = GeoTab.TRANSFERS) { Progress(G.verified, report = true) })
    add(R8GalleryScene("progress-failed", "upload/UploadProgressScreen.kt · UploadProgressScreen", "전송 실패", tab = GeoTab.TRANSFERS) { Progress(G.failed) })
    add(R8GalleryScene("progress-empty", "upload/UploadProgressScreen.kt · UploadProgressScreen", "내역 선택 없음", tab = GeoTab.TRANSFERS) { Progress(null) })
    add(R8GalleryScene("progress-loading", "upload/UploadProgressScreen.kt · UploadProgressScreen", "전송 확인 중", tab = GeoTab.TRANSFERS) { Progress(null, loading = true) })
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("targets-$kind", "upload/UploadTargetSettingsScreen.kt · UploadTargetSettingsScreen", "개발자 전송 서버 목록 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) { Targets(kind) })
    }
    add(R8GalleryScene("target-editor", "upload/UploadTargetSettingsScreen.kt · UploadTargetEditorScreen", "개발자 전송 서버 폼", tab = GeoTab.TRANSFERS) { TargetEditor() })
    add(R8GalleryScene("server-detail", "storage/DirectServerScreen.kt · DirectServerScreen", "서버 수신 결과 상세 · 긴 파일명", tab = GeoTab.TRANSFERS, panes = true) {
        Server(G.server.copy(selectedJob = G.serverJob, files = G.entries.map {
            ServerFileEntry(it.name, it.relativePath, if (it.type == RemoteEntryType.DIRECTORY) "directory" else "file", it.sizeBytes, it.modifiedAt)
        }))
    })
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("server-storage-detail-$kind", "storage/ServerStorageScreen.kt · ServerStorageScreen", "장치를 통한 서버 상세 · ${galleryStateLabel(kind)}", tab = GeoTab.TRANSFERS) {
            ServerFiles(G.serverStorage.copy(selectedSession = G.librarySession, entries = if (kind == "list") G.entries else emptyList(),
                isLoading = kind == "loading", error = if (kind == "error") "서버 파일 목록을 불러오지 못했습니다." else null))
        })
    }
    add(R8GalleryScene("text-preview", "storage/MediaPresentation.kt; storage/FileBrowser.kt · StoragePage / FilePreview", "긴 파일명 · 텍스트 미리보기", tab = GeoTab.TRANSFERS) {
        StoragePage("파일 미리보기", onBack = {}) {
            FilePreview(RemoteFileContent("중앙로_동측_교차로부터_보행교까지_정밀측량_실행로그_20261027.txt", "text/plain", G.pipelines.logContent.toByteArray()), G.NAME)
        }
    })
    add(R8GalleryScene("image-preview", "storage/MediaPresentation.kt; storage/FileBrowser.kt · StoragePage / FilePreview", "긴 파일명 · 합성 이미지 미리보기", tab = GeoTab.TRANSFERS, media = true) {
        val bytes = remember { java.io.ByteArrayOutputStream().use { out -> G.cameraFrame.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
        StoragePage("파일 미리보기", onBack = {}, media = true) {
            FilePreview(RemoteFileContent(G.FILE, "image/png", bytes), G.NAME, G.entries[1])
        }
    })
}

internal fun r8SettingsScenes(): List<R8GalleryScene> = buildList {
    add(R8GalleryScene("home", "settings/SettingsHubScreen.kt · SettingsHubScreen; settings/SettingsInformationScreens.kt · SettingsAboutScreen",
        "설정 루트 · 상세는 앱 정보 선택; 테마 상세는 저장소 의존으로 제외", tab = GeoTab.SETTINGS, panes = true, darkPalettes = true) { SettingsBoard() })
    add(R8GalleryScene("alerts-settings", "settings/AlertSettingsScreen.kt · AlertSettingsScreen", "알림 설정", tab = GeoTab.SETTINGS) { AlertsSettings() })
    add(R8GalleryScene("server-connection", "settings/ServerConnectionScreen.kt · ServerConnectionScreen", "서버 연결 확인 전", tab = GeoTab.SETTINGS) { ServerConnection() })
    add(R8GalleryScene("server-login", "settings/ServerConnectionScreen.kt · ServerConnectionScreen", "다시 로그인 필요", tab = GeoTab.SETTINGS) { ServerConnection(login = true) })
    add(R8GalleryScene("about", "settings/SettingsInformationScreens.kt · SettingsAboutScreen", "앱 정보", tab = GeoTab.SETTINGS) {
        SettingsAboutScreen(onBack = {}, developerModeEnabled = true, onDeveloperUnlocked = {})
    })
    add(R8GalleryScene("help", "settings/SettingsInformationScreens.kt · SettingsHelpScreen", "도움말", tab = GeoTab.SETTINGS) { SettingsHelpScreen(onBack = {}) })
    listOf("list", "empty", "loading").forEach { kind ->
        add(R8GalleryScene("alerts-$kind", "alerts/AlertCenterScreen.kt · AlertCenterScreen", "알림 · ${galleryStateLabel(kind)}", tab = GeoTab.SETTINGS) {
            Alerts(if (kind == "list") G.alerts else AlertCenterUiState(isLoaded = kind != "loading"))
        })
    }
    add(R8GalleryScene("developer", "field/DeveloperScreen.kt · DeveloperScreen", "개발자 도구", tab = GeoTab.SETTINGS) { Developer() })
    add(R8GalleryScene("dev-network", "dev/DevDeviceTools.kt · DevNetworkScreen", "네트워크 진단 · 주소 자료 없음", tab = GeoTab.SETTINGS) { DevNetwork() })
    add(R8GalleryScene("dev-sensor", "dev/DevDeviceTools.kt · DevSensorTestScreen", "센서 테스트 전", tab = GeoTab.SETTINGS) { DevSensors() })
    add(R8GalleryScene("dev-sensor-result", "dev/DevDeviceTools.kt · DevSensorTestScreen", "센서 테스트 예시 결과", tab = GeoTab.SETTINGS) { DevSensors(results = true) })
    add(R8GalleryScene("dev-sensor-error", "dev/DevDeviceTools.kt · DevSensorTestScreen", "센서 테스트 오류", tab = GeoTab.SETTINGS) { DevSensors(error = true) })
    add(R8GalleryScene("dev-fan", "dev/DevFanScreen.kt · DevFanScreen", "수동 팬 제어 · 예시 회전수", tab = GeoTab.SETTINGS) { Fan() })
    add(R8GalleryScene("dev-terminal", "dev/DevTerminalScreen.kt · DevTerminalScreen", "원격 터미널 · 예시 출력", tab = GeoTab.SETTINGS) { Terminal() })
    listOf("list", "empty", "error").forEach { kind ->
        add(R8GalleryScene("dev-profiles-$kind", "dev/DevServerScreens.kt · DevServerProfilesScreen", "서버 프로필 목록 · ${galleryStateLabel(kind)}", tab = GeoTab.SETTINGS) {
            Profiles(if (kind == "list") G.server else G.server.copy(profiles = emptyList(), messageIsError = kind == "error",
                message = if (kind == "error") "서버 연결 정보를 확인하지 못했습니다." else null))
        })
    }
    add(R8GalleryScene("dev-profile-editor", "dev/DevServerScreens.kt · DevServerProfileEditor", "서버 프로필 입력 · 토큰 비움", tab = GeoTab.SETTINGS) { ProfileEditor() })
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(R8GalleryScene("dev-trash-$kind", "dev/DevServerScreens.kt · DevServerTrashScreen", "서버 휴지통 · ${galleryStateLabel(kind)}", tab = GeoTab.SETTINGS) {
            ServerTrash(G.server.copy(trash = if (kind == "list") G.server.trash else emptyList(), isLoading = kind == "loading",
                messageIsError = kind == "error", message = if (kind == "error") "서버 휴지통을 불러오지 못했습니다." else null))
        })
    }
}

internal fun r8StartupScenes(): List<R8GalleryScene> = listOf(
    R8GalleryScene("loading", "startup/StartupScenes.kt · StartupLoadingScene", "L1 불러오는 중 · 애니메이션 시계 고정", navigation = false) { StartupLoadingScene() },
    R8GalleryScene("error", "startup/StartupScenes.kt · StartupErrorScene", "L3 복원 실패", navigation = false) {
        StartupErrorScene(IllegalStateException("검수용 복원 오류"), onRetry = {})
    },
    R8GalleryScene("content-loading", "startup/StartupScenes.kt · StartupContent", "실제 시작 소유자 · 복원 대기", navigation = false) {
        val timing = remember { StartupTiming(0L) }
        StartupContent(AppRestoreState.Loading, timing, {}, GeoPalette.STANDARD) { Work() }
    },
    R8GalleryScene("content-error", "startup/StartupScenes.kt · StartupContent", "실제 시작 소유자 · 복원 오류", navigation = false) {
        val timing = remember { StartupTiming(0L) }
        StartupContent(AppRestoreState.Failed(IllegalStateException("검수용 복원 오류")), timing, {}, GeoPalette.STANDARD) { Work() }
    },
    R8GalleryScene("content-restored", "startup/StartupScenes.kt · StartupContent", "실제 시작 소유자 · 복원 완료", panes = true) {
        val timing = remember { StartupTiming(0L) }
        StartupContent(AppRestoreState.Restored(emptyList()), timing, {}, GeoPalette.STANDARD) { Work() }
    }
)

/** Graph-owned overlays have no state-only route entry. Their exact public building blocks are
 * composed here with the graph's labels. Descriptions distinguish these from real host interactions. */
private fun overlayScene(
    id: String, source: String, description: String,
    host: Boolean = false,
    prepare: ((ComposeContentTestRule) -> Unit)? = null,
    content: @Composable () -> Unit
) = R8GalleryScene(id, source, description, overlay = true,
    tab = if (!host) GeoTab.TASKS else when {
        source.startsWith("devices/") || source.startsWith("sensors/") || source.startsWith("pairing/") ||
            source.startsWith("network/") || source.startsWith("wifi/") -> GeoTab.DEVICES
        source.startsWith("transfers/") || source.startsWith("upload/") || source.startsWith("storage/") -> GeoTab.TRANSFERS
        source.startsWith("settings/") || source.startsWith("dev/") || source.startsWith("alerts/") -> GeoTab.SETTINGS
        else -> GeoTab.TASKS
    },
    navigation = !host || !(source.startsWith("pairing/") || source.startsWith("survey/SurveyRunScreen") ||
        source.startsWith("sensors/CameraPreview") || source.startsWith("field/ActiveRunScreen")),
    media = host && (source.startsWith("pairing/") || source.startsWith("sensors/CameraPreview")),
    camera = host && source.startsWith("sensors/CameraPreview"),
    activeRun = host && source.startsWith("field/ActiveRunScreen"),
    panes = !host || source.startsWith("field/ActiveRunScreen") || source.startsWith("storage/DirectServer"),
    prepare = prepare) {
    if (!host) Work()
    content()
}

@Composable private fun DemoReceipt() {
    GeoReceipt(GeoReceiptState(
        collection = GeoReceiptItem("종료 확인", Tone.SUCCESS, "12분 34초", true),
        storage = GeoReceiptItem("장치 저장 확인", Tone.SUCCESS, "1,280개 · 12.8 GB", true),
        server = GeoReceiptItem("내용 일치", Tone.SUCCESS, "검수용 수신 서버 · 12:30", true),
        report = GeoReceiptItem("보고 전", Tone.PENDING)))
}

@Composable private fun DevicePicker() {
    GeoSheet("장치 선택", {}) {
        WorkDeviceChoiceRow(G.NAME, true, {}, "연결됨", Tone.SUCCESS, "55°C · 저장 564 GB 남음")
        WorkDeviceChoiceRow("GEO-X-2 · 보행교 주변 정밀측량 장치", false, {}, "오프라인", Tone.UNKNOWN, "마지막 응답 12:10")
        GeoButton("장치 관리", {}, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
    }
}

internal fun r8OverlayScenes(): List<R8GalleryScene> = buildList {
    listOf("work" to "navigation/WorkGraph.kt", "transfer" to "navigation/TransferGraph.kt").forEach { (id, path) ->
        add(overlayScene("$id-device-picker", "$path · 장치 선택", "그래프 구성 재현 · 실제 WorkDeviceChoiceRow") { DevicePicker() })
    }
    add(overlayScene("work-report", "navigation/WorkGraph.kt · 완료 보고 확인", "그래프 구성 재현 · 수집 대상과 서버 수신 근거") {
        GeoConfirmDialog("중앙로 동측 완료를 보고할까요?", "서버 수신을 확인했습니다. GEO& Work 상태가 '완료'로 바뀝니다.",
            "완료 보고", {}, {})
    })
    add(overlayScene("project-picker", "survey/SurveyRunScreen.kt · 프로젝트 선택", "실제 준비 화면에서 열기", host = true,
        prepare = { click(it, G.project.label) }) { Prepare() })
    add(overlayScene("prepare-relay", "survey/SurveyRunScreen.kt · 보정 중계 정보", "실제 준비 화면에서 열기", host = true,
        prepare = { click(it, "보정 정보 중계") }) {
        Prepare(G.survey.copy(preparationTelemetry = PreparationTelemetry(
            deviceId = G.DEVICE, status = G.status, correctionRelay = "중계 중")))
    })
    listOf(GeoStartPhase.CHECKING, GeoStartPhase.PASSED, GeoStartPhase.FAILED).forEach { phase ->
        add(overlayScene("survey-start-${phase.name.lowercase()}", "survey/SurveyRunScreen.kt · SurveyStartSheet", "시작 시트 · ${galleryStateLabel(phase.name.lowercase())}") {
            SurveyStartSheet(G.start(phase), {}, {}, {})
        })
        add(overlayScene("geo-start-${phase.name.lowercase()}", "components/geo/GeoStartSheet.kt · GeoStartSheet", "시작 공통 부품 · ${galleryStateLabel(phase.name.lowercase())}") {
            GeoStartSheet(G.start(phase), {}, {}, {})
        })
    }
    listOf("list", "empty", "loading", "error").forEach { kind ->
        add(overlayScene("task-choice-$kind", "survey/TaskChoiceSheet.kt · TaskChoiceSheet(state)", "상태 오버로드 · ${galleryStateLabel(kind)}") {
            TaskChoices(when (kind) {
                "empty" -> G.pipelines.copy(pipelines = emptyList())
                "loading" -> G.pipelines.copy(pipelines = emptyList(), isLoading = true)
                "error" -> G.pipelines.copy(pipelines = emptyList(), error = "장치 작업을 불러오지 못했습니다.")
                else -> G.pipelines
            })
        })
    }
    add(overlayScene("stop-run", "field/ActiveRunScreen.kt · 수집 종료; map/GeoMapRoute.kt · 수집 종료", "실제 수집 화면의 종료 확인 · 지도 경로도 같은 대상",
        host = true, prepare = { click(it, "수집 종료") }) { Collect() })
    add(overlayScene("active-relay", "field/ActiveRunScreen.kt · 보정 중계 정보", "같은 GeoInfoDialog 구성 · 상태와 장치 예시") {
        GeoInfoDialog("보정 정보 중계", {}) {
            Text(G.NAME, style = LocalGeoType.current.titleSm, color = LocalGeoColors.current.text)
            GeoBadge("중계 중", Tone.SUCCESS)
        }
    })
    add(overlayScene("quality", "field/QualityPresentation.kt · 위치 품질", "실제 위치 품질 부품에서 열기", host = true,
        prepare = { click(it, "위치 품질 자세히") }) {
        Column(Modifier.padding(24.dp)) { RunQualityEvidence(quality = null) }
    })
    add(overlayScene("map-quality", "map/GeoMapPanel.kt · 위치 품질", "순수 지도 패널에서 열기 · 지도 SDK와 위치 ViewModel 없음", host = true,
        prepare = { click(it, "위치 품질 자세히") }) {
        com.example.jetsoncontroller.ui.map.GeoMapPanel(
            mode = com.example.jetsoncontroller.ui.map.GeoMapMode.RUN,
            data = com.example.jetsoncontroller.ui.map.GeoMapData(), gnss = GnssSensorStatus(),
            expanded = true, onToggle = {}, phoneShown = false, onPhoneShown = {}, deviceName = G.NAME)
    })
    add(overlayScene("connection-method", "devices/ConnectMethodSheet.kt · ConnectMethodSheet", "장치 연결 방법 선택") { Connections() })
    add(overlayScene("connection-direct-confirm", "devices/ConnectMethodSheet.kt · 직접 연결", "실제 연결 방법 시트에서 열기", host = true,
        prepare = { click(it, "장치에 직접 연결") }) { Connections() })
    listOf("reboot" to "재부팅", "shutdown" to "전원 종료", "forget" to "등록 해제").forEach { (id, label) ->
        add(overlayScene("device-$id", "devices/DeviceDetailScreen.kt · 장치 위험 동작($label)", "실제 장치 상세 메뉴에서 열기", host = true,
            prepare = { click(it, label) }) { DeviceDetail() })
    }
    add(overlayScene("manual-code", "pairing/QrScannerScreen.kt · 수동 등록 코드", "실제 권한 안내 화면에서 수동 등록 열기 · 코드 비움", host = true,
        prepare = { click(it, "코드 직접 입력") }) { Scanner() })
    listOf(PairingPhase.QR_SCANNED, PairingPhase.CONNECTING, PairingPhase.AUTHENTICATING, PairingPhase.ERROR).forEach { phase ->
        add(overlayScene("pairing-${phase.name.lowercase()}", "pairing/PairingScreen.kt · PairingScreen / 등록 진행", "등록 단계 · ${galleryStateLabel(phase.name.lowercase())}", host = true) {
            PairingScreen(PairingUiState(phase = phase, displayDeviceName = G.NAME,
                errorMessage = if (phase == PairingPhase.ERROR) "장치 응답을 받지 못했습니다." else null), {}, {}, {})
        })
    }
    add(overlayScene("capture-location", "sensors/CameraPreviewScreen.kt · 캡처 저장 위치", "실제 저장 위치 시트 · 선택값은 메모리 기본값 · 저장 동작 없음",
        host = true, prepare = { icon(it, "캡처 저장 위치") }) { Camera() })
    add(overlayScene("wifi-apply", "network/NetworkSettingsScreen.kt · Wi-Fi 적용", "실제 Wi-Fi 폼에서 열기", host = true,
        prepare = { click(it, "숨겨진 네트워크 추가"); click(it, "적용") }) { Network() })
    add(overlayScene("wifi-info", "network/NetworkSettingsScreen.kt · 연결 정보", "실제 Wi-Fi 메뉴에서 열기", host = true,
        prepare = { icon(it, "더 보기"); click(it, "연결 상태") }) { Network() })
    add(overlayScene("wifi-direct-confirm", "wifi/WifiDirectScreen.kt · 직접 연결 확인", "실제 주변 장치 행에서 열기", host = true,
        prepare = { click(it, G.NAME); click(it, "직접 연결") }) { Direct() })
    add(overlayScene("transfer-cancel", "transfers/TransfersHomeScreen.kt → upload/UploadProgressScreen.kt · 취소", "전송 홈에서 이동하는 진행 상세 하단에서 열기", host = true,
        prepare = { click(it, "전송 취소") }) { Progress() })
    add(overlayScene("queue-delete", "upload/UploadQueueScreen.kt · 기록 삭제", "실제 전송 기록 메뉴에서 열기", host = true,
        prepare = { icon(it, "중앙로 동측 메뉴"); click(it, "기록 삭제") }) { Queue() })
    add(overlayScene("progress-cancel", "upload/UploadProgressScreen.kt · 취소", "실제 진행 상세에서 열기", host = true,
        prepare = { click(it, "전송 취소") }) { Progress() })
    add(overlayScene("progress-delete", "upload/UploadProgressScreen.kt · 정리/기록 삭제", "실제 진행 상세 메뉴에서 열기", host = true,
        prepare = { icon(it, "더 보기"); click(it, "기록 삭제") }) { Progress(G.completed) })
    add(overlayScene("progress-cleanup", "upload/UploadProgressScreen.kt · 원본 정리", "실제 수신 검증 완료 화면에서 열기", host = true,
        prepare = { click(it, "장치 원본 정리") }) { Progress(G.verified) })
    add(overlayScene("progress-report", "upload/UploadProgressScreen.kt · 완료 보고", "실제 수신 검증 완료 화면에서 열기", host = true,
        prepare = { click(it, "완료 보고") }) { Progress(G.verified, report = true) })
    add(overlayScene("result-report", "field/RunResultScreen.kt · 결과 확인", "공통 대화상자 구성 재현 · 수집 결과 영수증") {
        GeoConfirmDialog("중앙로 동측을 완료로 보고할까요?", "GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다.",
            "완료 보고", {}, {}, facts = { DemoReceipt() })
    })
    add(overlayScene("file-trash", "storage/FileBrowser.kt · 휴지통 이동", "실제 파일 선택·메뉴에서 열기", host = true,
        prepare = { rule ->
            click(rule, "보낼 파일 선택")
            rule.onNode(hasContentDescription("${G.entries[1].name} 선택") and isToggleable(), useUnmergedTree = true)
                .performScrollTo().assertIsOff().performClick().assertIsOn()
            icon(rule, "파일 메뉴")
            click(rule, "휴지통으로 이동")
        }) {
        Files(G.storage.copy(entries = listOf(G.entries[1])))
    })
    add(overlayScene("server-trash", "storage/DirectServerScreen.kt · 휴지통 이동", "서버 수신 결과 메뉴에서 열기", host = true,
        prepare = { icon(it, "서버 작업 메뉴"); click(it, "서버 휴지통으로 이동") }) { Server(G.server.copy(selectedJob = G.serverJob)) })
    add(overlayScene("server-job-info", "storage/DirectServerScreen.kt · 작업 정보", "실제 서버 휴지통 항목의 정보 대화상자", host = true,
        prepare = { click(it, "서문로 서측") }) { Server(G.server.copy(section = DirectServerSection.TRASH)) })
    add(overlayScene("server-receipt", "storage/DirectServerScreen.kt · ReceiptDialog / 수신 영수증", "실제 비공개 ReceiptDialog를 소유 화면의 상태로 열기", host = true) {
        Server(G.server.copy(receipt = G.receipt))
    })
    add(overlayScene("local-trash-empty", "storage/LocalTrashScreen.kt · 휴지통 비우기", "실제 휴지통 메뉴에서 열기", host = true,
        prepare = { icon(it, "휴지통 메뉴"); click(it, "휴지통 비우기") }) { Trash() })
    add(overlayScene("server-picker", "storage/ServerStorageScreen.kt · 서버 선택", "실제 서버 목록의 선택 필드에서 열기", host = true,
        prepare = { click(it, G.targets.first().label) }) { ServerFiles() })
    add(overlayScene("server-storage-delete", "storage/ServerStorageScreen.kt · 영구 삭제", "수신 결과 상세의 관리 묶음에서 열기", host = true,
        prepare = { click(it, "영구 삭제") }) {
        ServerFiles(G.serverStorage.copy(selectedSession = G.librarySession, entries = listOf(G.entries[1])))
    })
    add(overlayScene("theme-mode", "settings/ThemeScreen.kt · 모드", "동일 CHOICE 구성 재현 · 테마 저장소 사용 안 함") {
        GeoSheet("모드", {}) { ThemeMode.entries.forEach { GeoRadioRow(it.label, it == ThemeMode.SYSTEM, {}) } }
    })
    add(overlayScene("server-edit", "settings/ServerConnectionScreen.kt · 서버 편집(다시 로그인)", "실제 서버 연결 화면에서 열기 · 토큰 비움", host = true,
        prepare = { click(it, "다시 로그인") }) { ServerConnection(login = true) })
    add(overlayScene("alerts-clear", "alerts/AlertCenterScreen.kt · 알림 삭제", "실제 알림 메뉴에서 열기", host = true,
        prepare = { icon(it, "알림 옵션"); click(it, "모두 지우기") }) { Alerts() })
    add(overlayScene("dev-choice", "dev/DevUi.kt · 개발자 선택값", "DevChoice와 같은 CHOICE 구성 · 1.3 글꼴에도 별도 창") {
        GeoSheet("제어 방식", {}) { listOf("자동", "수동").forEach { GeoRadioRow(it, it == "수동", {}) } }
    })
    add(overlayScene("terminal-execute", "dev/DevTerminalScreen.kt · 명령 실행", "실제 입력 후 확인만 열기 · 실행 콜백은 비움", host = true,
        prepare = { rule -> rule.onNode(hasSetTextAction()).performTextInput("pwd"); Espresso.closeSoftKeyboard(); click(rule, "명령 실행") }) { Terminal() })
    add(overlayScene("profile-delete", "dev/DevServerScreens.kt · 서버 삭제", "실제 프로필 편집 화면에서 열기", host = true,
        prepare = { click(it, "프로필 삭제") }) { ProfileEditor() })
    add(overlayScene("server-trash-empty", "dev/DevServerScreens.kt · 서버 휴지통 비우기", "실제 서버 휴지통에서 열기", host = true,
        prepare = { click(it, "표시된 1개 비우기") }) { ServerTrash() })
    add(overlayScene("pipeline-remove", "pipelines/PipelineScreens.kt · 등록 해제", "실제 개발자 작업 상세에서 열기", host = true,
        prepare = { click(it, "등록 해제") }) { Pipelines(details = true) })
    add(overlayScene("config-reload", "pipelines/PipelineScreens.kt · 다시 불러오기", "실제 작업 설정에서 열기", host = true,
        prepare = { click(it, "장치 설정 다시 불러오기") }) { Config() })
    add(overlayScene("log-picker", "pipelines/PipelineScreens.kt · 실행 로그 선택", "실제 로그 선택 필드에서 열기", host = true,
        prepare = { rule ->
            val picker = rule.onAllNodes(hasClickAction() and hasText("실행 로그", substring = true))
            picker[0].performClick()
        }) { Logs() })
    add(overlayScene("policy-root", "pipelines/PipelinePolicyScreen.kt · 결과 저장소", "실제 정책 저장소 필드에서 열기", host = true,
        prepare = { click(it, "선택 안 됨") }) { Policy() })
    add(overlayScene("policy-reload", "pipelines/PipelinePolicyScreen.kt · 다시 불러오기", "실제 수집 정책에서 열기", host = true,
        prepare = { click(it, "장치 설정 다시 불러오기") }) { Policy() })
    add(overlayScene("upload-target-delete", "upload/UploadTargetSettingsScreen.kt · 서버 삭제", "실제 전송 서버 편집에서 열기", host = true,
        prepare = { click(it, "서버 삭제") }) { TargetEditor() })
    GeoSheetKind.entries.forEach { kind ->
        add(overlayScene("common-sheet-${kind.name.lowercase()}", "components/geo/GeoSheets.kt · GeoSheet", "공통 시트 · ${kind.name}") {
            GeoSheet("중앙로 동측 검수", {}, kind = kind,
                subtitle = "도심 도로 시설물 정밀측량 · GEO-X-1",
                footer = if (kind == GeoSheetKind.FORM) ({ GeoSheetFooter(GeoOverlayAction("저장", {}), GeoOverlayAction("취소", {})) }) else null) {
                when (kind) {
                    GeoSheetKind.CHOICE -> { GeoRadioRow("중앙로 동측", true, {}); GeoRadioRow("교차로에서 보행교까지 이어지는 긴 검수 구간 이름", false, {}) }
                    GeoSheetKind.FORM -> GeoTextField("중앙로 동측", {}, "구간 이름")
                    GeoSheetKind.INFO -> { DemoReceipt(); GeoCopyableValue(G.FILE, label = "원본 파일명") }
                }
            }
        })
    }
    add(overlayScene("full-screen-fallback", "components/geo/GeoSheets.kt · OverlayFullScreenDialog", "전체 화면 fallback 직접 호출 · 내부 스크롤과 고정 footer") {
        OverlayFullScreenDialog(title = "긴 수집 정보 검수", subtitle = G.NAME, onDismissRequest = {},
            footer = { GeoSheetFooter(GeoOverlayAction("저장", {}), GeoOverlayAction("취소", {})) }) {
            repeat(20) { GeoFactRow("확인 항목 ${it + 1}", "교차로에서 보행교까지 이어지는 중앙로 동측의 긴 검수 구간 이름") }
        }
    })
    add(overlayScene("common-confirm", "components/geo/GeoDialogs.kt · GeoConfirmDialog / OverlayDialog", "공통 위험 확인 · 실제 내부 OverlayDialog 경유") {
        GeoConfirmDialog("중앙로 동측 파일을 영구 삭제할까요?", "선택한 1,280개 파일을 지웁니다. 되돌릴 수 없습니다.", "영구 삭제", {}, {},
            kind = GeoConfirmKind.DANGER, facts = { GeoCopyableValue(G.FILE, label = "원본 파일명") })
    })
    add(overlayScene("common-info", "components/geo/GeoDialogs.kt · GeoInfoDialog / OverlayDialog", "공통 정보 · 실제 내부 OverlayDialog 경유") {
        GeoInfoDialog("중앙로 동측 수집 정보", {}) { DemoReceipt(); GeoCopyableValue(G.FILE, label = "원본 파일명") }
    })
}

/** These rows are intentionally recorded rather than silently replaced with look-alike screens. */
internal fun r8GalleryOmissions(group: String): List<Pair<String, String>> = when (group) {
    "device" -> listOf(
        "map/GeoMapScreen.kt · GeoMapScreen" to "내부 MobileLocationViewModel 생성과 지도 SDK 접근을 주입할 수 없음. ViewModel·네트워크 금지 조건에 따라 전체 지도 제외. 순수 GeoMapPanel의 품질 시트는 overlay에서 촬영.",
        "sensors/GnssMapScreen.kt · GnssMapScreen" to "GeoMapScreen을 호출하므로 같은 위치 ViewModel·지도 SDK 의존.",
        "diagnostics/ConnectionDiagnosticsScreen.kt · ConnectionDiagnosticsScreen" to "상태 인자가 없고 전역 ConnectionDiagnostics.health를 구독함. 예시 상태 주입 불가.",
        "pairing/QrScannerScreen.kt · 실제 카메라 스캐너" to "권한 허용 상태는 CameraX와 ML Kit를 시작함. 외부 입력 없이 권한 안내·수동 등록만 촬영."
    )
    "settings" -> listOf("settings/ThemeScreen.kt · ThemeScreen" to "내부 rememberAppearanceStore가 실제 Activity의 SharedPreferences를 읽음. 상태 주입 지점 없이 저장소 금지 조건을 지킬 수 없어 제외. 모드 시트는 동일 공통 부품으로 별도 촬영.")
    "overlay" -> listOf(
        "survey/TaskChoiceSheet.kt · TaskChoiceSheet(viewModel)" to "ViewModel 오버로드 금지. 같은 화면을 그리는 state 오버로드는 모든 목록 상태 촬영.",
        "map/GeoMapWindow.kt · 전체 지도 Dialog" to "내부 GeoMapScreen의 위치 ViewModel과 지도 SDK 의존. 공통 전체 화면 fallback은 별도 촬영."
    )
    "run" -> listOf("field/ActiveRunScreen.kt · mapCard" to "외부 지도 소유자의 ViewModel·지도 SDK를 생성하지 않도록 mapCard 슬롯은 비움. 실제 진행 제어와 GeoPaneLayout은 촬영.")
    "startup" -> listOf("zone · connection · dashboard" to "독립 화면·시트·대화상자 선언이 없는 점검표 행. 소유 화면의 예시 상태와 연결 표시에서 간접 검수.")
    else -> emptyList()
}
