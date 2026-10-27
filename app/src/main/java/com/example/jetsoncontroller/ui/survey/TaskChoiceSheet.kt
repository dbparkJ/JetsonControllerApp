package com.example.jetsoncontroller.ui.survey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.TaskState
import com.example.jetsoncontroller.ui.pipelines.PipelineUiState
import com.example.jetsoncontroller.ui.pipelines.PipelineViewModel
import java.time.Instant

/** The caller keeps its PreparationDraft while this sheet only chooses the task. */
@Composable
internal fun TaskChoiceSheet(
    viewModel: PipelineViewModel,
    deviceId: String,
    deviceName: String,
    ticketTitle: String?,
    onSelect: (ManagedPipeline) -> Unit,
    onDismissRequest: () -> Unit,
    openerFocusRequester: FocusRequester? = null,
    selectedPipelineId: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    TaskChoiceSheet(state, deviceId, deviceName, ticketTitle, onSelect, onDismissRequest, openerFocusRequester, selectedPipelineId)
}

@Composable
internal fun TaskChoiceSheet(
    state: PipelineUiState,
    deviceId: String,
    deviceName: String,
    ticketTitle: String?,
    onSelect: (ManagedPipeline) -> Unit,
    onDismissRequest: () -> Unit,
    openerFocusRequester: FocusRequester? = null,
    selectedPipelineId: String? = null
) {
    val sameDevice = state.deviceId.equals(deviceId, true)
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.deviceId, deviceId) {
        if (!sameDevice) onDismissRequest()
    }
    GeoSheet("수집 작업 선택", onDismissRequest,
        kind = GeoSheetKind.CHOICE,
        subtitle = listOfNotNull(ticketTitle, deviceName).joinToString(" · "),
        openerFocusRequester = openerFocusRequester) {
        when {
            !sameDevice || state.isLoading -> GeoSkeleton("수집 작업을 불러오는 중", kind = GeoSkeletonKind.Rows)
            state.error != null -> GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
                "수집 작업을 불러오지 못했습니다", state.error))
            state.pipelines.isEmpty() -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                "이 장치에 수집 작업이 없습니다", "관리자에게 작업 등록을 요청하세요."))
            else -> Column(Modifier.selectableGroup()) { state.pipelines.forEach { pipeline ->
                val word = taskChoiceState(pipeline)
                GeoRadioRow(pipeline.label, selectedPipelineId == pipeline.id, {
                    // A tap on a stale row cannot select a task on the next device.
                    latestState.takeIf { it.deviceId.equals(deviceId, true) }?.pipelines
                        ?.firstOrNull { it.id == pipeline.id }?.let { current ->
                            if (latestState.controlAvailable) onSelect(current)
                        }
                }, description = pipeline.execution?.startedAt?.let { time ->
                    runCatching { Instant.parse(time) }.getOrNull()?.let { "마지막 실행 ${Formats.time(it)}" }
                }, badge = { GeoBadge(word.word, word.tone) },
                    enabled = state.controlAvailable && state.busyPipelineId == null,
                    disabledReason = "연결되면 작업을 선택할 수 있습니다.")
            } }
        }
    }
}

internal fun taskChoiceState(pipeline: ManagedPipeline): TaskState = taskChoiceState(pipeline.state)

internal fun taskChoiceState(state: PipelineState): TaskState = when (state) {
    PipelineState.STOPPED -> TaskState.WAITING
    PipelineState.RUNNING -> TaskState.COLLECTING
    PipelineState.STARTING -> TaskState.START_REQUESTED
    PipelineState.STOPPING -> TaskState.STOP_REQUESTED
    PipelineState.RETRYING -> TaskState.RETRYING
    PipelineState.WAITING_FOR_TIME_SYNC -> TaskState.ALIGNING_TIME
    PipelineState.FAILED -> TaskState.FAILED
    PipelineState.UNKNOWN -> TaskState.UNKNOWN
}
