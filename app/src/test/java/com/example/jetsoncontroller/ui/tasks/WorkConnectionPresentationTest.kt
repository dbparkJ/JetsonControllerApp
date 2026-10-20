package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import org.junit.Assert.*
import org.junit.Test

class WorkConnectionPresentationTest {
    private fun input() = TasksHomeInput("GEO-X-1", true,
        SurveyRunUiState(unconfirmedRunId = "run"), emptyList(), emptyList(), emptyList(), false)

    @Test fun `an unconfirmed run on a silent transport offers reconnect and connection methods`() {
        val state = buildTasksHomeUiState(input().copy(
            reachable = workDeviceReachable(true, StatusFreshness.STALE)))
        assertEquals(OperationState.RUN_UNCONFIRMED, state.focus!!.operation)
        assertEquals("GEO-X-1 수집 상태를 확인하지 못했습니다", state.focus.title)
        assertEquals(NextActionCommand.RECONNECT, state.focus.primary)
        assertEquals(NextActionCommand.CONNECTION_HELP, state.focus.secondary)
        assertFalse(state.pinned.startEnabled)
        assertEquals(Tone.WARNING, state.connectionTone)
    }

    @Test fun `reachable unconfirmed state keeps the explicit status check`() {
        val focus = workNextAction(input())
        assertEquals(NextActionCommand.CHECK_STATE, focus.primary)
        assertNull(focus.secondary)
    }

    @Test fun `a transport without a first response can still be checked`() {
        assertTrue(workDeviceReachable(true, StatusFreshness.CURRENT))
        assertTrue(workDeviceReachable(true, StatusFreshness.UNKNOWN))
        assertFalse(workDeviceReachable(true, StatusFreshness.STALE))
        assertFalse(workDeviceReachable(false, StatusFreshness.CURRENT))
    }

    @Test fun `offline chrome keeps the warning when the device strip is hidden by focus`() {
        val state = buildTasksHomeUiState(input().copy(connected = false,
            survey = SurveyRunUiState(runStateConfirmed = true), connectionTone = Tone.UNKNOWN))
        assertTrue(state.deviceStrip!!.hiddenByFocus)
        assertEquals(Tone.WARNING, state.connectionTone)
    }

    @Test fun `both roots use connection vocabulary tones with authentication and permission priority`() {
        assertEquals(Tone.SUCCESS, workRootConnectionTone(Tone.SUCCESS, true))
        assertEquals(Tone.INFO, workRootConnectionTone(Tone.INFO, true))
        assertEquals(Tone.WARNING, workRootConnectionTone(Tone.SUCCESS, false))
        assertEquals(Tone.WARNING, workRootConnectionTone(Tone.SUCCESS, true, basicConnection = true))
        assertEquals(Tone.PENDING, workRootConnectionTone(Tone.UNKNOWN, false, checking = true))
        assertEquals(Tone.WARNING, workRootConnectionTone(Tone.UNKNOWN, false, checking = true, permissionMissing = true))
        assertEquals(Tone.ERROR, workRootConnectionTone(Tone.UNKNOWN, false, checking = true,
            permissionMissing = true, credentialsRejected = true))
    }

    @Test fun `readiness describes the transport rather than the connected badge`() {
        assertEquals("같은 네트워크", workConnectionLabel(true, TransportType.LAN))
        assertEquals("직접 연결", workConnectionLabel(true, TransportType.WIFI_DIRECT))
        assertEquals("기본 연결", workConnectionLabel(true, TransportType.BLE))
        assertEquals("오프라인", workConnectionLabel(false, TransportType.LAN))
        assertEquals("미확인", workConnectionLabel(true, null))
    }
}
