package com.example.jetsoncontroller.ui.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
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
import com.example.jetsoncontroller.ui.tasks.WorkSurfaceCard
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
    val lockedReason = preparationEditDisabledReason(state)
    val editable = lockedReason == null
    val freshnessTick by produceState(System.currentTimeMillis(), state.deviceId) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }
    // A response can arrive between ticks. Read the clock again before comparing its timestamps.
    val now = remember(freshnessTick, state) { System.currentTimeMillis() }
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
        GeoSheet("프로젝트 선택", { projectPicker = false }, openerFocusRequester = projectFocus) {
            state.projects.forEach { project ->
                GeoRadioRow(project.label, state.selectedProject?.surveyProjectId == project.surveyProjectId,
                    { projectPicker = false; newProject = false; onSelectProject(project) })
            }
            PreparationAddRow("새 프로젝트", { projectPicker = false; newProject = true })
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
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
                    GeoReadinessLine("$displayName · ${preparationActionReason(state)}", preparationActionTone(state))
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
                            actionArea = true)
                    }
                    if (!state.online && onConnectionMethod != null) {
                        GeoButton("연결 방법", onConnectionMethod, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
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
                    val lastExecution = state.lastExecutionStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        GeoBadge(taskState.word, taskState.tone)
                        Text(listOfNotNull(displayName, lastExecution?.let { "마지막 실행 ${Formats.time(it)}" }).joinToString(" · "),
                            Modifier.weight(1f), style = type.caption, color = c.textSecondary)
                    }
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
                    WorkSurfaceCard(Modifier.padding(top = 24.dp)) {
                    GeoPickerField("프로젝트", state.preparationDraft?.project.preparationLabel(state.selectedProject?.label ?: "프로젝트 선택"),
                        { projectPicker = true }, Modifier.focusRequester(projectFocus),
                        enabled = editable, disabledReason = lockedReason)
                    if (newProject) {
                        GeoTextField(projectLabel, { projectLabel = it }, "새 프로젝트", enabled = editable,
                            disabledReason = lockedReason)
                        GeoButton("선택", { onCreateProject(projectLabel); newProject = false },
                            enabled = editable && projectLabel.isNotBlank(), style = GeoButtonStyle.Secondary)
                    }
                    }
                    GeoSection("구간") {
                      WorkSurfaceCard {
                        Column(Modifier.selectableGroup()) {
                            if (state.isLoading && state.sections.isEmpty()) GeoSkeleton("수집 구간을 불러오는 중", kind = GeoSkeletonKind.Rows)
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
                            val newSectionReason = preparationNewSectionDisabledReason(state)
                            PreparationAddRow("새 구간", { newSection = true },
                                enabled = newSectionReason == null, disabledReason = newSectionReason)
                        }
                        if (newSection) {
                            GeoTextField(sectionLabel, { sectionLabel = it }, "새 구간", enabled = editable,
                                disabledReason = lockedReason)
                            GeoButton("선택", { onCreateSection(sectionLabel); newSection = false },
                                style = GeoButtonStyle.Secondary, enabled = editable && sectionLabel.isNotBlank())
                        }
                      }
                    }
                    GeoSection("준비 상태") {
                        WorkSurfaceCard {
                          GeoRowList(GeoRowTrailing.Status) {
                            val rows = preparationReadiness(state, now)
                            rows.forEachIndexed { index, row ->
                                val openRelay: (() -> Unit)? = if (row.label == "보정 정보 중계") {
                                    { if (onCorrectionRelay != null) onCorrectionRelay() else relayInfo = true }
                                } else null
                                GeoPreparationStatusRow(row.label, row.value, preparationStatusWord(row), row.tone,
                                    onClick = openRelay,
                                    divider = index < rows.lastIndex)
                            }
                          }
                        }
                    }
                    inlineAction()
                }
            }
        }
    }
}

@Composable
private fun PreparationAddRow(title: String, onClick: () -> Unit,
    enabled: Boolean = true, disabledReason: String? = null) {
    require(enabled || !disabledReason.isNullOrBlank())
    val c = LocalGeoColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).contentTarget(enabled, onClick).contentDivider()
        .semantics(mergeDescendants = true) {}.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(GeoIcons.add, null, Modifier.size(24.dp), tint = if (enabled) c.textSecondary else c.disabledText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = LocalGeoType.current.titleSm, color = if (enabled) c.text else c.disabledText)
            if (!enabled && disabledReason != null) Text(disabledReason,
                style = LocalGeoType.current.caption, color = c.textSecondary)
        }
    }
}

internal fun preparationEditDisabledReason(state: SurveyRunUiState): String? = when {
    state.contextLocked -> "진행 중인 수집과 시작 요청을 먼저 확인하세요."
    state.restoringLocalState -> "저장된 선택을 확인하고 있습니다."
    state.operation != null -> "진행 중인 작업이 끝난 뒤 선택할 수 있습니다."
    else -> null
}

internal fun preparationNewSectionDisabledReason(state: SurveyRunUiState): String? =
    preparationEditDisabledReason(state) ?: "프로젝트를 먼저 선택하세요.".takeIf {
        state.selectedProject == null && state.preparationDraft?.project == null
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
    optionalChecks: List<GeoStartCheck> = emptyList(),
    mapPreview: (@Composable () -> Unit)? = null
) {
    GeoStartSheet(state, onStart, onRecheck, onDismissRequest, modifier, openerFocusRequester,
        optionalChecks = optionalChecks, mapPreview = mapPreview)
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

/** The icon tone for [preparationActionReason], in the same order. */
internal fun preparationActionTone(state: SurveyRunUiState): Tone = when {
    state.pendingStart != null -> Tone.PENDING
    state.unconfirmedRunId != null -> Tone.WARNING
    state.restoringLocalState || !state.runStateConfirmed -> Tone.PENDING
    !state.online || !state.controlAvailable -> Tone.WARNING
    state.contextLocked || state.observedActiveRunCount > 0 -> Tone.WARNING
    !state.preparationSelectionComplete -> Tone.UNKNOWN
    state.policy == null -> Tone.WARNING
    else -> Tone.SUCCESS
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
