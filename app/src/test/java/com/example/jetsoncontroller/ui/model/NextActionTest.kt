package com.example.jetsoncontroller.ui.model

import com.example.jetsoncontroller.ui.field.FieldStageInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextActionTest {
    private val idle = NextActionInput("GEO-X-1", FieldStageInput(
        connected = true, runStateConfirmed = true, registeredDeviceCount = 1,
        surveyLabel = "신월4거리", elapsedLabel = "00:12:34", lastObservedLabel = "16:36:02"
    ), zoneLabel = "구역 안")

    @Test
    fun `every operation row has the specified copy and action`() {
        data class Case(val input: NextActionInput, val state: OperationState, val title: String?, val action: NextActionCommand?)
        val cases = listOf(
            Case(idle.copy(field = idle.field.copy(registeredDeviceCount = 0)), OperationState.NO_DEVICE, null, NextActionCommand.REGISTER),
            Case(idle.copy(field = idle.field.copy(pendingStartRequestId = "request")), OperationState.START_PENDING,
                "GEO-X-1 수집 시작 결과를 확인해야 합니다", NextActionCommand.CHECK_START),
            Case(idle.copy(field = idle.field.copy(unconfirmedRunId = "run")), OperationState.RUN_UNCONFIRMED,
                "GEO-X-1 수집 상태를 확인하지 못했습니다", NextActionCommand.CHECK_STATE),
            Case(idle.copy(field = idle.field.copy(connected = false)), OperationState.DISCONNECTED,
                "GEO-X-1 연결이 끊겼습니다", NextActionCommand.RECONNECT),
            Case(idle.copy(field = idle.field.copy(activeRunId = "run"), activeRunCount = 1), OperationState.COLLECTING,
                "신월4거리 수집 중", NextActionCommand.VIEW_RUN),
            Case(idle.copy(activeRunCount = 2), OperationState.MULTIPLE_RUNS,
                "GEO-X-1에서 수집 2건이 진행 중으로 보입니다", NextActionCommand.VIEW_RUN),
            Case(idle.copy(field = idle.field.copy(runAwaitingResultId = "run")), OperationState.NEEDS_RESULT,
                "신월4거리 수집이 끝났습니다", NextActionCommand.VIEW_RESULT),
            Case(idle.copy(receiptVerified = true, ticketLinked = true), OperationState.NEEDS_REPORT,
                "신월4거리 서버 수신을 확인했습니다", NextActionCommand.REPORT),
            Case(idle.copy(basicConnection = true), OperationState.BASIC_CONNECTION,
                "GEO-X-1 기본 연결입니다", NextActionCommand.WIFI_SETTINGS),
            Case(idle, OperationState.IDLE, null, null)
        )
        cases.forEach { (input, state, title, command) ->
            val actual = nextAction(input)
            assertEquals(state, actual.operation)
            assertEquals(title, actual.title)
            assertEquals(command, actual.primary)
        }
        assertEquals("마지막 확인 16:36:02 · 상태를 확인할 때까지 새 수집을 시작할 수 없습니다.",
            nextAction(idle.copy(field = idle.field.copy(runStateConfirmed = false))).body)
        assertEquals("00:12:34 경과 · 구역 안",
            nextAction(idle.copy(activeRunCount = 1)).body)
    }

    @Test
    fun `FieldStage safety order precedes connectivity active runs results reports and basic connection`() {
        val all = idle.copy(field = idle.field.copy(pendingStartRequestId = "request", unconfirmedRunId = "run",
            activeRunId = "active", runAwaitingResultId = "ended", connected = false), activeRunCount = 2,
            receiptVerified = true, ticketLinked = true, basicConnection = true)
        assertEquals(OperationState.START_PENDING, nextAction(all).operation)
        val unknown = all.copy(field = all.field.copy(pendingStartRequestId = null))
        assertEquals(OperationState.RUN_UNCONFIRMED, nextAction(unknown).operation)
        val offline = unknown.copy(field = unknown.field.copy(unconfirmedRunId = null))
        assertEquals(OperationState.DISCONNECTED, nextAction(offline).operation)
        val connectedUnknown = offline.copy(field = offline.field.copy(connected = true, runStateConfirmed = false))
        assertEquals(OperationState.RUN_UNCONFIRMED, nextAction(connectedUnknown).operation)
        val connected = connectedUnknown.copy(field = connectedUnknown.field.copy(runStateConfirmed = true))
        assertEquals(OperationState.MULTIPLE_RUNS, nextAction(connected).operation)
        assertEquals(OperationState.NEEDS_RESULT, nextAction(connected.copy(activeRunCount = 0,
            field = connected.field.copy(activeRunId = null))).operation)
    }

    @Test
    fun `all transport remedies preserve the operation text and both reconnect actions appear immediately`() {
        val pending = idle.copy(field = idle.field.copy(connected = false, pendingStartRequestId = "request"))
        val expected = nextAction(pending)
        listOf(
            pending to TransportRemedy(NextActionCommand.RECONNECT, NextActionCommand.CONNECTION_HELP),
            pending.copy(permissionMissing = true) to TransportRemedy(NextActionCommand.ALLOW_PERMISSION,
                detail = "GEO-X-1 · 연결 권한이 필요합니다"),
            pending.copy(credentialsRejected = true, permissionMissing = true) to TransportRemedy(NextActionCommand.REGISTER_AGAIN,
                detail = "GEO-X-1 인증 정보를 확인할 수 없습니다.")
        ).forEach { (input, remedy) ->
            val actual = nextAction(input)
            assertEquals(expected.operation, actual.operation)
            assertEquals(expected.title, actual.title)
            assertEquals(expected.body, actual.body)
            assertEquals(NextActionCommand.CHECK_START, actual.operationAction)
            assertEquals(remedy, actual.transportRemedy)
        }
        val reconnected = nextAction(pending.copy(field = pending.field.copy(connected = true)))
        assertEquals(OperationState.START_PENDING, reconnected.operation)
        assertEquals(NextActionCommand.CHECK_START, reconnected.primary)
        assertNull(reconnected.transportRemedy)
        listOf(
            pending.copy(field = pending.field.copy(pendingStartRequestId = null, unconfirmedRunId = "run")),
            pending.copy(field = pending.field.copy(pendingStartRequestId = null))
        ).forEach { operation ->
            val original = nextAction(operation)
            listOf(operation.copy(credentialsRejected = true), operation.copy(permissionMissing = true)).forEach { remedy ->
                val actual = nextAction(remedy)
                assertEquals(original.operation, actual.operation)
                assertEquals(original.title, actual.title)
                assertEquals(original.body, actual.body)
            }
            assertEquals(NextActionCommand.CONNECTION_HELP, original.secondary)
        }
    }

    @Test
    fun `offline result and server report actions do not reorder the disconnected operation`() {
        val offline = idle.copy(field = idle.field.copy(connected = false))
        val result = nextAction(offline.copy(field = offline.field.copy(runAwaitingResultId = "ended")))
        assertEquals(OperationState.DISCONNECTED, result.operation)
        assertEquals(NextActionCommand.VIEW_RESULT, result.offlineAction)
        val report = nextAction(offline.copy(receiptVerified = true, ticketLinked = true))
        assertEquals(OperationState.DISCONNECTED, report.operation)
        assertEquals(NextActionCommand.REPORT, report.offlineAction)
        assertEquals(OperationState.IDLE, nextAction(idle.copy(receiptVerified = true)).operation)
        assertEquals(OperationState.IDLE, nextAction(idle.copy(receiptVerified = true,
            ticketLinked = true, completionReported = true)).operation)
        assertEquals("마지막 응답 5분 전 · 수집은 장치에서 계속될 수 있습니다.",
            nextAction(offline.copy(activeRunCount = 1, lastResponseLabel = "5분 전")).body)
    }
}
