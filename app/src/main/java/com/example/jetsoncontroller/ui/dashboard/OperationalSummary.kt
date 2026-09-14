package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.components.StatusTone

internal data class OperationalSignal(
    val title: String,
    val value: String,
    val detail: String,
    val tone: StatusTone
)

internal enum class HomeActionDestination {
    CONNECTION,
    PIPELINES,
    SENSORS,
    STORAGE
}

internal data class HomeNextAction(
    val eyebrow: String,
    val title: String,
    val detail: String,
    val buttonLabel: String,
    val destination: HomeActionDestination
)

internal data class OperationalSummary(
    val connection: OperationalSignal,
    val collection: OperationalSignal,
    val internet: OperationalSignal,
    val positioning: OperationalSignal,
    val nextAction: HomeNextAction
)

internal fun operationalSummary(
    state: DashboardUiState,
    pipelines: List<ManagedPipeline>,
    uploads: List<UploadJob>,
    tasksConfirmed: Boolean
): OperationalSummary {
    val reportedRunning = pipelines.filter { it.state == PipelineState.RUNNING }
    val running = reportedRunning.filter { tasksConfirmed && !it.activeRunId.isNullOrBlank() }
    val transitional = pipelines.filter {
        it.state in setOf(PipelineState.STARTING, PipelineState.STOPPING, PipelineState.RETRYING)
    }
    val connectionStage = com.example.jetsoncontroller.ui.connection.userConnectionStage(
        state.isOnline,
        state.transportType
    )
    val connection = OperationalSignal(
        title = "앱 ↔ 장비",
        value = connectionStage.label,
        detail = when {
            state.fullControlAvailable -> "인증된 제어 경로"
            state.isOnline -> "기본 연결 · 전체 제어 필요"
            else -> "장비 연결을 확인하세요"
        },
        tone = when {
            state.fullControlAvailable -> StatusTone.SUCCESS
            state.isOnline -> StatusTone.WARNING
            else -> StatusTone.ERROR
        }
    )
    val collection = when {
        running.size == 1 -> OperationalSignal(
            title = "Jetson 수집",
            value = "수집 중",
            detail = running.single().label,
            tone = StatusTone.SUCCESS
        )
        running.size > 1 -> OperationalSignal(
            title = "Jetson 수집",
            value = "실행 ${running.size}개 확인 필요",
            detail = "중복 실행 여부를 확인하세요",
            tone = StatusTone.WARNING
        )
        reportedRunning.isNotEmpty() -> OperationalSignal(
            title = "Jetson 수집",
            value = "최근 실행 보고 · 현재 미확인",
            detail = "현재 관찰 시각과 실행 ID를 다시 확인하세요",
            tone = StatusTone.WARNING
        )
        transitional.isNotEmpty() -> OperationalSignal(
            title = "Jetson 수집",
            value = "변경 확인 중",
            detail = "명령을 반복하지 말고 실제 상태를 확인하세요",
            tone = StatusTone.WARNING
        )
        pipelines.any { it.state in setOf(PipelineState.FAILED, PipelineState.UNKNOWN) } -> OperationalSignal(
            title = "Jetson 수집",
            value = "실행 결과 확인 필요",
            detail = "실패 또는 알 수 없는 작업 상태를 확인하세요",
            tone = StatusTone.WARNING
        )
        tasksConfirmed -> OperationalSignal(
            title = "Jetson 수집",
            value = "수집 안 함",
            detail = "최근 조회에서 실행 중인 작업 없음",
            tone = StatusTone.INFO
        )
        else -> OperationalSignal(
            title = "Jetson 수집",
            value = "현재 상태 미확인",
            detail = "앱 연결 상태만으로 중단을 판단하지 않습니다",
            tone = StatusTone.WARNING
        )
    }
    val activeUpload = uploads.firstOrNull {
        it.state in setOf(UploadJobState.SCANNING, UploadJobState.UPLOADING)
    }
    val verifiedUpload = uploads.firstOrNull {
        it.state == UploadJobState.COMPLETED && it.verification?.matched == true
    }
    val internet = when {
        activeUpload != null -> OperationalSignal(
            title = "인터넷 · 서버",
            value = "전송 관찰 중",
            detail = activeUpload.folderName ?: activeUpload.relativePath,
            tone = StatusTone.INFO
        )
        verifiedUpload != null -> OperationalSignal(
            title = "인터넷 · 서버",
            value = "수신 증거 있음",
            detail = "현재 도달성은 서버에서 다시 확인하세요",
            tone = StatusTone.SUCCESS
        )
        else -> OperationalSignal(
            title = "인터넷 · 서버",
            value = "현재 상태 미확인",
            detail = "장비 Wi-Fi와 별도로 확인합니다",
            tone = StatusTone.INFO
        )
    }
    val positioning = positioningSignal(state.status, state.statusFreshness)
    val nextAction = when {
        !state.fullControlAvailable -> HomeNextAction(
            eyebrow = "다음 행동",
            title = "장비 제어 연결 확인",
            detail = "선택 장비와 인증된 LAN 또는 직접 연결을 확인하세요.",
            buttonLabel = "연결 방법 보기",
            destination = HomeActionDestination.CONNECTION
        )
        running.isNotEmpty() || reportedRunning.isNotEmpty() || transitional.isNotEmpty() -> HomeNextAction(
            eyebrow = "다음 행동",
            title = if (running.isNotEmpty()) "수집 상태 계속 확인" else "명령 결과 다시 확인",
            detail = if (running.isNotEmpty()) {
                "앱 연결과 별개로 Jetson의 실제 실행, 경로와 저장 상태를 확인하세요."
            } else {
                "적용 여부가 불명확한 동안 시작·중지 명령을 반복하지 마세요."
            },
            buttonLabel = "작업 상태 보기",
            destination = HomeActionDestination.PIPELINES
        )
        !tasksConfirmed -> HomeNextAction(
            eyebrow = "다음 행동",
            title = "작업 상태 최신화",
            detail = "Jetson에서 최근 실행 상태를 확인한 뒤 새 수집을 준비하세요.",
            buttonLabel = "작업 확인",
            destination = HomeActionDestination.PIPELINES
        )
        !state.status.sensorTelemetryAvailable || !state.status.sensorTelemetryFresh -> HomeNextAction(
            eyebrow = "다음 행동",
            title = "시작 전 센서 확인",
            detail = "필수 여부와 합격 기준은 작업 정책을 따릅니다. 현재 관찰값을 먼저 확인하세요.",
            buttonLabel = "센서 상태 보기",
            destination = HomeActionDestination.SENSORS
        )
        else -> HomeNextAction(
            eyebrow = "다음 행동",
            title = "수집 작업 선택",
            detail = "작업과 출력 위치를 확인하고 최종 점검 후 시작하세요.",
            buttonLabel = "작업 선택",
            destination = HomeActionDestination.PIPELINES
        )
    }
    return OperationalSummary(connection, collection, internet, positioning, nextAction)
}

private fun positioningSignal(status: JetsonStatus, freshness: StatusFreshness): OperationalSignal {
    if (freshness != StatusFreshness.CURRENT || !status.sensorTelemetryAvailable ||
        !status.sensorTelemetryFresh
    ) {
        return OperationalSignal(
            title = "GNSS · RTK",
            value = "현재 상태 미확인",
            detail = "마지막 관찰 시각을 확인하세요",
            tone = StatusTone.WARNING
        )
    }
    if (!status.gnssSensor.configured) {
        return OperationalSignal(
            title = "GNSS · RTK",
            value = "미설정",
            detail = "작업의 필수 센서 정책을 확인하세요",
            tone = StatusTone.INFO
        )
    }
    if (!status.gnssSensor.connected) {
        return OperationalSignal(
            title = "GNSS · RTK",
            value = "GNSS 연결 안 됨",
            detail = status.gnssSensor.error ?: "배선과 수신기 상태를 확인하세요",
            tone = StatusTone.WARNING
        )
    }
    val fix = status.gnssSensor.fixName.takeUnless { it.equals("unknown", true) }
        ?: status.gnssSensor.fixType.takeUnless { it.equals("none", true) }
        ?: "위치 수신 중"
    val rtk = status.gnssSensor.rtkStatus.takeUnless { it.equals("unknown", true) }
    return OperationalSignal(
        title = "GNSS · RTK",
        value = listOfNotNull(fix, rtk).joinToString(" · "),
        detail = listOfNotNull(
            status.gnssSensor.satellites?.let { "위성 ${it}개" },
            status.gnssSensor.differentialAgeS?.let { "보정 ${"%.1f".format(it)}초 전" }
        ).joinToString(" · ").ifBlank { "관찰값 · 승인 임계값 없음" },
        tone = StatusTone.INFO
    )
}
