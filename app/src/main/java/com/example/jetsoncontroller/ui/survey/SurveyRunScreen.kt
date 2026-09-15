package com.example.jetsoncontroller.ui.survey

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelinePreflight
import com.example.jetsoncontroller.model.PipelineRunPolicy
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.SectionHeader
import com.example.jetsoncontroller.ui.components.SectionSurface
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.TextButton

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
    onDismissMessage: () -> Unit
) {
    var confirmStart by remember(state.deviceId, state.pipelineId, state.preflight?.preflightId) {
        mutableStateOf(false)
    }
    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("이 조사 범위로 수집을 시작할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("장비: ${state.deviceId}")
                    Text("프로젝트: ${state.selectedProject?.label}")
                    Text("구간: ${state.selectedSection?.label}")
                    Text("정책: v${state.policy?.policyVersion} · ${state.preflight?.preflightId}")
                    Text("시작하면 프로젝트·구간·정책과 결과 폴더가 실행에 고정됩니다.")
                }
            },
            confirmButton = {
                Button(onClick = { confirmStart = false; onStart() }, enabled = state.canStart) {
                    Text("점검 근거로 시작")
                }
            },
            dismissButton = { TextButton(onClick = { confirmStart = false }) { Text("취소") } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("조사 수집 준비")
                        Text(state.pipelineLabel.ifBlank { state.pipelineId.orEmpty() }, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                } },
                actions = { IconButton(onClick = onRefresh, enabled = state.online && state.operation == null) {
                    Icon(Icons.Default.Refresh, "조사 정보 새로고침")
                } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(startReadinessLabel(state), style = MaterialTheme.typography.bodySmall)
                    when {
                        state.pendingStart != null -> Button(
                            onClick = onRetryPendingStart,
                            enabled = state.online && state.operation == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("같은 요청 ID로 시작 결과 확인") }
                        state.preflight?.ready == true -> Button(
                            onClick = { confirmStart = true },
                            enabled = state.canStart,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("확인 후 수집 시작") }
                        else -> Button(
                            onClick = onPreflight,
                            enabled = state.canPreflight,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("시작 전 필수 점검") }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 720.dp
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isLoading || state.operation != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!state.online) item { AppBanner("장비 연결이 확인되지 않았습니다. 현재 화면의 선택만으로 수집을 시작할 수 없습니다.", StatusTone.WARNING) }
                if (state.contextLocked) item {
                    AppBanner(
                        if (state.pendingStart != null || state.unconfirmedRunId != null) {
                            "시작 결과를 확인하는 동안 프로젝트·구간·정책을 변경하거나 새 요청을 만들 수 없습니다."
                        } else "현재 수집 실행에 프로젝트·구간·정책이 고정되어 있습니다. 실행이 끝난 뒤 변경하세요.",
                        StatusTone.INFO
                    )
                }
                state.error?.let { error -> item {
                    AppBanner(error, StatusTone.WARNING, actionLabel = "최신 상태 다시 불러오기",
                        onAction = onRefresh, onDismiss = onDismissMessage)
                } }
                state.message?.let { message -> item {
                    AppBanner(message, StatusTone.SUCCESS, onDismiss = onDismissMessage)
                } }
                state.activeRun?.let { run -> item { ActiveRunCard(run) } }
                item {
                    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { SurveyContextPane(state, onSelectProject, onSelectSection,
                            onCreateProject, onCreateSection) }
                        Box(Modifier.weight(1f)) { PolicyPane(state, onSensorRequirement, onMinFreeBytes,
                            onOutputMinFiles, onOutputMinBytes, onOutputPatterns, onOutputRoot, onOutputPath,
                            onSavePolicy) }
                    } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SurveyContextPane(state, onSelectProject, onSelectSection, onCreateProject, onCreateSection)
                        PolicyPane(state, onSensorRequirement, onMinFreeBytes, onOutputMinFiles, onOutputMinBytes,
                            onOutputPatterns, onOutputRoot, onOutputPath, onSavePolicy)
                    }
                }
                item { PreflightPane(state.preflight) }
            }
        }
    }
}

@Composable
private fun SurveyContextPane(
    state: SurveyRunUiState,
    onSelectProject: (SurveyProject) -> Unit,
    onSelectSection: (SurveySection) -> Unit,
    onCreateProject: (String) -> Unit,
    onCreateSection: (String) -> Unit
) {
    var projectLabel by remember(state.deviceId) { mutableStateOf("") }
    var sectionLabel by remember(state.deviceId, state.selectedProject?.surveyProjectId) { mutableStateOf("") }
    SectionSurface(LocalCobaltColors.current.sectionSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("1. 프로젝트와 조사 구간")
            Text("선택은 이 장비에만 저장되며 서버의 현재 revision과 일치해야 합니다.",
                style = MaterialTheme.typography.bodySmall, color = LocalCobaltColors.current.muted)
            Text("프로젝트", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.projects.forEach { project ->
                    FilterChip(
                        selected = state.selectedProject?.surveyProjectId == project.surveyProjectId,
                        onClick = { onSelectProject(project) },
                        enabled = !state.contextLocked && state.operation == null,
                        label = { Text("${project.label} · r${project.revision}") }
                    )
                }
            }
            if (state.projects.isEmpty() && !state.isLoading) Text("등록된 프로젝트가 없습니다.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(projectLabel, { projectLabel = it.take(80) }, label = { Text("새 프로젝트 이름") },
                    singleLine = true, enabled = !state.contextLocked, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onCreateProject(projectLabel); projectLabel = "" },
                    enabled = projectLabel.isNotBlank() && !state.contextLocked && state.operation == null) { Text("만들기") }
            }
            HorizontalDivider()
            Text("조사 구간", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sections.forEach { section ->
                    FilterChip(
                        selected = state.selectedSection?.surveySectionId == section.surveySectionId,
                        onClick = { onSelectSection(section) },
                        enabled = !state.contextLocked && state.operation == null,
                        label = { Text("${section.label} · r${section.revision}") }
                    )
                }
            }
            if (state.selectedProject != null && state.sections.isEmpty() && state.operation == null) {
                Text("이 프로젝트에 등록된 구간이 없습니다.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(sectionLabel, { sectionLabel = it.take(80) }, label = { Text("새 구간 이름") },
                    singleLine = true, enabled = state.selectedProject != null && !state.contextLocked,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onCreateSection(sectionLabel); sectionLabel = "" },
                    enabled = sectionLabel.isNotBlank() && state.selectedProject != null && !state.contextLocked && state.operation == null) {
                    Text("만들기")
                }
            }
        }
    }
}

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
    val draft = state.policyDraft
    val enabled = !state.contextLocked && state.operation == null
    SectionSurface(LocalCobaltColors.current.sectionRaised) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("2. 수집 정책")
                StatusBadge(state.policy?.let { "v${it.policyVersion}" } ?: "미저장",
                    if (state.policy == null) StatusTone.WARNING else StatusTone.INFO)
            }
            Text("RTK는 품질 관찰로만 기록하며 시작 통과 기준으로 사용하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = LocalCobaltColors.current.muted)
            SupportedPolicySensors.forEach { sensor ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NumericPolicyField("최소 파일 수", draft.outputMinFiles, onOutputMinFiles, enabled) }
                Box(Modifier.weight(1f)) { NumericPolicyField("최소 bytes", draft.outputMinBytes, onOutputMinBytes, enabled) }
            }
            Text("결과 저장소", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.roots.forEach { root ->
                    FilterChip(selected = draft.outputRootId == root.id, onClick = { onOutputRoot(root.id) },
                        enabled = enabled, label = { Text(root.label) })
                }
            }
            OutlinedTextField(draft.outputPath, onOutputPath, label = { Text("결과 상대 경로 · 비워두면 저장소 루트") },
                supportingText = { if (!safeOutputPath(draft.outputPath)) Text("절대 경로와 . 또는 .. 구간은 사용할 수 없습니다.") },
                isError = !safeOutputPath(draft.outputPath), enabled = enabled, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.outputPatterns, onOutputPatterns,
                label = { Text("예상 파일 패턴 · 한 줄에 하나") }, enabled = enabled,
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = onSavePolicy, enabled = enabled && draft.valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (state.policy == null) "정책 저장" else "새 정책 버전 저장")
            }
        }
    }
}

@Composable
private fun NumericPolicyField(label: String, value: String, onValue: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PreflightPane(preflight: PipelinePreflight?) {
    SectionSurface(LocalCobaltColors.current.sectionSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeader("3. 시작 전 점검")
                StatusBadge(when {
                    preflight == null -> "점검 전"
                    preflight.ready -> "준비됨"
                    else -> "확인 필요"
                }, when {
                    preflight == null -> StatusTone.INFO
                    preflight.ready -> StatusTone.SUCCESS
                    else -> StatusTone.WARNING
                })
            }
            if (preflight == null) Text("프로젝트·구간과 저장된 정책으로 장비 시간, 저장 공간, 센서를 점검합니다.")
            else {
                Text("점검 ID ${preflight.preflightId}", style = MaterialTheme.typography.bodySmall)
                Text("시간 ${checkLabel(preflight.checks.time.state)} · 저장 공간 ${checkLabel(preflight.checks.storage.state)}")
                preflight.checks.sensors.forEach { sensor ->
                    Text("${sensorLabel(sensor.sensor)} · ${requirementLabel(sensor.requirement)} · ${checkLabel(sensor.state)}" +
                        sensor.detail?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
                preflight.problems.forEach { problem ->
                    Text("• ${problem.detail ?: problem.code}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ActiveRunCard(run: com.example.jetsoncontroller.model.PipelineRun) {
    SectionSurface(LocalCobaltColors.current.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("현재 실행에 고정된 조사 범위", style = MaterialTheme.typography.titleMedium,
                color = LocalCobaltColors.current.heroText)
            Text("${run.contextSnapshot.surveyProjectLabel} · ${run.contextSnapshot.surveySectionLabel}",
                color = LocalCobaltColors.current.heroText)
            Text("Run ${run.runId} · ${run.state} · ${run.output.rootId}/${run.output.path}",
                style = MaterialTheme.typography.bodySmall, color = LocalCobaltColors.current.heroText)
        }
    }
}

internal fun startReadinessLabel(state: SurveyRunUiState): String = when {
    !state.online -> "장비 연결 확인 필요"
    state.pendingStart != null -> "시작 결과 미확인 · 저장된 요청 ID 재사용"
    state.unconfirmedRunId != null -> "최근 실행 상태 미확인 · 같은 장비에서 재조회 필요"
    state.contextLocked -> "현재 실행이 끝날 때까지 조사 범위 잠김"
    !state.selectionComplete -> "프로젝트와 조사 구간 선택 필요"
    state.policy == null -> "수집 정책 저장 필요"
    state.preflight == null -> "시작 전 점검 필요"
    !state.preflight.ready -> "점검 문제 확인 필요"
    !state.canStart -> "선택 또는 정책 변경됨 · 다시 점검 필요"
    else -> "프로젝트·구간·정책·필수 점검 확인됨"
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
