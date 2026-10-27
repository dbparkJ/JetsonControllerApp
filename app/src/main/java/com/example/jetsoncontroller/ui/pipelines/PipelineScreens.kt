package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.ui.dev.DevSection as GeoSection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow

@Composable
fun PipelineListScreen(
    state: PipelineUiState, onBack: () -> Unit, onRefresh: () -> Unit, onAdd: () -> Unit,
    onControl: (ManagedPipeline, String) -> Unit, onRemove: (ManagedPipeline) -> Unit,
    onLogs: (ManagedPipeline) -> Unit, onConfig: (ManagedPipeline) -> Unit,
    onOutput: (ManagedPipeline) -> Unit, onSectionSelected: (GeoTab) -> Unit,
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
    val action: GeoButtonAction? = if (pipeline != null) run {
        val running = pipeline.state in setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.RETRYING, PipelineState.WAITING_FOR_TIME_SYNC)
        val failed = pipeline.state == PipelineState.FAILED
        GeoButtonAction(if (running) "중지 요청" else if (failed) "다시 시작" else "수집 준비", {
            if (running) onControl(pipeline, "stop") else onPrepareRun(pipeline)
        }, enabled = available && pipeline.id !in state.pendingActions &&
            (running || (startCapability && pipeline.state in setOf(PipelineState.STOPPED, PipelineState.FAILED))),
            disabledReason = if (!fresh) "장치의 현재 상태를 확인한 뒤 실행할 수 있습니다." else "요청 결과를 확인하고 있습니다.")
    } else if (detailId == null && developerModeEnabled) GeoButtonAction("작업 등록", onAdd) else null
    DevPage(if (developerModeEnabled) "작업 관리" else "수집 작업 선택", onBack,
        actions = { GeoIconButton(GeoIcons.refresh, "작업 목록 다시 조회", onRefresh, enabled = !state.isLoading) },
        primaryAction = action) {
        if (state.pipelines.isNotEmpty()) DevFeedback(state.error, true)
        if (developerModeEnabled && !state.isLoading) state.technicalError?.let { GeoCopyableValue(it, label = "오류 상세") }
        DevFeedback(state.message)
        if (detailId == null) {
            when (pipelineListContent(state.isLoading, state.error, state.pipelines.isNotEmpty())) {
                PipelineDetailContent.LOADING -> DevLoading("작업 목록을 불러오는 중")
                PipelineDetailContent.ERROR -> GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
                    "작업 목록을 불러오지 못했습니다", pipelineFailureDetail(state.error, "작업 목록을 불러오지 못했습니다")), fullScreen = true)
                PipelineDetailContent.EMPTY -> DevEmpty("이 장치에 등록된 작업이 없습니다.",
                    if (developerModeEnabled) "작업 등록을 눌러 이 장치에 작업을 등록하세요." else "작업 등록은 관리자에게 요청하세요.")
                else -> Unit
            }
            if (state.pipelines.isNotEmpty()) GeoSection("등록된 작업", first = true, trailingColumn = GeoRowTrailing.Status) {
              state.pipelines.forEachIndexed { index, item ->
                val status = taskStateLabel(item, fresh, state.pendingActions[item.id])
                        GeoRow(item.label, { onDetails(item) }, meta = taskSupportingText(status),
                            leading = { GeoIconTile(GeoIcons.tabWork) }, divider = index < state.pipelines.lastIndex,
                            badge = { GeoBadge(if (!fresh) "미확인" else status.substringBefore(" · "), when {
                                !fresh -> Tone.UNKNOWN
                                item.id in state.pendingActions -> Tone.PENDING
                                item.state == PipelineState.FAILED -> Tone.ERROR
                                item.state == PipelineState.RUNNING -> Tone.INFO
                                else -> Tone.UNKNOWN
                            }) })
              }
            }
            if (developerModeEnabled) GeoSection("기록", trailingColumn = GeoRowTrailing.None) {
                GeoRow("작업 이력", onHistory, leading = { GeoIconTile(GeoIcons.log) }, divider = false)
            }
        } else if (state.isLoading && pipeline == null) DevLoading("작업 정보를 불러오는 중")
        else if (state.error != null && pipeline == null) GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
            "작업 정보를 불러오지 못했습니다", pipelineFailureDetail(state.error, "작업 정보를 불러오지 못했습니다")), fullScreen = true)
        else if (pipeline == null) DevEmpty("작업을 찾을 수 없습니다", "장치의 작업 목록을 다시 조회하세요.")
        else {
            DevIdentity(pipeline.label, "$deviceName · 마지막 실행 ${devTimestamp(pipeline.execution?.startedAt)}",
                status = taskStateLabel(pipeline, fresh, state.pendingActions[pipeline.id]),
                tone = when {
                    !fresh -> Tone.UNKNOWN
                    pipeline.id in state.pendingActions -> Tone.PENDING
                    pipeline.state == PipelineState.FAILED -> Tone.ERROR
                    pipeline.state == PipelineState.RUNNING -> Tone.INFO
                    else -> Tone.UNKNOWN
                }, statusInDetail = true)
            Spacer(Modifier.height(8.dp))
            GeoSection("실행", first = true, modifier = Modifier.padding(top = 24.dp), insetContent = false) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    GeoFactRow("종료 코드", (pipeline.execution?.exitCode ?: pipeline.lastExitCode).toString(), numeric = true)
                    GeoFactRow("시작 전 점검", when (pipeline.execution?.storagePreflight) { "PASSED", "passed" -> "통과"; "FAILED", "failed" -> "실패"; else -> "미확인" })
                }
                if (developerModeEnabled) GeoSwitchRow("부팅 시 실행", pipeline.enabled,
                    { onControl(pipeline, if (it) "enable" else "disable") }, enabled = available,
                    disabledReason = "장치의 현재 상태를 확인한 뒤 바꿀 수 있습니다.", divider = false)
            }
            GeoSection("설정과 기록", trailingColumn = GeoRowTrailing.Status) {
                GeoRow("실행 로그", { onLogs(pipeline) }, leading = { GeoIconTile(GeoIcons.log) })
                if (developerModeEnabled) {
                    GeoRow("작업 설정", { onConfig(pipeline) }, leading = { GeoIconTile(GeoIcons.tabSettings) })
                    GeoRow("수집 정책", { onPolicy(pipeline) }, leading = { GeoIconTile(GeoIcons.file) })
                }
                GeoRow("저장 결과", { onOutput(pipeline) }, meta = pipeline.outputPath,
                    leading = { GeoIconTile(GeoIcons.folder) },
                    enabled = pipeline.outputRootId != null && pipeline.outputPath != null,
                    disabledReason = "저장 결과 위치가 없습니다.")
                GeoRow("보정 정보 중계", onRelay, leading = { GeoIconTile(GeoIcons.link) }, divider = false,
                    badge = { GeoBadge(if (state.mobileRtkRelay.active) "중계 등록됨" else "꺼짐", if (state.mobileRtkRelay.active) Tone.INFO else Tone.UNKNOWN) })
            }
            GeoTechInfo(listOf(GeoTechnicalValue("실행 ID", pipeline.execution?.runId.orEmpty()),
                GeoTechnicalValue("로그 ID", pipeline.execution?.logId.orEmpty()), GeoTechnicalValue("실행 파일", pipeline.entrypoint),
                GeoTechnicalValue("설정 파일", pipeline.config), GeoTechnicalValue("리비전", pipeline.sourceRevision),
                GeoTechnicalValue("중계 서버 수신 바이트", state.mobileRtkRelay.bytesFromCaster.toString())), developerModeEnabled, technical)
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
    val focus = LocalFocusManager.current
    DevPage("작업 등록", onBack, primaryAction = GeoButtonAction("작업 등록", onRegister,
        enabled = state.controlAvailable && state.draft.canSubmit && state.discoveredFolder != null &&
            !state.isLoading && !state.isDiscoveringFolder,
        disabledReason = "장치에 연결하고 표시 이름과 작업 폴더를 확인하세요.")) {
        DevFeedback(state.error, true)
        GeoSection("작업 정보", first = true) {
            GeoTextField(state.draft.label, onLabelChange, "표시 이름", inputKind = GeoInputKind.TEXT,
                imeAction = ImeAction.Next, keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
        }
        GeoSection("실행 소스") {
            GeoPickerField("작업 폴더", selectionLabel(state.draft.repositoryRoot, state.draft.repositoryPath),
                { onPick(PipelinePickerTarget.REPOSITORY) }, enabled = !state.isLoading, disabledReason = "등록하고 있습니다.")
            if (state.isDiscoveringFolder) DevLoading("작업 폴더의 실행 정보를 불러오는 중")
            state.discoveredFolder?.let {
                DevText("작업 폴더에서 실행 정보를 찾았습니다.")
                GeoCopyableValue(it.pipelineId, label = "작업 ID")
                GeoCopyableValue(it.entrypoint, label = "실행 파일")
                GeoCopyableValue(it.config, label = "설정 파일")
                DevText("결과 폴더는 자동으로 정합니다.", secondary = true)
            }
        }
        GeoSection("실행 설정", insetContent = false) {
            GeoSwitchRow("부팅 시 자동 실행", state.draft.autostart, onAutostartChange,
                description = "꺼 두면 장치를 다시 켠 뒤 상태를 확인하고 직접 시작합니다.",
                enabled = !state.isLoading, disabledReason = "등록하고 있습니다.", divider = false)
        }
    }
}

@Composable
fun PipelinePickerScreen(roots: List<RemoteRoot>, state: PipelinePickerState, onBack: () -> Unit,
    onRefresh: () -> Unit, onRootClick: (RemoteRoot) -> Unit, onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit, onSelectCurrentDirectory: () -> Unit) {
    val directory = state.target == PipelinePickerTarget.REPOSITORY || state.target == PipelinePickerTarget.VIRTUALENV
    val visible = state.entries.filter { it.type == RemoteEntryType.DIRECTORY || fileMatches(it, state.target) }
    DevPage("폴더 선택", onBack, primaryAction = if (directory && state.root != null)
        GeoButtonAction("이 폴더 선택", onSelectCurrentDirectory, enabled = !state.isLoading) else null) {
        GeoButtonActions(buildList {
            add(GeoButtonAction(state.root?.label ?: "저장소", { state.root?.let(onRootClick) ?: onRefresh() },
                style = GeoButtonStyle.Secondary, icon = GeoIcons.folder, enabled = !state.isLoading))
            if (state.currentPath.isNotBlank()) add(GeoButtonAction("상위 폴더", onBack, style = GeoButtonStyle.Secondary))
        })
        if (state.root != null) GeoCopyableValue(state.currentPath.ifBlank { "/" }, label = "현재 경로")
        if (state.isLoading) DevLoading("폴더를 불러오는 중")
        else if (state.error != null) GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
            "폴더를 불러오지 못했습니다", pipelineFailureDetail(state.error, "폴더를 불러오지 못했습니다"), "다시 시도"), fullScreen = true, onAction = onRefresh)
        else if (state.root == null) GeoSection("저장소", trailingColumn = GeoRowTrailing.None) {
            if (roots.isEmpty()) DevEmpty("선택할 저장소가 없습니다", "장치의 저장소를 확인해 주세요.", fullScreen = false)
          roots.forEachIndexed { index, root ->
                GeoRow(root.label, { onRootClick(root) }, enabled = !state.isLoading,
                    leading = { GeoIconTile(GeoIcons.folder) }, divider = index < roots.lastIndex, disabledReason = "폴더를 확인하고 있습니다.")
            }
        }
        else {
            GeoSection("폴더", first = true, modifier = Modifier.padding(top = 24.dp), count = "${visible.size}개",
                trailingColumn = GeoRowTrailing.None) {
                visible.forEach { entry -> GeoRow(com.example.jetsoncontroller.ui.storage.breakableFileName(entry.name), {
                    if (entry.type == RemoteEntryType.DIRECTORY) onDirectoryClick(entry) else onFileClick(entry)
                }, enabled = !state.isLoading, disabledReason = "폴더를 확인하고 있습니다.",
                    leading = { GeoIconTile(if (entry.type == RemoteEntryType.DIRECTORY) GeoIcons.folder else GeoIcons.file) }) }
                if (visible.isEmpty()) DevEmpty(if (directory) "하위 폴더가 없습니다" else "선택할 파일이 없습니다",
                    if (directory) "지금 폴더를 선택할 수 있습니다." else "이 위치에는 선택할 파일이 없습니다.", fullScreen = false)
            }
        }
    }
}

@Composable
fun PipelineLogScreen(state: PipelineUiState, onBack: () -> Unit, onRefresh: () -> Unit, onLogSelected: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    val pipeline = state.pipelines.firstOrNull { it.id == state.detailPipelineId }
    val deviceName = state.deviceName
    val selected = state.logFiles.firstOrNull { it.id == state.selectedLogId }
    val content = pipelineDetailContent(state.controlAvailable, state.detailLoading, state.error, state.logContent.isNotEmpty())
    if (picker) GeoSheet("실행 로그 선택", { picker = false }, kind = GeoSheetKind.CHOICE,
        subtitle = "${pipeline?.label ?: "수집 작업"} · $deviceName") {
        state.logFiles.forEach { file ->
            GeoRadioRow(logKind(file, state), file.id == state.selectedLogId,
                { picker = false; onLogSelected(file.id) }, description = "${devTimestamp(file.startedAt)} 시작 · ${Formats.size(file.sizeBytes)}")
        }
    }
    DevFrame("실행 로그", onBack, actions = {
        GeoIconButton(GeoIcons.refresh, "실행 로그 다시 조회", onRefresh, enabled = state.controlAvailable && !state.detailLoading)
    }) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val logHeight = maxHeight / 2
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = LocalGeoWindow.current.gutterDp.dp).padding(bottom = 24.dp)) {
                GeoSection("실행", first = true) {
                    GeoPickerField("실행 로그", selected?.let { "${logKind(it, state)} · ${devTimestamp(it.startedAt)} 시작" } ?: "선택 안 됨",
                        { picker = true }, enabled = state.logFiles.isNotEmpty(), disabledReason = when (content) {
                            PipelineDetailContent.OFFLINE -> "장치에 연결하면 실행 로그를 불러올 수 있습니다."
                            PipelineDetailContent.ERROR -> "실행 로그를 불러오지 못했습니다."
                            PipelineDetailContent.LOADING -> "실행 로그를 확인하고 있습니다."
                            else -> "저장된 실행 로그가 없습니다."
                        })
                    if (content == PipelineDetailContent.CONTENT) {
                        if (!state.controlAvailable) DevFeedback("마지막으로 불러온 로그입니다. 장치를 같은 네트워크나 직접 연결로 연결하면 다시 조회할 수 있습니다.")
                        DevFeedback(state.error, true)
                    }
                }
                Spacer(Modifier.height(24.dp))
                when (content) {
                    PipelineDetailContent.CONTENT -> key(state.selectedLogId) {
                        DevLogView(state.logContent.lines().mapIndexed { index, line -> GeoLogLine(index.toString(), line) },
                            pipelineLogIsLive(state.controlAvailable, state.logLive, selected?.active == true), selected?.sizeBytes,
                            Modifier.fillMaxWidth().height(logHeight))
                    }
                    PipelineDetailContent.OFFLINE -> GeoStateBlock(GeoStateContent(GeoStateKind.OFFLINE,
                        "$deviceName 실행 로그를 불러올 수 없습니다", "장치를 같은 네트워크나 직접 연결로 연결하면 실행 로그를 불러올 수 있습니다."))
                    PipelineDetailContent.ERROR -> GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
                        "실행 로그를 불러오지 못했습니다", state.error ?: "장치 연결을 확인하고 다시 시도하세요.", "다시 시도"), onAction = onRefresh)
                    PipelineDetailContent.LOADING -> DevLoading("실행 로그를 불러오는 중")
                    else -> if (state.logFiles.isEmpty()) DevEmpty("저장된 실행 로그가 없습니다", "작업을 시작하면 실행마다 로그가 따로 남습니다.")
                        else DevEmpty("로그 내용이 없습니다", "선택한 실행에서는 아직 결과가 기록되지 않았습니다.")
                }
            }
        }
    }
}

@Composable
fun PipelineConfigScreen(state: PipelineUiState, onBack: () -> Unit, onValueChange: (String, String) -> Unit,
    onSave: () -> Unit, onReload: () -> Unit = {}) {
    val focus = LocalFocusManager.current
    var reload by remember(state.deviceId, state.controlAvailable) { mutableStateOf(false) }
    val pipeline = state.pipelines.firstOrNull { it.id == state.detailPipelineId }
    val deviceName = state.deviceName
    val content = pipelineDetailContent(state.controlAvailable, state.detailLoading, state.error, state.configFields.isNotEmpty())
    if (reload) GeoConfirmDialog("장치 설정을 다시 불러올까요?", "작성 중인 내용을 장치의 현재 설정으로 바꿉니다.", "다시 불러오기",
        { reload = false; if (state.controlAvailable && !state.configSaving) onReload() }, { reload = false },
        dismissLabel = "초안 유지", confirmEnabled = state.controlAvailable && !state.configSaving,
        disabledReason = "장치에 연결되고 저장이 끝나야 다시 불러올 수 있습니다.")
    DevPage("작업 설정", onBack, primaryAction = GeoButtonAction("설정 저장", onSave, enabled = state.controlAvailable && !state.configNeedsReview &&
            !state.detailLoading && !state.configSaving && state.configHasChanges && state.configValuesValid,
            disabledReason = if (state.configNeedsReview) "장치의 설정을 다시 불러온 뒤 저장할 수 있습니다." else "변경한 설정 값을 확인하세요.")) {
        DevIdentity(pipeline?.label ?: "수집 작업", deviceName)
        GeoSection("설정 정보", first = true, modifier = Modifier.padding(top = 24.dp), insetContent = false) {
            if (state.configPath.isNotBlank()) GeoCopyableValue(state.configPath,
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp), label = "설정 파일")
            GeoRow("장치 설정 다시 불러오기", { reload = true }, leading = { GeoIconTile(GeoIcons.refresh) },
                enabled = state.controlAvailable && !state.configSaving && !state.detailLoading,
                disabledReason = "장치에 연결되고 저장이 끝나야 다시 불러올 수 있습니다.", divider = false)
        }
        if (content == PipelineDetailContent.CONTENT) {
            if (!state.controlAvailable) DevFeedback("마지막으로 불러온 설정입니다. 장치를 같은 네트워크나 직접 연결로 연결하면 다시 조회할 수 있습니다.")
            DevFeedback(state.error, true)
        }
        DevFeedback(state.message)
        if (state.configNeedsReview) {
            DevText("${deviceName}에서 설정이 바뀌었습니다. 장치 설정을 다시 불러와 확인하세요.")
        }
        if (content == PipelineDetailContent.OFFLINE) GeoStateBlock(GeoStateContent(GeoStateKind.OFFLINE,
            "$deviceName 설정을 불러올 수 없습니다", "장치를 같은 네트워크나 직접 연결로 연결하면 작업 설정을 불러올 수 있습니다."), fullScreen = true)
        else if (content == PipelineDetailContent.ERROR) GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
            "작업 설정을 불러오지 못했습니다", "장치 연결을 확인하고 장치 설정을 다시 불러오세요."), fullScreen = true)
        else if (content == PipelineDetailContent.LOADING) DevLoading("작업 설정을 불러오는 중")
        else if (content == PipelineDetailContent.EMPTY) DevEmpty("바꿀 수 있는 설정 값이 없습니다",
            "이 작업의 설정 파일에는 앱에서 바꿀 수 있는 키와 값이 없습니다.")
        else GeoSection("설정 값", insetContent = false) {
            state.configFields.forEachIndexed { index, field ->
                val caption = configFieldCaption(field.path, field.label)
                val enabled = state.controlAvailable && !state.detailLoading && !state.configSaving
                val isSwitch = field.type == PipelineConfigValueType.BOOLEAN
                Column(if (isSwitch) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (isSwitch) GeoSwitchRow(caption.label, field.value == "true",
                        { onValueChange(field.path, it.toString()) }, enabled = enabled,
                        disabledReason = "장치 연결과 저장 상태를 확인하세요.", divider = false)
                    else {
                        GeoTextField(field.value, { onValueChange(field.path, it.take(4096)) }, caption.label,
                            enabled = enabled, disabledReason = "장치 연결과 저장 상태를 확인하세요.",
                            singleLine = field.type != PipelineConfigValueType.STRING,
                            numeric = field.type in setOf(PipelineConfigValueType.INTEGER, PipelineConfigValueType.DECIMAL),
                            inputKind = GeoInputKind.TEXT,
                            imeAction = if (index == state.configFields.lastIndex) ImeAction.Done else ImeAction.Next,
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }),
                            error = if (configFieldValueValid(field.type, field.value)) null else "유효한 값을 입력하세요.")
                    }
                    caption.key?.let {
                        GeoCopyableValue(it, if (isSwitch) Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            else Modifier.padding(top = 4.dp))
                    }
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
