package com.example.jetsoncontroller.ui.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.TaskState
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.delay
import java.time.Instant

/** P3 owns choices; S8 owns the explicit device check and confirmation. */
@Suppress("UNUSED_PARAMETER") // Policy callbacks remain source compatible with the graph; editing lives in dev.
@Composable
internal fun SurveyRunScreen(
    state: SurveyRunUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectProject: (SurveyProject) -> Unit,
    onSelectSection: (SurveySection) -> Unit,
    onCreateProject: (String) -> Unit,
    onCreateSection: (String) -> Unit,
    onSensorRequirement: (String, SensorRequirementChoice) -> Unit,
    onMinFreeBytes: (String) -> Unit,
    onOutputMinFiles: (String) -> Unit,
    onOutputMinBytes: (String) -> Unit,
    onOutputPatterns: (String) -> Unit,
    onOutputRoot: (String) -> Unit,
    onOutputPath: (String) -> Unit,
    onSavePolicy: () -> Unit,
    onPreflight: () -> Unit,
    onStart: () -> Unit,
    onRetryPendingStart: () -> Unit,
    onDismissMessage: () -> Unit,
    developerModeEnabled: Boolean = false,
    deviceName: String? = null,
    ticketTitle: String? = null,
    ticketMessage: String? = null,
    ticketPreparationReady: Boolean = true,
    ticketPreparationKey: String? = null,
    zoneLine: String = "위치 미확인",
    mapCard: @Composable () -> Unit = {},
    onDeviceFiles: (() -> Unit)? = null,
    onConnectionMethod: (() -> Unit)? = null,
    onDeviceDetails: (() -> Unit)? = null,
    onCorrectionRelay: (() -> Unit)? = null
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val displayName = deviceName?.takeIf(String::isNotBlank) ?: "선택한 장치"
    // Deliberately exclude the preflight id: its arrival must not dismiss the checking sheet.
    val targetKey = listOf(state.deviceId, state.pipelineId, ticketPreparationKey,
        state.preparationDraft, state.selectedProject, state.selectedSection)
    var showStart by remember(state.deviceId, state.pipelineId, ticketPreparationKey) { mutableStateOf(false) }
    var projectPicker by remember(targetKey) { mutableStateOf(false) }
    var newProject by remember(targetKey) { mutableStateOf(false) }
    var newSection by remember(targetKey) { mutableStateOf(false) }
    var relayInfo by remember(state.deviceId, state.pipelineId) { mutableStateOf(false) }
    var projectLabel by rememberSaveable(state.deviceId, ticketPreparationKey) { mutableStateOf("") }
    var sectionLabel by rememberSaveable(state.deviceId, ticketPreparationKey) { mutableStateOf("") }
    val startFocus = remember { FocusRequester() }
    val projectFocus = remember { FocusRequester() }
    var topHeight by remember { mutableFloatStateOf(56f) }
    val density = LocalDensity.current
    val editable = !state.contextLocked && !state.restoringLocalState && state.operation == null
    val lockedReason = "진행 중인 수집과 시작 요청을 먼저 확인하세요."
    val now by produceState(System.currentTimeMillis(), state.deviceId) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }
    val beginCheck = { showStart = true; onPreflight() }
    LaunchedEffect(state.contextLocked, state.restoringLocalState) {
        if (state.contextLocked || state.restoringLocalState) { showStart = false; projectPicker = false }
    }

    if (showStart) {
        SurveyStartSheet(
            state = startSheetState(state, displayName, ticketTitle, zoneLine, now,
                onStorage = onDeviceFiles, onConnection = onConnectionMethod, onSensors = onDeviceDetails,
                preparationReady = ticketPreparationReady),
            onStart = { if (state.canStart && ticketPreparationReady) onStart() },
            onRecheck = onPreflight,
            onDismissRequest = { showStart = false },
            modifier = Modifier.testTag("start-sheet"),
            openerFocusRequester = startFocus,
            optionalChecks = optionalStartChecks(state)
        )
    }
    if (relayInfo) GeoInfoDialog("보정 정보 중계", onDismissRequest = { relayInfo = false }) {
        GeoFactRow("장치", displayName)
        GeoFactRow("상태", preparationReadiness(state, now).firstOrNull { it.label == "보정 정보 중계" }?.value ?: "미확인")
    }
    if (projectPicker) {
        GeoSheet("프로젝트 선택", { projectPicker = false }, openerFocusRequester = projectFocus,
            footer = {
                GeoButton("새 프로젝트", { projectPicker = false; newProject = true },
                    style = GeoButtonStyle.Tertiary, icon = GeoIcons.add)
            }) {
            state.projects.forEach { project ->
                GeoRadioRow(project.label, state.selectedProject?.surveyProjectId == project.surveyProjectId,
                    { projectPicker = false; newProject = false; onSelectProject(project) })
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.surface)) {
        GeoTopBar {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .onSizeChanged { topHeight = it.height / density.density }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
                Text("수집 준비", Modifier.weight(1f).padding(start = 12.dp), color = c.onTopBar, style = type.title)
                GeoIconButton(GeoIcons.refresh, "다시 확인", onRefresh, chrome = true,
                    enabled = state.online && state.operation == null,
                    disabledReason = "연결되면 다시 확인할 수 있습니다.")
            }
        }
        GeoActionArea(
            visibilityKey = listOf(state.deviceId, state.pipelineId),
            chrome = LocalGeoChrome.current.let { it.copy(topDp = it.topDp + topHeight) },
            action = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$displayName · ${preparationActionReason(state)}", style = type.body, color = c.textSecondary)
                    when {
                        state.pendingStart != null -> GeoButton("시작 결과 확인", onRetryPendingStart,
                            Modifier.fillMaxWidth(), enabled = state.online && state.operation == null,
                            disabledReason = "연결되면 시작 결과를 확인할 수 있습니다.", actionArea = true)
                        state.unconfirmedRunId != null -> GeoButton("상태 다시 확인", onRefresh,
                            Modifier.fillMaxWidth(), enabled = state.online && state.operation == null,
                            disabledReason = "연결되면 상태를 확인할 수 있습니다.", actionArea = true)
                        else -> GeoButton("수집 시작", beginCheck,
                            Modifier.fillMaxWidth().focusRequester(startFocus).testTag("prepare-start"),
                            enabled = state.canPreflight && ticketPreparationReady,
                            disabledReason = preparationActionReason(state), actionArea = true)
                    }
                    if (!state.online && onConnectionMethod != null) {
                        GeoButton("연결 방법", onConnectionMethod, style = GeoButtonStyle.Tertiary)
                    }
                }
            }
        ) { inlineAction ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp,
                top = 16.dp, bottom = 24.dp)) {
                item {
                    Text(ticketTitle ?: state.pipelineLabel.ifBlank { "수집 작업" },
                        Modifier.semantics { heading() }, style = type.boardTitle, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    val taskState = when {
                        state.pendingStart != null || state.unconfirmedRunId != null -> TaskState.START_REQUESTED
                        else -> taskChoiceState(state.preparationTaskState)
                    }
                    GeoBadge(taskState.word, taskState.tone)
                    val lastExecution = state.lastExecutionStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    Text(listOfNotNull(displayName, lastExecution?.let { "마지막 실행 ${Formats.time(it)}" }).joinToString(" · "),
                        style = type.caption, color = c.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    mapCard()
                }
                ticketMessage?.let { item { GeoInlineNote(it, tone = Tone.WARNING) } }
                state.error?.let { item {
                    GeoInlineNote(it, tone = Tone.WARNING, kind = GeoNoteKind.LIVE_ERROR,
                        actionLabel = "다시 확인", onAction = onRefresh)
                } }
                if (state.pendingStart != null || state.unconfirmedRunId != null) item {
                    GeoInlineNote("같은 수집의 시작 결과를 확인합니다. 확인이 끝날 때까지 새 수집을 시작할 수 없습니다.",
                        tone = Tone.PENDING)
                }
                item {
                    GeoPickerField("프로젝트", state.preparationDraft?.project.preparationLabel(state.selectedProject?.label ?: "프로젝트 선택"),
                        { projectPicker = true }, Modifier.focusRequester(projectFocus),
                        enabled = editable, disabledReason = lockedReason)
                    if (newProject) {
                        GeoTextField(projectLabel, { projectLabel = it }, "새 프로젝트", enabled = editable,
                            disabledReason = lockedReason)
                        GeoButton("선택", { onCreateProject(projectLabel); newProject = false },
                            enabled = editable && projectLabel.isNotBlank(), style = GeoButtonStyle.Secondary)
                    }
                    GeoSection("구간") {
                        Column(Modifier.selectableGroup()) {
                            state.sections.forEach { section ->
                                GeoRadioRow(section.label, state.selectedSection?.surveySectionId == section.surveySectionId,
                                    { newSection = false; onSelectSection(section) }, enabled = editable,
                                    disabledReason = lockedReason)
                            }
                            if (state.preparationDraft?.section is com.example.jetsoncontroller.model.PreparationSelection.CreateNew ||
                                state.preparationDraft?.section is com.example.jetsoncontroller.model.PreparationSelection.CreateFromTicket) {
                                GeoRadioRow(state.preparationDraft?.section.preparationLabel(null), true, {},
                                    enabled = editable, disabledReason = lockedReason)
                            }
                        }
                        GeoButton("새 구간", { newSection = true }, style = GeoButtonStyle.Tertiary,
                            icon = GeoIcons.add, enabled = editable &&
                                (state.selectedProject != null || state.preparationDraft?.project != null),
                            disabledReason = "프로젝트를 먼저 선택하세요.")
                        if (newSection) {
                            GeoTextField(sectionLabel, { sectionLabel = it }, "새 구간", enabled = editable,
                                disabledReason = lockedReason)
                            GeoButton("선택", { onCreateSection(sectionLabel); newSection = false },
                                style = GeoButtonStyle.Secondary, enabled = editable && sectionLabel.isNotBlank())
                        }
                    }
                    GeoSection("준비 상태") {
                        preparationReadiness(state, now).forEach { row ->
                            if (row.label == "보정 정보 중계") {
                                GeoRow(row.label, { if (onCorrectionRelay != null) onCorrectionRelay() else relayInfo = true }, value = row.value)
                            } else GeoCheckRow(row.label, row.value, row.tone)
                        }
                    }
                    inlineAction()
                }
            }
        }
    }
}

/** Adapts the shared S8 gate so optional warnings remain visible without becoming required checks. */
@Composable
internal fun SurveyStartSheet(
    state: GeoStartSheetState,
    onStart: () -> Unit,
    onRecheck: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    openerFocusRequester: FocusRequester? = null,
    optionalChecks: List<GeoStartCheck> = emptyList()
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val rules = startSheetRules(state)
    GeoSheet("${state.sectionName} 수집을 시작할까요?", onDismissRequest, modifier,
        kind = GeoSheetKind.FORM, openerFocusRequester = openerFocusRequester,
        footer = {
            rules.blockingReason?.let { Text(it, color = c.textSecondary, style = type.body) }
            if (rules.showRecheck) GeoButton("다시 점검", onRecheck, style = GeoButtonStyle.Tertiary)
            GeoSheetFooter(GeoOverlayAction(rules.primaryLabel, onStart, enabled = rules.canStart, busy = rules.busy),
                GeoOverlayAction("취소", onDismissRequest))
        }) {
        if (state.ticketLinked) {
            Text("${state.sectionName} · ${state.projectName} · ${state.deviceName}", style = type.body, color = c.textSecondary)
        } else {
            GeoFactRow("장치", state.deviceName)
            GeoFactRow("프로젝트", state.projectName)
            GeoFactRow("구간", state.sectionName)
        }
        Spacer(Modifier.height(8.dp))
        if (state.insideZone) GeoBadge(state.zoneLine, Tone.SUCCESS)
        else Text(state.zoneLine, style = type.body, color = c.text)
        GeoSection("시작 전 점검") {
            (state.checks + optionalChecks).forEach { check ->
                GeoCheckRow(check.label, if (check.phase == GeoStartPhase.CHECKING) "확인 중" else check.value ?: "미확인",
                    when (check.phase) {
                        GeoStartPhase.CHECKING -> Tone.PENDING
                        GeoStartPhase.PASSED -> Tone.SUCCESS
                        GeoStartPhase.FAILED -> Tone.WARNING
                    }, failureReason = check.reason.takeIf { check.phase == GeoStartPhase.FAILED })
                if (check.phase == GeoStartPhase.FAILED) check.remedy?.let {
                    GeoButton(it.label, it.onClick, style = GeoButtonStyle.Tertiary,
                        enabled = it.enabled, disabledReason = it.disabledReason)
                }
            }
        }
        if (state.ticketLinked) {
            Spacer(Modifier.height(12.dp))
            Text("GEO& Work 상태가 '진행 중'으로 바뀝니다.", style = type.body, color = c.text)
        }
        Spacer(Modifier.height(4.dp))
        Text("시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다.", style = type.body, color = c.text)
    }
}

internal fun preparationActionReason(state: SurveyRunUiState): String = when {
    state.pendingStart != null -> "시작 확인 중"
    state.unconfirmedRunId != null -> "수집 상태를 확인할 수 없습니다"
    state.restoringLocalState || !state.runStateConfirmed -> "확인 중"
    !state.online || !state.controlAvailable -> "연결되면 시작할 수 있습니다."
    state.contextLocked || state.observedActiveRunCount > 0 -> "진행 중인 수집과 시작 요청을 먼저 확인하세요."
    !state.preparationSelectionComplete -> "프로젝트와 구간을 선택하세요."
    state.policy == null -> "수집 작업 설정을 확인하세요."
    else -> "${state.preparationTelemetry.connectionLabel} · 시작할 수 있습니다."
}

internal fun startReadinessLabel(state: SurveyRunUiState): String = when {
    !state.online -> "장치 연결이 끊겨 점검할 수 없습니다"
    state.pendingStart != null -> "수집 시작 여부를 확인하고 있습니다"
    state.unconfirmedRunId != null -> "최근 실행 상태 미확인 · 같은 장치에서 재조회 필요"
    state.contextLocked -> "현재 실행이 끝날 때까지 조사 범위 잠김"
    !state.selectionComplete -> "프로젝트와 조사 구간 선택 필요"
    state.policy == null -> "수집 작업 설정 확인 필요"
    state.preflight == null -> "시작 전 점검 필요"
    !state.preflight.ready -> "점검 문제 확인 필요"
    !state.canStart -> "선택 또는 정책 변경됨 · 다시 점검 필요"
    else -> "조사 구간과 필수 점검 확인됨"
}

/**
 * The tone of the readiness line.
 *
 * A pending or unconfirmed start is [Tone.PENDING], never an error: the device may
 * well have started the run, and telling the operator it failed is how a second run gets
 * launched over the top of a healthy one.
 */
internal fun startReadinessTone(state: SurveyRunUiState): Tone = when {
    !state.online -> Tone.ERROR
    state.pendingStart != null -> Tone.PENDING
    state.unconfirmedRunId != null -> Tone.UNKNOWN
    state.contextLocked -> Tone.PENDING
    !state.selectionComplete -> Tone.INFO
    state.policy == null -> Tone.INFO
    state.preflight == null -> Tone.INFO
    !state.preflight.ready -> Tone.WARNING
    !state.canStart -> Tone.WARNING
    else -> Tone.SUCCESS
}
