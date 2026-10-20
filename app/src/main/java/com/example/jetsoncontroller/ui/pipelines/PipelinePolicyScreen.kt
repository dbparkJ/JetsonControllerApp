package com.example.jetsoncontroller.ui.pipelines

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.survey.RunPolicyDraft
import com.example.jetsoncontroller.ui.survey.SensorRequirementChoice
import com.example.jetsoncontroller.ui.survey.toDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class PipelinePolicyState(
    val draft: RunPolicyDraft = RunPolicyDraft(), val policy: PipelineRunPolicy? = null,
    val roots: List<RemoteRoot> = emptyList(), val online: Boolean = false,
    val busy: Boolean = false, val needsReview: Boolean = false, val message: String? = null
)

internal class PipelinePolicyViewModel(private val repository: JetsonRepository,
    private val deviceId: String?, private val pipelineId: String) : ViewModel() {
    private val mutable = MutableStateFlow(PipelinePolicyState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private fun targetOnline(): Boolean {
        val transport = repository.transportState.value as? TransportState.Connected ?: return false
        return repository.selectedDeviceId.value.equals(deviceId, true) && transport.deviceId.equals(deviceId, true) &&
            transport.type != TransportType.BLE
    }
    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { id, transport ->
                id.equals(deviceId, true) && transport is TransportState.Connected && transport.deviceId.equals(deviceId, true) &&
                    transport.type != TransportType.BLE
            }.distinctUntilChanged().collectLatest { online ->
                val expected = ++generation
                mutable.value = mutable.value.copy(online = online, busy = online)
                if (online) load(expected)
            }
        }
    }
    private suspend fun load(expected: Int) {
        val roots = repository.getWorkspaceRoots()
        val result = repository.pipelineRunPolicy(pipelineId)
        if (expected != generation) return
        mutable.value = mutable.value.copy(busy = false, roots = roots.getOrDefault(emptyList()),
            policy = result.getOrNull(), draft = result.getOrNull()?.toDraft() ?: mutable.value.draft,
            needsReview = false, message = if (result.isFailure) "수집 정책을 불러오지 못했습니다. 다시 시도하세요." else null)
    }
    fun edit(update: RunPolicyDraft.() -> RunPolicyDraft) {
        if (!mutable.value.online || mutable.value.busy) return
        mutable.value = mutable.value.copy(draft = mutable.value.draft.update(), message = null)
    }
    fun reload() {
        if (!mutable.value.online || mutable.value.busy) return
        val expected = ++generation
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch { load(expected) }
    }
    fun save() {
        val current = mutable.value
        if (!current.online || !targetOnline() || current.busy || current.needsReview || current.policy == null || !current.draft.valid) return
        val request = current.draft.request(current.policy)
        val expected = generation
        mutable.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                if (!targetOnline()) return@launch
                val result = repository.updatePipelineRunPolicy(pipelineId, request)
                if (expected != generation || !targetOnline()) return@launch
                mutable.value = result.fold(
                    { saved -> mutable.value.copy(policy = saved, draft = saved.toDraft(), busy = false, message = "수집 정책을 저장했습니다.") },
                    { mutable.value.copy(busy = false, needsReview = true, message = "수집 정책을 저장하지 못했습니다. 장치 설정을 다시 불러와 확인하세요.") })
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { if (expected == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    class Factory(private val repository: JetsonRepository, private val deviceId: String?, private val pipelineId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PipelinePolicyViewModel(repository, deviceId, pipelineId) as T
    }
}

@Composable
internal fun PipelinePolicyScreen(state: PipelinePolicyState, label: String, deviceName: String,
    onBack: () -> Unit, onEdit: (RunPolicyDraft.() -> RunPolicyDraft) -> Unit, onSave: () -> Unit, onReload: () -> Unit) {
    var rootPicker by remember { mutableStateOf(false) }
    var confirmReload by remember { mutableStateOf(false) }
    var minimumFree by remember { mutableStateOf(PolicySizeEdit(policySizeInput(state.draft.minFreeBytes))) }
    var minimumOutput by remember { mutableStateOf(PolicySizeEdit(policySizeInput(state.draft.outputMinBytes))) }
    LaunchedEffect(state.draft.minFreeBytes, state.busy) {
        if (state.busy || minimumFree.sourceBytes != state.draft.minFreeBytes)
            minimumFree = PolicySizeEdit(policySizeInput(state.draft.minFreeBytes))
    }
    LaunchedEffect(state.draft.outputMinBytes, state.busy) {
        if (state.busy || minimumOutput.sourceBytes != state.draft.outputMinBytes)
            minimumOutput = PolicySizeEdit(policySizeInput(state.draft.outputMinBytes))
    }
    val enabled = state.online && !state.busy
    val validUnits = minimumFree.bytes != null && minimumOutput.bytes != null
    if (rootPicker) GeoSheet("결과 저장소", { rootPicker = false }) {
        state.roots.forEach { root -> GeoRadioRow(root.label, root.id == state.draft.outputRootId,
            { rootPicker = false; onEdit { copy(outputRootId = root.id) } }) }
    }
    if (confirmReload) GeoConfirmDialog("장치 설정을 다시 불러올까요?", "작성 중인 내용을 장치의 현재 설정으로 바꿉니다.", "다시 불러오기",
        { confirmReload = false; if (enabled) onReload() }, { confirmReload = false }, dismissLabel = "초안 유지", confirmEnabled = enabled,
        disabledReason = "장치에 연결되고 저장이 끝나야 다시 불러올 수 있습니다.")
    DevPage("수집 정책", onBack, footer = {
        GeoButton("정책 저장", onSave, Modifier.fillMaxWidth(), enabled = enabled && state.policy != null && state.draft.valid && validUnits && !state.needsReview,
            actionArea = true, disabledReason = "장치 연결과 수집 정책 값을 확인하세요.")
    }) {
        DevIdentity(label, deviceName)
        DevFeedback(state.message, state.needsReview)
        if (state.busy) DevText("확인 중")
        GeoButton("장치 설정 다시 불러오기", { confirmReload = true }, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = enabled)
        GeoSection("센서") {
            for ((key, name) in listOf("camera" to "카메라", "gnss" to "위치(GNSS)", "imu" to "움직임(IMU)")) {
                DevChoice(name, listOf("필수", "선택", "사용 안 함"), (state.draft.sensors[key] ?: SensorRequirementChoice.NOT_USED).ordinal,
                    { choice -> onEdit { copy(sensors = sensors + (key to SensorRequirementChoice.entries[choice])) } }, enabled)
                Spacer(Modifier.height(16.dp))
            }
        }
        GeoSection("시작 전 점검") {
            GeoTextField(minimumFree.text, { value -> minimumFree = minimumFree.edit(value)
                minimumFree.bytes?.let { onEdit { copy(minFreeBytes = it) } }
            }, "최소 여유 공간", numeric = true, unit = minimumFree.input.unit, enabled = enabled,
                error = if (minimumFree.bytes == null) "0 이상의 용량을 입력하세요." else null,
                disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
        }
        GeoSection("예상 결과") {
            val files: @Composable (Modifier) -> Unit = { modifier ->
                GeoTextField(state.draft.outputMinFiles, { value -> onEdit { copy(outputMinFiles = value.take(9)) } }, "최소 파일 수", modifier,
                    numeric = true, unit = "개", enabled = enabled, disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
            }
            val size: @Composable (Modifier) -> Unit = { modifier ->
                GeoTextField(minimumOutput.text, { value -> minimumOutput = minimumOutput.edit(value)
                    minimumOutput.bytes?.let { onEdit { copy(outputMinBytes = it) } }
                }, "최소 용량", modifier, numeric = true, unit = minimumOutput.input.unit, enabled = enabled,
                    error = if (minimumOutput.bytes == null) "0 이상의 용량을 입력하세요." else null,
                    disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { files(Modifier); size(Modifier) }
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { files(Modifier.weight(1f)); size(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
            GeoPickerField("결과 저장소", state.roots.firstOrNull { it.id == state.draft.outputRootId }?.label ?: "선택 안 됨",
                { rootPicker = true }, enabled = enabled, disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
            Spacer(Modifier.height(16.dp))
            GeoTextField(state.draft.outputPath, { value -> onEdit { copy(outputPath = value.take(512)) } }, "상대 경로",
                helper = "비워 두면 저장소 맨 위에 저장합니다.", enabled = enabled, disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
            Spacer(Modifier.height(16.dp))
            GeoTextField(state.draft.outputPatterns, { value -> onEdit { copy(outputPatterns = value.take(2000)) } }, "파일 패턴",
                helper = "한 줄에 하나씩 입력하세요.", singleLine = false, enabled = enabled, disabledReason = "장치에 연결하면 바꿀 수 있습니다.")
        }
    }
}
