package com.example.jetsoncontroller.ui.model

import com.example.jetsoncontroller.ui.field.FieldStage
import com.example.jetsoncontroller.ui.field.FieldStageInput
import com.example.jetsoncontroller.ui.field.judgeFieldStage

enum class OperationState {
    NO_DEVICE, START_PENDING, RUN_UNCONFIRMED, DISCONNECTED, COLLECTING,
    MULTIPLE_RUNS, NEEDS_RESULT, NEEDS_REPORT, BASIC_CONNECTION, IDLE
}

enum class NextActionCommand(val label: String) {
    REGISTER("QR 코드 스캔"), CHECK_START("시작 결과 확인"), CHECK_STATE("상태 다시 확인"),
    VIEW_RUN("진행 보기"), VIEW_RESULT("결과 보기"), REPORT("완료 보고"),
    WIFI_SETTINGS("장치 Wi-Fi 설정"), REGISTER_AGAIN("QR 코드로 다시 등록"),
    ALLOW_PERMISSION("권한 허용"), RECONNECT("다시 연결"), CONNECTION_HELP("연결 방법")
}

data class TransportRemedy(
    val primary: NextActionCommand,
    val secondary: NextActionCommand? = null,
    val detail: String? = null
)

data class NextActionInput(
    val deviceName: String,
    val field: FieldStageInput,
    val activeRunCount: Int = field.activeRunCount,
    val receiptVerified: Boolean = false,
    val ticketLinked: Boolean = false,
    val completionReported: Boolean = false,
    val basicConnection: Boolean = false,
    val credentialsRejected: Boolean = false,
    val permissionMissing: Boolean = false,
    val lastResponseLabel: String? = null,
    val zoneLabel: String? = null
)

data class NextAction(
    val operation: OperationState,
    val title: String?,
    val body: String?,
    /** The operation's action; a remedy never overwrites the operation or its copy. */
    val operationAction: NextActionCommand?,
    val transportRemedy: TransportRemedy? = null,
    /** Result and report remain available on their rows even when Focus is disconnected. */
    val offlineAction: NextActionCommand? = null
) {
    val primary: NextActionCommand? get() = transportRemedy?.primary ?: operationAction
    val secondary: NextActionCommand? get() = transportRemedy?.secondary
}

fun nextAction(input: NextActionInput): NextAction {
    val field = input.field
    if (field.registeredDeviceCount <= 0) return NextAction(
        OperationState.NO_DEVICE, null, null, NextActionCommand.REGISTER
    )
    val device = input.deviceName
    val survey = field.surveyLabel ?: device
    val activeRunCount = maxOf(input.activeRunCount, field.activeRunCount, if (field.activeRunId == null) 0 else 1)
    val reportable = input.receiptVerified && input.ticketLinked && !input.completionReported
    val offlineAction = when {
        field.runAwaitingResultId != null -> NextActionCommand.VIEW_RESULT
        reportable -> NextActionCommand.REPORT
        else -> null
    }
    // Use the existing safety order, including offline before active run/result.
    val stage = judgeFieldStage(field.copy(activeRunCount = activeRunCount))
    val operation = when (stage) {
        FieldStage.START_PENDING -> OperationState.START_PENDING
        FieldStage.RUN_UNCONFIRMED -> OperationState.RUN_UNCONFIRMED
        FieldStage.NOT_CONNECTED -> OperationState.DISCONNECTED
        FieldStage.COLLECTING -> if (activeRunCount > 1) OperationState.MULTIPLE_RUNS else OperationState.COLLECTING
        FieldStage.NEEDS_RESULT -> OperationState.NEEDS_RESULT
        else -> when {
            reportable -> OperationState.NEEDS_REPORT
            input.basicConnection -> OperationState.BASIC_CONNECTION
            else -> OperationState.IDLE
        }
    }
    val plan = when (operation) {
        OperationState.START_PENDING -> NextAction(operation,
            "$device 수집 시작 결과를 확인해야 합니다",
            "같은 요청의 결과를 확인할 때까지 새 수집을 시작할 수 없습니다.", NextActionCommand.CHECK_START)
        OperationState.RUN_UNCONFIRMED -> NextAction(operation,
            "$device 수집 상태를 확인하지 못했습니다",
            listOfNotNull(field.lastObservedLabel?.let { "마지막 확인 $it" },
                "상태를 확인할 때까지 새 수집을 시작할 수 없습니다.").joinToString(" · "), NextActionCommand.CHECK_STATE)
        OperationState.DISCONNECTED -> NextAction(operation, "$device 연결이 끊겼습니다",
            listOfNotNull(input.lastResponseLabel?.let { "마지막 응답 $it" },
                "수집은 장치에서 계속될 수 있습니다.".takeIf { activeRunCount > 0 }).joinToString(" · "), null)
        OperationState.COLLECTING -> NextAction(operation, "$survey 수집 중",
            listOfNotNull(field.elapsedLabel?.let { "$it 경과" }, input.zoneLabel).joinToString(" · "), NextActionCommand.VIEW_RUN)
        OperationState.MULTIPLE_RUNS -> NextAction(operation, "${device}에서 수집 ${activeRunCount}건이 진행 중으로 보입니다",
            "새로 시작하기 전에 진행 중인 수집을 확인하세요.", NextActionCommand.VIEW_RUN)
        OperationState.NEEDS_RESULT -> NextAction(operation, "$survey 수집이 끝났습니다", null, NextActionCommand.VIEW_RESULT)
        OperationState.NEEDS_REPORT -> NextAction(operation, "$survey 서버 수신을 확인했습니다",
            "GEO& Work에 완료를 보고하세요.", NextActionCommand.REPORT)
        OperationState.BASIC_CONNECTION -> NextAction(operation, "$device 기본 연결입니다",
            "같은 네트워크에 연결하면 모든 기능을 쓸 수 있습니다.", NextActionCommand.WIFI_SETTINGS)
        OperationState.IDLE -> NextAction(operation, null, null, null)
        OperationState.NO_DEVICE -> error("Handled before stage evaluation")
    }
    val remedy = if (!field.connected && operation in setOf(
            OperationState.START_PENDING, OperationState.RUN_UNCONFIRMED,
            OperationState.DISCONNECTED, OperationState.MULTIPLE_RUNS
        )) when {
        input.credentialsRejected -> TransportRemedy(NextActionCommand.REGISTER_AGAIN,
            detail = "$device 인증 정보를 확인할 수 없습니다.")
        input.permissionMissing -> TransportRemedy(NextActionCommand.ALLOW_PERMISSION,
            detail = "$device · 연결 권한이 필요합니다")
        else -> TransportRemedy(NextActionCommand.RECONNECT, NextActionCommand.CONNECTION_HELP)
    } else null
    return plan.copy(transportRemedy = remedy, offlineAction = offlineAction)
}
