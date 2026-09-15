package com.example.jetsoncontroller.ui.survey

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelinePreflight
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.AdaptiveColumns
import com.example.jetsoncontroller.ui.components.AdaptiveContent
import com.example.jetsoncontroller.ui.components.ConnectionStepper
import com.example.jetsoncontroller.ui.components.GeoBottomActionBar
import com.example.jetsoncontroller.ui.components.GeoDataRow
import com.example.jetsoncontroller.ui.components.GeoIdentifier
import com.example.jetsoncontroller.ui.components.GeoPrimaryAction
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.components.GeoSection
import com.example.jetsoncontroller.ui.components.GeoSectionHeader
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.Button

/**
 * 조사 수집 준비 — the screen that decides whether a collection may start.
 *
 * Structure follows the operator's decision, not the data model: 조사 범위 → 수집 정책 →
 * 시작 전 점검, with a step indicator so the operator knows how far along they are and a
 * bottom bar that always states *why* the start button is or is not available.
 *
 * Three changes matter more than the visual ones:
 *
 *  - The policy editor is collapsed behind a disclosure. Asking a field worker to type a
 *    minimum free-byte count before every survey turns a safety policy into a form to be
 *    dismissed. The saved policy is shown as a readable summary; editing is available,
 *    marked as an administrator concern, and does not block the default path.
 *  - Preflight results are rows with their own status badge instead of a paragraph of
 *    concatenated text, so a single failing sensor is visible without reading a sentence.
 *  - Start, confirm-result and re-check are three visibly different buttons. A pending
 *    start never renders as a fresh "시작" button, because re-pressing it is how a survey
 *    ends up recorded twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    developerModeEnabled: Boolean = false
) {
    val c = LocalGeoColors.current
    var confirmStart by remember(state.deviceId, state.pipelineId, state.preflight?.preflightId) {
        mutableStateOf(false)
    }
    if (confirmStart) {
        StartConfirmationDialog(
            state = state,
            developerModeEnabled = developerModeEnabled,
            onDismiss = { confirmStart = false },
            onConfirm = { confirmStart = false; onStart() }
        )
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.canvas,
                    titleContentColor = c.ink
                ),
                title = { Text(if (state.preflight == null) "수집 준비" else "장치 점검") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = state.online && state.operation == null) {
                        Icon(Icons.Default.Refresh, "조사 정보 새로고침")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = c.surface) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.md)
                ) {
                    when {
                        state.pendingStart != null -> GeoPrimaryAction(
                            "시작 상태 확인", onRetryPendingStart,
                            enabled = state.online && state.operation == null,
                            icon = Icons.Default.Refresh
                        )
                        state.unconfirmedRunId != null -> GeoPrimaryAction(
                            "수집 상태 확인", onRefresh,
                            enabled = state.online && state.operation == null,
                            icon = Icons.Default.Refresh
                        )
                        state.preflight?.ready == true -> GeoPrimaryAction(
                            "수집 시작", { confirmStart = true },
                            enabled = state.canStart,
                            icon = Icons.Default.CheckCircle
                        )
                        state.preflight != null -> GeoPrimaryAction(
                            "장치 다시 점검", onPreflight,
                            enabled = state.canPreflight,
                            icon = Icons.Default.Refresh
                        )
                        else -> GeoPrimaryAction(
                            "장치 점검", onPreflight,
                            enabled = state.canPreflight,
                            icon = Icons.Default.CheckCircle
                        )
                    }
                }
            }
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1120.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = GeoSpace.md,
                    bottom = GeoSpace.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
            ) {
                item { PreparationSteps(if (state.preflight == null) 0 else 1) }
                if (state.isLoading || state.operation != null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (!state.online) {
                    item {
                        AppBanner(
                            "장치 연결이 끊겼습니다. 다시 연결한 뒤 장치 점검을 진행해 주세요.",
                            StatusTone.ERROR
                        )
                    }
                }
                if (state.contextLocked) {
                    item {
                        AppBanner(
                            if (state.pendingStart != null || state.unconfirmedRunId != null) {
                                "수집 시작 여부를 확인하는 동안 프로젝트와 구간을 변경하거나 새 수집을 시작할 수 없습니다."
                            } else {
                                "현재 수집에 프로젝트와 구간이 고정되어 있습니다. 수집이 끝난 뒤 변경하세요."
                            },
                            if (state.pendingStart != null || state.unconfirmedRunId != null) {
                                StatusTone.PENDING
                            } else {
                                StatusTone.INFO
                            }
                        )
                    }
                }
                state.error?.let { error ->
                    item {
                        AppBanner(
                            error, StatusTone.WARNING,
                            actionLabel = "최신 상태 다시 불러오기",
                            onAction = onRefresh, onDismiss = onDismissMessage
                        )
                    }
                }
                state.message?.let { message ->
                    item { AppBanner(message, StatusTone.SUCCESS, onDismiss = onDismissMessage) }
                }
                if (developerModeEnabled) state.technicalError?.let { detail ->
                    item { AppBanner(detail, StatusTone.UNKNOWN, onDismiss = onDismissMessage) }
                }
                state.activeRun?.let { run -> item { ActiveRunCard(run) } }
                item {
                    AdaptiveColumns(
                        first = {
                            SurveyContextPane(
                                state, onSelectProject, onSelectSection,
                                onCreateProject, onCreateSection,
                                developerModeEnabled
                            )
                        },
                        second = {
                            if (state.preflight != null) {
                                PreflightPane(state.preflight, developerModeEnabled)
                            } else {
                                CollectionTaskPane(state)
                            }
                            if (developerModeEnabled) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                PolicyPane(
                                    state, onSensorRequirement, onMinFreeBytes,
                                    onOutputMinFiles, onOutputMinBytes, onOutputPatterns,
                                    onOutputRoot, onOutputPath, onSavePolicy
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparationSteps(currentStep: Int) {
    val c = LocalGeoColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        listOf("조사 선택", "장치 점검", "수집").forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                Text(
                    "${index + 1}  $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == currentStep) c.primary else c.muted
                )
                HorizontalDivider(thickness = 3.dp, color = if (index == currentStep) c.primary else c.border)
            }
        }
    }
}

// =====================================================================================
// Start confirmation
// =====================================================================================

/**
 * Confirmation states *what will be fixed to the run*, not "are you sure".
 *
 * Once started, the project, section, policy version and output folder are attached to
 * the run and cannot be corrected afterwards, so this is the last moment the operator
 * can catch a wrong section.
 */
@Composable
private fun StartConfirmationDialog(
    state: SurveyRunUiState,
    developerModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val c = LocalGeoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.primary) },
        title = { Text("수집을 시작할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                ConfirmRow("장비", state.deviceId.orEmpty())
                ConfirmRow("프로젝트", state.selectedProject?.label ?: "선택 없음")
                ConfirmRow("구간", state.selectedSection?.label ?: "선택 없음")
                if (developerModeEnabled) {
                    ConfirmRow("정책", state.policy?.let { "v${it.policyVersion}" } ?: "미저장")
                    state.preflight?.preflightId?.let { GeoIdentifier("점검 ID", it) }
                }
                Spacer(Modifier.height(GeoSpace.xs))
                Text(
                    "시작하면 선택한 프로젝트와 구간이 이 수집에 고정되며, 수집 중에는 바꿀 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = state.canStart, modifier = Modifier.testTag("confirm-start")) {
                Text("수집 시작")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    val c = LocalGeoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// =====================================================================================
// 1. Survey context
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurveyContextPane(
    state: SurveyRunUiState,
    onSelectProject: (SurveyProject) -> Unit,
    onSelectSection: (SurveySection) -> Unit,
    onCreateProject: (String) -> Unit,
    onCreateSection: (String) -> Unit,
    developerModeEnabled: Boolean
) {
    val c = LocalGeoColors.current
    var projectLabel by remember(state.deviceId) { mutableStateOf("") }
    var sectionLabel by remember(state.deviceId, state.selectedProject?.surveyProjectId) { mutableStateOf("") }
    var creating by rememberSaveable(state.deviceId) { mutableStateOf(false) }
    var projectExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        Text("수집할 구간을 선택하세요", style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
            Text("프로젝트", style = MaterialTheme.typography.labelMedium, color = c.muted)
            ExposedDropdownMenuBox(
                expanded = projectExpanded,
                onExpandedChange = {
                    if (!state.contextLocked && state.operation == null) projectExpanded = !projectExpanded
                }
            ) {
                OutlinedTextField(
                    value = state.selectedProject?.let {
                        it.label + if (developerModeEnabled) " · r${it.revision}" else ""
                    } ?: "프로젝트 선택",
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor().heightIn(min = 60.dp),
                    enabled = !state.contextLocked && state.operation == null,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(projectExpanded, onDismissRequest = { projectExpanded = false }) {
                    state.projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.label) },
                            onClick = { projectExpanded = false; onSelectProject(project) }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("새 프로젝트 만들기") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { projectExpanded = false; creating = true }
                    )
                }
            }
        }
        if (state.projects.isEmpty() && !state.isLoading) {
            Text("등록된 프로젝트가 없습니다.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("조사 구간", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(
                onClick = { creating = !creating },
                enabled = !state.contextLocked,
                modifier = Modifier.size(GeoSize.minTouchTarget)
            ) { Icon(Icons.Default.Add, contentDescription = "프로젝트 또는 조사 구간 만들기") }
        }
        if (state.sections.isNotEmpty()) {
            Surface(color = c.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Column {
                    state.sections.forEachIndexed { index, section ->
                        val selected = state.selectedSection?.surveySectionId == section.surveySectionId
                        Surface(
                            onClick = { onSelectSection(section) },
                            enabled = !state.contextLocked && state.operation == null,
                            color = if (selected) c.accent else c.surface
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                    .padding(horizontal = GeoSpace.lg, vertical = GeoSpace.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    section.label + if (developerModeEnabled) " · r${section.revision}" else "",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) c.primary else c.ink
                                )
                                Icon(
                                    if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (selected) "선택됨" else "선택",
                                    tint = if (selected) c.primary else c.muted
                                )
                            }
                        }
                        if (index < state.sections.lastIndex) HorizontalDivider(color = c.border)
                    }
                }
            }
        }
        if (state.selectedProject != null && state.sections.isEmpty() && state.operation == null) {
            Text("이 프로젝트에 등록된 구간이 없습니다.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        if (creating) {
            AppBanner(
                "새 프로젝트를 만들거나, 선택한 프로젝트에 조사 구간을 추가할 수 있습니다.",
                StatusTone.INFO,
                onDismiss = { creating = false }
            )
            CreateRow(
                value = projectLabel,
                onValue = { projectLabel = it.take(80) },
                label = "새 프로젝트 이름",
                enabled = !state.contextLocked,
                actionEnabled = projectLabel.isNotBlank() && !state.contextLocked && state.operation == null,
                onAction = { onCreateProject(projectLabel); projectLabel = "" }
            )
            CreateRow(
                value = sectionLabel,
                onValue = { sectionLabel = it.take(80) },
                label = "새 구간 이름",
                enabled = state.selectedProject != null && !state.contextLocked,
                actionEnabled = sectionLabel.isNotBlank() && state.selectedProject != null &&
                    !state.contextLocked && state.operation == null,
                onAction = { onCreateSection(sectionLabel); sectionLabel = "" }
            )
        }
    }
}

@Composable
private fun CollectionTaskPane(state: SurveyRunUiState) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text("수집 작업", style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(
            state.pipelineLabel.ifBlank { "수집 작업 미선택" },
            style = MaterialTheme.typography.titleLarge,
            color = c.ink
        )
        Text(
            state.deviceId?.let { "$it · ${if (state.online) "연결됨" else "연결 확인 필요"}" }
                ?: "선택한 장치 없음",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.online) c.success else c.muted
        )
    }
}

@Composable
private fun PreflightIntro(state: SurveyRunUiState) {
    val c = LocalGeoColors.current
    GeoSection {
        GeoSectionHeader(title = "시작 전 점검", eyebrow = "다음 단계")
        Text(
            when {
                !state.selectionComplete -> "프로젝트와 조사 구간을 선택하면 장치 상태를 점검할 수 있습니다."
                state.policy == null -> "이 수집 작업의 설정이 준비되지 않았습니다. 설정 담당자에게 확인하세요."
                else -> "장치 연결, 저장 공간, 필수 센서를 확인합니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted
        )
    }
}

@Composable
private fun CreateRow(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    enabled: Boolean,
    actionEnabled: Boolean,
    onAction: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                OutlinedTextField(
                    value, onValue, label = { Text(label) }, singleLine = true,
                    enabled = enabled, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onAction, enabled = actionEnabled, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
                ) { Text("만들기") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value, onValue, label = { Text(label) }, singleLine = true,
                    enabled = enabled, modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onAction, enabled = actionEnabled, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.heightIn(min = GeoSize.secondaryAction)
                ) { Text("만들기") }
            }
        }
    }
}

// =====================================================================================
// 2. Collection policy
// =====================================================================================

/**
 * The saved policy, readable at a glance, with the editor behind a disclosure.
 *
 * The brief asks that the operator *understand and confirm* a policy an administrator
 * prepared, rather than re-enter thresholds before every survey. The editor is still
 * here — hiding a capability is not the same as removing it — but it no longer sits
 * between the operator and the start button.
 */
@Composable
private fun PolicyPane(
    state: SurveyRunUiState,
    onSensorRequirement: (String, SensorRequirementChoice) -> Unit,
    onMinFreeBytes: (String) -> Unit,
    onOutputMinFiles: (String) -> Unit,
    onOutputMinBytes: (String) -> Unit,
    onOutputPatterns: (String) -> Unit,
    onOutputRoot: (String) -> Unit,
    onOutputPath: (String) -> Unit,
    onSavePolicy: () -> Unit
) {
    val c = LocalGeoColors.current
    val draft = state.policyDraft
    val enabled = !state.contextLocked && state.operation == null
    var editing by rememberSaveable(state.deviceId) { mutableStateOf(state.policy == null) }

    GeoSection(tone = if (state.policy != null) StatusTone.SUCCESS else null) {
        GeoSectionHeader(
            title = "수집 정책",
            eyebrow = "2단계",
            trailing = {
                StatusBadge(
                    state.policy?.let { "v${it.policyVersion}" } ?: "미저장",
                    if (state.policy == null) StatusTone.INFO else StatusTone.SUCCESS
                )
            }
        )

        // Read-only summary: what this policy will enforce when the run starts.
        SupportedPolicySensors.forEach { sensor ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sensorLabel(sensor), style = MaterialTheme.typography.bodyMedium)
                StatusBadge(
                    requirementLabel(draft.sensors[sensor] ?: SensorRequirementChoice.NOT_USED),
                    when (draft.sensors[sensor]) {
                        SensorRequirementChoice.REQUIRED -> StatusTone.INFO
                        SensorRequirementChoice.OPTIONAL -> StatusTone.WARNING
                        else -> StatusTone.UNKNOWN
                    }
                )
            }
        }
        Text(
            "RTK는 품질 관찰로만 기록하며 시작 통과 기준으로 사용하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )

        GeoRowDivider()
        TextButton(
            onClick = { editing = !editing },
            modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget)
        ) {
            Text(if (editing) "정책 편집 닫기" else "정책 편집 · 관리자 설정", Modifier.weight(1f))
            Icon(
                if (editing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        if (editing) {
            Text(
                "이 값은 시작 차단 기준과 종료 후 결과 검사에 사용됩니다. 운영 정책과 다른 값을 넣으면 실제 조사 품질과 화면의 판정이 어긋납니다.",
                style = MaterialTheme.typography.bodySmall,
                color = c.warning
            )
            SupportedPolicySensors.forEach { sensor ->
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                    Text(sensorLabel(sensor), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SensorRequirementChoice.entries.forEach { choice ->
                            FilterChip(
                                selected = draft.sensors[sensor] == choice,
                                onClick = { onSensorRequirement(sensor, choice) },
                                enabled = enabled,
                                label = { Text(requirementLabel(choice)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            NumericPolicyField("시작 전 최소 여유 공간 (bytes)", draft.minFreeBytes, onMinFreeBytes, enabled)
            Text("예상 결과", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                Box(Modifier.weight(1f)) {
                    NumericPolicyField("최소 파일 수", draft.outputMinFiles, onOutputMinFiles, enabled)
                }
                Box(Modifier.weight(1f)) {
                    NumericPolicyField("최소 bytes", draft.outputMinBytes, onOutputMinBytes, enabled)
                }
            }
            Text("결과 저장소", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
            ) {
                state.roots.forEach { root ->
                    FilterChip(
                        selected = draft.outputRootId == root.id,
                        onClick = { onOutputRoot(root.id) },
                        enabled = enabled,
                        label = { Text(root.label) }
                    )
                }
            }
            OutlinedTextField(
                draft.outputPath, onOutputPath,
                label = { Text("결과 상대 경로 · 비워두면 저장소 루트") },
                supportingText = {
                    if (!safeOutputPath(draft.outputPath)) {
                        Text("절대 경로와 . 또는 .. 구간은 사용할 수 없습니다.")
                    }
                },
                isError = !safeOutputPath(draft.outputPath),
                enabled = enabled, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                draft.outputPatterns, onOutputPatterns,
                label = { Text("예상 파일 패턴 · 한 줄에 하나") },
                enabled = enabled, minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSavePolicy,
                enabled = enabled && draft.valid,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
            ) {
                Text(if (state.policy == null) "정책 저장" else "새 정책 버전 저장")
            }
        }
    }
}

@Composable
private fun NumericPolicyField(label: String, value: String, onValue: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value, onValue, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// =====================================================================================
// 3. Preflight
// =====================================================================================

/**
 * Each check is its own row with its own verdict.
 *
 * "확인 필요" and "상태 미확인" are separate outcomes and get separate tones: a sensor that
 * reported a failure and a sensor that never answered call for different actions.
 */
@Composable
private fun PreflightPane(preflight: PipelinePreflight?, developerModeEnabled: Boolean) {
    val c = LocalGeoColors.current
    val tone = when {
        preflight == null -> StatusTone.INFO
        preflight.ready -> StatusTone.SUCCESS
        else -> StatusTone.WARNING
    }
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        Text(
            if (preflight?.ready == true) "수집을 시작할 수 있습니다" else "확인이 필요한 항목이 있습니다",
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink
        )
        preflight?.let {
            Text(
                "${it.contextSnapshot.surveyProjectLabel} · ${it.contextSnapshot.surveySectionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted
            )
        }
        if (preflight == null) {
            Text(
                "프로젝트·구간과 저장된 정책으로 장비 시간, 저장 공간, 센서를 실제로 조회해 점검합니다. 확인 팝업이 아니라 장비의 응답입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted
            )
        } else {
            Surface(color = c.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(GeoSpace.xl)) {
                    CheckInfoRow("장치 연결 확인", preflight.deviceId, StatusTone.SUCCESS)
                    HorizontalDivider(Modifier.padding(vertical = GeoSpace.lg), color = c.border)
                    CheckInfoRow(
                        "장치 시간 확인",
                        checkLabel(preflight.checks.time.state),
                        checkTone(preflight.checks.time.state)
                    )
                    HorizontalDivider(Modifier.padding(vertical = GeoSpace.lg), color = c.border)
                    CheckInfoRow(
                        "저장 공간 확인",
                        checkLabel(preflight.checks.storage.state),
                        checkTone(preflight.checks.storage.state)
                    )
                    preflight.checks.sensors.forEach { sensor ->
                        HorizontalDivider(Modifier.padding(vertical = GeoSpace.lg), color = c.border)
                        CheckInfoRow(
                            "${sensorLabel(sensor.sensor)} 준비",
                            checkLabel(sensor.state) + if (developerModeEnabled) {
                                sensor.detail?.let { " · $it" }.orEmpty()
                            } else "",
                            checkTone(sensor.state)
                        )
                    }
                }
            }
            if (developerModeEnabled) {
                Spacer(Modifier.height(GeoSpace.sm))
                GeoIdentifier("점검 ID", preflight.preflightId)
            }
            if (preflight.problems.isNotEmpty()) {
                GeoRowDivider()
                Text("확인이 필요한 항목", style = MaterialTheme.typography.titleSmall)
                if (developerModeEnabled) {
                    preflight.problems.forEach { problem ->
                        Text(
                            "• ${problem.detail ?: problem.code}",
                            color = c.danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        "장치에서 확인이 필요한 항목 ${preflight.problems.size}개가 있습니다.",
                        color = c.danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInfoRow(title: String, detail: String, tone: StatusTone) {
    val c = LocalGeoColors.current
    val visuals = com.example.jetsoncontroller.ui.components.statusVisuals(tone)
    Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(visuals.icon, null, tint = visuals.content, modifier = Modifier.size(GeoSize.iconMd))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, tone: StatusTone, supporting: String? = null) {
    val c = LocalGeoColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        StatusBadge(value, tone)
    }
}

// =====================================================================================
// Active run
// =====================================================================================

@Composable
private fun ActiveRunCard(run: com.example.jetsoncontroller.model.PipelineRun) {
    val c = LocalGeoColors.current
    GeoSection(tone = StatusTone.PENDING) {
        GeoSectionHeader(
            title = "${run.contextSnapshot.surveyProjectLabel} · ${run.contextSnapshot.surveySectionLabel}",
            eyebrow = "현재 실행에 고정된 조사 범위",
            trailing = { StatusBadge("진행 중", StatusTone.PENDING) }
        )
        Text(
            "이 실행이 끝날 때까지 프로젝트·구간·정책은 바꿀 수 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
    }
}

// =====================================================================================
// Labels
// =====================================================================================

internal fun startReadinessLabel(state: SurveyRunUiState): String = when {
    !state.online -> "장비 연결 확인 필요"
    state.pendingStart != null -> "수집 시작 여부를 확인하고 있습니다"
    state.unconfirmedRunId != null -> "최근 실행 상태 미확인 · 같은 장비에서 재조회 필요"
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
 * A pending or unconfirmed start is [StatusTone.PENDING], never an error: the device may
 * well have started the run, and telling the operator it failed is how a second run gets
 * launched over the top of a healthy one.
 */
internal fun startReadinessTone(state: SurveyRunUiState): StatusTone = when {
    !state.online -> StatusTone.ERROR
    state.pendingStart != null -> StatusTone.PENDING
    state.unconfirmedRunId != null -> StatusTone.UNKNOWN
    state.contextLocked -> StatusTone.PENDING
    !state.selectionComplete -> StatusTone.INFO
    state.policy == null -> StatusTone.INFO
    state.preflight == null -> StatusTone.INFO
    !state.preflight.ready -> StatusTone.WARNING
    !state.canStart -> StatusTone.WARNING
    else -> StatusTone.SUCCESS
}

private fun sensorLabel(sensor: String): String = when (sensor.lowercase()) {
    "camera" -> "카메라"
    "gnss" -> "GNSS"
    "imu" -> "IMU"
    else -> sensor
}

private fun requirementLabel(choice: SensorRequirementChoice): String = when (choice) {
    SensorRequirementChoice.REQUIRED -> "필수"
    SensorRequirementChoice.OPTIONAL -> "선택"
    SensorRequirementChoice.NOT_USED -> "사용 안 함"
}

private fun requirementLabel(requirement: String): String = when (requirement.uppercase()) {
    "REQUIRED" -> "필수"
    "OPTIONAL" -> "선택"
    else -> "요구 안 함"
}

private fun checkLabel(state: String): String = when (state.uppercase()) {
    "PASSED", "READY", "AVAILABLE", "CURRENT", "ACTIVE" -> "확인됨"
    "FAILED", "MISSING", "STALE", "LOST", "ERROR" -> "확인 필요"
    "NOT_CONFIGURED" -> "미설정"
    else -> "상태 미확인"
}

/**
 * A check that failed and a check that never answered are different facts. Only the
 * first is a warning; the second is genuinely unknown and needs a re-check rather than
 * a repair.
 */
private fun checkTone(state: String): StatusTone = when (state.uppercase()) {
    "PASSED", "READY", "AVAILABLE", "CURRENT", "ACTIVE" -> StatusTone.SUCCESS
    "FAILED", "MISSING", "LOST", "ERROR" -> StatusTone.WARNING
    "STALE" -> StatusTone.UNKNOWN
    "NOT_CONFIGURED" -> StatusTone.INFO
    else -> StatusTone.UNKNOWN
}
