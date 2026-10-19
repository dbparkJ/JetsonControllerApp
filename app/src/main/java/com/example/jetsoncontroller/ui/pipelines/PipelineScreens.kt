package com.example.jetsoncontroller.ui.pipelines

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.model.Formats

@Composable
fun PipelineListScreen(
    state: PipelineUiState, onBack: () -> Unit, onRefresh: () -> Unit, onAdd: () -> Unit,
    onControl: (ManagedPipeline, String) -> Unit, onRemove: (ManagedPipeline) -> Unit,
    onLogs: (ManagedPipeline) -> Unit, onConfig: (ManagedPipeline) -> Unit,
    onOutput: (ManagedPipeline) -> Unit, onSectionSelected: (ControlSection) -> Unit,
    onClearMessage: () -> Unit, deviceName: String = "선택한 장치", unreadCount: Int = 0,
    onAlerts: () -> Unit = {}, onDetails: (ManagedPipeline) -> Unit = onLogs,
    detailId: String? = null, startCapability: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(), onHistory: () -> Unit = {},
    onPrepareRun: (ManagedPipeline) -> Unit = {}, developerModeEnabled: Boolean = false,
    onPolicy: (ManagedPipeline) -> Unit = onConfig, onRelay: () -> Unit = {}
) {
    var removal by remember(state.deviceId, state.controlAvailable) { mutableStateOf<ManagedPipeline?>(null) }
    val fresh = tasksAreFresh(state.controlAvailable, state.observedAtMillis, nowMillis)
    val pipeline = state.pipelines.firstOrNull { it.id == detailId }
    val available = fresh && state.busyPipelineId == null
    var technical by rememberTechnicalInfo()
    removal?.let { frozen ->
        GeoConfirmDialog("${frozen.label} 등록을 해제할까요?",
            "${deviceName}의 실행 서비스가 멈춥니다. 저장된 실행 기록과 측정 파일은 그대로입니다.", "등록 해제",
            { removal = null; if (available && state.pipelines.any { it.id == frozen.id }) onRemove(frozen) },
            { removal = null }, kind = GeoConfirmKind.DANGER, confirmEnabled = available,
            disabledReason = "장치의 현재 상태를 확인한 뒤 등록을 해제할 수 있습니다.")
    }
    val footer: (@Composable () -> Unit)? = if (pipeline != null) ({
        val running = pipeline.state in setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.RETRYING, PipelineState.WAITING_FOR_TIME_SYNC)
        val failed = pipeline.state == PipelineState.FAILED
        GeoButton(if (running) "중지 요청" else if (failed) "다시 시작" else "수집 준비", {
            if (running) onControl(pipeline, "stop") else onPrepareRun(pipeline)
        }, Modifier.fillMaxWidth(), enabled = available && pipeline.id !in state.pendingActions &&
            (running || (startCapability && pipeline.state in setOf(PipelineState.STOPPED, PipelineState.FAILED))), actionArea = true,
            disabledReason = if (!fresh) "장치의 현재 상태를 확인한 뒤 실행할 수 있습니다." else "요청 결과를 확인하고 있습니다.")
    }) else null
    DevPage(if (developerModeEnabled) "작업 관리" else "수집 작업 선택", onBack,
        actions = { if (detailId == null && developerModeEnabled) GeoIconButton(GeoIcons.add, "작업 등록", onAdd, chrome = true) }, footer = footer) {
        DevFeedback(state.error, true)
        DevFeedback(state.message)
        if (state.isLoading) DevText("확인 중")
        if (detailId == null) {
            if (state.pipelines.isEmpty() && !state.isLoading) DevEmpty("이 장치에 등록된 작업이 없습니다.", "작업 등록은 관리자에게 요청하세요.")
            state.pipelines.forEach { item ->
                GeoRow(item.label, { onDetails(item) }, meta = taskStateLabel(item, fresh, state.pendingActions[item.id]))
            }
            GeoButton("다시 조회", onRefresh, style = GeoButtonStyle.Tertiary, enabled = !state.isLoading)
            if (developerModeEnabled) GeoRow("작업 이력", onHistory)
        } else if (pipeline == null) DevEmpty("작업을 찾을 수 없습니다", "장치의 작업 목록을 다시 확인하세요.", "다시 시도", onRefresh)
        else {
            DevIdentity(pipeline.label, "$deviceName · 마지막 실행 ${devTimestamp(pipeline.execution?.startedAt)}",
                status = taskStateLabel(pipeline, fresh, state.pendingActions[pipeline.id]),
                tone = when {
                    !fresh -> StatusTone.UNKNOWN
                    pipeline.id in state.pendingActions -> StatusTone.PENDING
                    pipeline.state == PipelineState.FAILED -> StatusTone.ERROR
                    pipeline.state == PipelineState.RUNNING -> StatusTone.INFO
                    else -> StatusTone.UNKNOWN
                })
            Spacer(Modifier.height(8.dp))
            GeoSection("실행") {
                GeoFactRow("종료 코드", (pipeline.execution?.exitCode ?: pipeline.lastExitCode).toString(), numeric = true)
                GeoFactRow("시작 전 점검", when (pipeline.execution?.storagePreflight) { "PASSED", "passed" -> "통과"; "FAILED", "failed" -> "실패"; else -> "미확인" })
                if (developerModeEnabled) GeoSwitchRow("부팅 시 실행", pipeline.enabled,
                    { onControl(pipeline, if (it) "enable" else "disable") }, enabled = available,
                    disabledReason = "장치의 현재 상태를 확인한 뒤 바꿀 수 있습니다.")
            }
            GeoSection("설정과 기록") {
                GeoRow("실행 로그", { onLogs(pipeline) })
                if (developerModeEnabled) {
                    GeoRow("작업 설정", { onConfig(pipeline) })
                    GeoRow("수집 정책", { onPolicy(pipeline) })
                }
                GeoRow("저장 결과", { onOutput(pipeline) }, value = pipeline.outputPath,
                    enabled = pipeline.outputRootId != null && pipeline.outputPath != null,
                    disabledReason = "저장 결과 위치가 없습니다.")
                GeoRow("보정 정보 중계", onRelay, value = if (state.mobileRtkRelay.active) "중계 중" else "꺼짐")
            }
            GeoTechInfo(listOf(GeoTechnicalValue("실행 ID", pipeline.execution?.runId.orEmpty()),
                GeoTechnicalValue("로그 ID", pipeline.execution?.logId.orEmpty()), GeoTechnicalValue("실행 파일", pipeline.entrypoint),
                GeoTechnicalValue("설정 파일", pipeline.config), GeoTechnicalValue("리비전", pipeline.sourceRevision),
                GeoTechnicalValue("보정 수신량", state.mobileRtkRelay.bytesFromCaster.toString())), developerModeEnabled, technical)
            if (developerModeEnabled) GeoSection("관리", danger = true) {
                DevText("등록을 해제하면 ${deviceName}의 실행 서비스가 멈춥니다.")
                GeoButton("등록 해제", { removal = pipeline }, Modifier.fillMaxWidth(), style = GeoButtonStyle.DangerOutline, enabled = available)
            }
        }
    }
}

@Composable
fun PipelineEditorScreen(state: PipelineUiState, onBack: () -> Unit, onPick: (PipelinePickerTarget) -> Unit,
    onLabelChange: (String) -> Unit, onAutostartChange: (Boolean) -> Unit, onRegister: () -> Unit) {
    DevPage("작업 등록", onBack, footer = {
        GeoButton("작업 등록", onRegister, Modifier.fillMaxWidth(), enabled = state.controlAvailable && state.draft.canSubmit &&
            state.discoveredFolder != null && !state.isLoading && !state.isDiscoveringFolder, actionArea = true,
            disabledReason = "장치에 연결하고 표시 이름과 작업 폴더를 확인하세요.")
    }) {
        DevFeedback(state.error, true)
        GeoSection("작업 정보", first = true) { GeoTextField(state.draft.label, onLabelChange, "표시 이름") }
        GeoSection("실행 소스") {
            GeoPickerField("작업 폴더", selectionLabel(state.draft.repositoryRoot, state.draft.repositoryPath),
                { onPick(PipelinePickerTarget.REPOSITORY) }, enabled = !state.isLoading, disabledReason = "등록하고 있습니다.")
            if (state.isDiscoveringFolder) DevText("확인 중")
            state.discoveredFolder?.let {
                DevText("작업 폴더에서 실행 정보를 찾았습니다.")
                DevText("${it.pipelineId} · ${it.entrypoint} · ${it.config}", mono = true)
                DevText("결과 폴더는 자동으로 정합니다.", secondary = true)
            }
        }
        GeoSection("실행 설정") {
            GeoSwitchRow("부팅 시 자동 실행", state.draft.autostart, onAutostartChange,
                description = "꺼 두면 장치를 다시 켠 뒤 상태를 확인하고 직접 시작합니다.",
                enabled = !state.isLoading, disabledReason = "등록하고 있습니다.")
        }
    }
}

@Composable
fun PipelinePickerScreen(roots: List<RemoteRoot>, state: PipelinePickerState, onBack: () -> Unit,
    onRefresh: () -> Unit, onRootClick: (RemoteRoot) -> Unit, onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit, onSelectCurrentDirectory: () -> Unit) {
    val directory = state.target == PipelinePickerTarget.REPOSITORY || state.target == PipelinePickerTarget.VIRTUALENV
    val visible = state.entries.filter { it.type == RemoteEntryType.DIRECTORY || fileMatches(it, state.target) }
    DevPage("폴더 선택", onBack, footer = if (directory && state.root != null) ({
        GeoButton("이 폴더 선택", onSelectCurrentDirectory, Modifier.fillMaxWidth(), enabled = !state.isLoading, actionArea = true)
    }) else null) {
        DevText(selectionLabel(state.root, state.currentPath))
        if (state.isLoading) DevText("확인 중")
        if (state.error != null) DevEmpty("폴더를 불러오지 못했습니다", state.error, "다시 시도", onRefresh)
        if (state.root == null) roots.forEach { root -> GeoRow(root.label, { onRootClick(root) }, enabled = !state.isLoading,
            disabledReason = "폴더를 확인하고 있습니다.") }
        else {
            GeoSection("폴더", count = "${visible.size}개") {
                visible.forEach { entry -> GeoRow(entry.name, {
                    if (entry.type == RemoteEntryType.DIRECTORY) onDirectoryClick(entry) else onFileClick(entry)
                }, enabled = !state.isLoading, disabledReason = "폴더를 확인하고 있습니다.") }
            }
        }
        if (!state.isLoading && state.error == null && (if (state.root == null) roots.isEmpty() else visible.isEmpty()))
            DevEmpty("선택할 폴더가 없습니다", "이 위치에는 폴더가 없습니다.")
    }
}

@Composable
fun PipelineLogScreen(state: PipelineUiState, onBack: () -> Unit, onRefresh: () -> Unit, onLogSelected: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    if (picker) GeoSheet("실행 로그 선택", { picker = false }) {
        state.logFiles.forEach { file ->
            GeoRadioRow(logKind(file, state), file.id == state.selectedLogId,
                { picker = false; onLogSelected(file.id) }, description = "${devTimestamp(file.startedAt)} 시작 · ${Formats.size(file.sizeBytes)}")
        }
    }
    DevFrame("실행 로그", onBack) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            GeoPickerField("실행 로그", state.logFiles.firstOrNull { it.id == state.selectedLogId }?.let { "${logKind(it, state)} · ${devTimestamp(it.startedAt)} 시작" } ?: "선택 안 됨",
                { picker = true }, enabled = state.logFiles.isNotEmpty(), disabledReason = "저장된 실행 로그가 없습니다")
            DevFeedback(state.error, true)
            GeoButton("다시 조회", onRefresh, style = GeoButtonStyle.Tertiary, enabled = !state.detailLoading)
        }
        when {
            state.detailLoading && state.logFiles.isEmpty() -> DevText("확인 중")
            state.logFiles.isEmpty() -> Column(Modifier.padding(horizontal = 24.dp)) {
                DevEmpty("저장된 실행 로그가 없습니다", "작업을 시작하면 실행마다 로그가 따로 남습니다.")
            }
            state.logContent.isEmpty() -> Column(Modifier.padding(horizontal = 24.dp)) {
                DevEmpty("로그 내용이 없습니다", "선택한 실행에서는 아직 결과가 기록되지 않았습니다.")
            }
            else -> key(state.selectedLogId) {
                GeoLogView(state.logContent.lines().mapIndexed { index, line -> GeoLogLine(index.toString(), line) },
                    state.logLive, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
fun PipelineConfigScreen(state: PipelineUiState, onBack: () -> Unit, onValueChange: (String, String) -> Unit,
    onSave: () -> Unit, onReload: () -> Unit = {}) {
    var reload by remember(state.deviceId, state.controlAvailable) { mutableStateOf(false) }
    if (reload) GeoConfirmDialog("장치 설정을 다시 불러올까요?", "작성 중인 내용을 장치의 현재 설정으로 바꿉니다.", "다시 불러오기",
        { reload = false; if (state.controlAvailable && !state.configSaving) onReload() }, { reload = false },
        dismissLabel = "초안 유지", confirmEnabled = state.controlAvailable && !state.configSaving,
        disabledReason = "장치에 연결되고 저장이 끝나야 다시 불러올 수 있습니다.")
    DevPage("작업 설정", onBack, footer = {
        GeoButton("설정 저장", onSave, Modifier.fillMaxWidth(), enabled = state.controlAvailable && !state.configNeedsReview &&
            !state.detailLoading && !state.configSaving && state.configHasChanges && state.configValuesValid,
            actionArea = true, disabledReason = if (state.configNeedsReview) "장치의 설정을 다시 불러온 뒤 저장할 수 있습니다." else "변경한 설정 값을 확인하세요.")
    }) {
        DevFeedback(state.error, true)
        DevFeedback(state.message)
        DevText(state.configPath, mono = true)
        if (state.configNeedsReview) {
            DevText("장치에서 설정이 바뀌었습니다. 장치 설정을 다시 불러와 확인하세요.")
            GeoButton("장치 설정 다시 불러오기", { reload = true }, style = GeoButtonStyle.Tertiary, enabled = state.controlAvailable)
        }
        if (state.detailLoading) DevText("확인 중")
        else if (state.configFields.isEmpty() && state.error == null) DevEmpty("바꿀 수 있는 설정 값이 없습니다",
            "이 작업의 설정 파일에는 앱에서 바꿀 수 있는 키와 값이 없습니다.")
        else GeoSection("설정 값") {
            state.configFields.forEach { field ->
                val enabled = state.controlAvailable && !state.detailLoading && !state.configSaving
                if (field.type == PipelineConfigValueType.BOOLEAN) GeoSwitchRow(field.label, field.value == "true",
                    { onValueChange(field.path, it.toString()) }, enabled = enabled, disabledReason = "장치 연결과 저장 상태를 확인하세요.")
                else {
                    GeoTextField(field.value, { onValueChange(field.path, it.take(4096)) }, field.label,
                        enabled = enabled, disabledReason = "장치 연결과 저장 상태를 확인하세요.",
                        singleLine = field.type != PipelineConfigValueType.STRING,
                        numeric = field.type in setOf(PipelineConfigValueType.INTEGER, PipelineConfigValueType.DECIMAL),
                        error = if (configFieldValueValid(field.type, field.value)) null else "유효한 값을 입력하세요.")
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun selectionLabel(root: RemoteRoot?, path: String): String =
    listOfNotNull(root?.label, path.takeIf { it.isNotBlank() }).joinToString(" › ").ifEmpty { "선택 안 됨" }

private fun fileMatches(entry: RemoteFileEntry, target: PipelinePickerTarget?): Boolean = when (target) {
    PipelinePickerTarget.ENTRYPOINT -> entry.name.endsWith(".py", true)
    PipelinePickerTarget.CONFIG -> entry.name.endsWith(".yaml", true) || entry.name.endsWith(".yml", true)
    else -> false
}

private fun logKind(file: PipelineLogFile, state: PipelineUiState): String = when {
    file.active -> "현재 실행"
    file.id == state.logFiles.firstOrNull { !it.active }?.id -> "최근 실행"
    else -> "보관 로그"
}
