package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MobileRtkRelayState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/** Keep only an empty loading placeholder; explicit invalid metrics and sensor failures replace it. */
internal fun retainPreparationStatus(
    previous: PreparationTelemetry,
    incoming: PreparationTelemetry
): PreparationTelemetry = if (
    !incoming.deviceId.isNullOrBlank() && incoming.deviceId.equals(previous.deviceId, true) &&
    incoming.status == JetsonStatus()
) incoming.copy(status = previous.status) else incoming

private data class PreparationRelayLabel(val pipelineId: String?, val label: String?)

/** Brief renewals keep the registered label; errors, stops and a new pipeline are immediate. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<MobileRtkRelayState>.preparationRelayLabels(): Flow<String?> = flow {
    var displayed: PreparationRelayLabel? = null
    emitAll(this@preparationRelayLabels.map { relay ->
        PreparationRelayLabel(relay.pipelineId, relay.pipelineId?.let {
            when {
                relay.error != null -> "오류"
                relay.active -> "중계 등록됨"
                relay.preparing -> "연결하는 중"
                else -> "꺼짐"
            }
        })
    }.distinctUntilChanged().transformLatest { next ->
        if (next.pipelineId == displayed?.pipelineId && next.label == "연결하는 중" &&
            displayed?.label == "중계 등록됨") delay(750)
        emit(next.label)
        displayed = next
    })
}
