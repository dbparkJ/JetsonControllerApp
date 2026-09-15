package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.model.PipelineState

/** Poll interval is 5 seconds. After three missed observations, never claim live execution. */
fun tasksAreFresh(available: Boolean, observedAt: Long?, now: Long): Boolean =
    available && observedAt != null && now >= observedAt && now - observedAt <= 15_000L

fun taskStateLabel(state: PipelineState, confirmed: Boolean, pendingAction: String? = null): String = when {
    !confirmed -> "현재 상태 미확인 · 마지막 ${taskStateLabel(state, true)}"
    pendingAction != null -> when (pendingAction) {
        "start", "restart" -> "시작 요청 중 · 실행 확인 대기"
        "stop" -> "중지 요청 중 · 종료 확인 대기"
        else -> "요청 결과 확인 필요"
    }
    else -> when (state) {
        PipelineState.RUNNING -> "실행 중"
        PipelineState.STARTING -> "시작 요청 중"
        PipelineState.STOPPING -> "중지 요청 중"
        PipelineState.STOPPED -> "대기"
        PipelineState.FAILED -> "실패"
        PipelineState.RETRYING -> "재시도 중"
        PipelineState.WAITING_FOR_TIME_SYNC -> "시간 동기화 대기"
        PipelineState.UNKNOWN -> "현재 상태 미확인"
    }
}

internal fun taskStateLabel(
    pipeline: com.example.jetsoncontroller.model.ManagedPipeline,
    confirmed: Boolean,
    pendingAction: String? = null
): String = when {
    !confirmed -> "현재 상태 미확인 · 마지막 ${taskStateLabel(pipeline.state, true)}"
    pendingAction != null -> taskStateLabel(pipeline.state, true, pendingAction)
    pipeline.state == PipelineState.RUNNING && pipeline.activeRunId.isNullOrBlank() ->
        "실행 보고 · 실행 ID 확인 필요"
    pipeline.state == PipelineState.RUNNING -> "실행 중 · ${pipeline.activeRunId}"
    else -> taskStateLabel(pipeline.state, true)
}

internal fun reconcileTaskRequests(requests: Map<String, String>, states: Map<String, PipelineState>): Map<String, String> =
    requests.filter { (id, action) ->
        val state = states[id]
        when (action) {
            "start", "restart" -> state !in setOf(PipelineState.RUNNING, PipelineState.FAILED, PipelineState.STOPPED)
            "stop" -> state !in setOf(PipelineState.STOPPED, PipelineState.FAILED)
            else -> state == null || state == PipelineState.UNKNOWN
        }
    }
