package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketStatus
import com.example.jetsoncontroller.ui.components.geo.GeoButtonAction
import com.example.jetsoncontroller.ui.components.geo.GeoButtonStyle
import com.example.jetsoncontroller.ui.model.NextAction
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.model.TransportRemedy
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import org.junit.Assert.*
import org.junit.Test

class R8WorkHomeActionsTest {
    private val calls = mutableListOf<String>()
    private fun state() = buildTasksHomeUiState(TasksHomeInput("장치", true,
        SurveyRunUiState(runStateConfirmed = true), emptyList(), emptyList(), emptyList(), false))
        .copy(focus = null)

    private fun actions(state: TasksHomeUiState, stop: GeoButtonAction? = null, reportEnabled: Boolean = true) =
        workHomeActions(state, { calls += "ticket:$it" }, { calls += "start" }, { calls += "manual" },
            { calls += "run" }, { calls += it.name }, { calls += "server" }, "다시 보고", reportEnabled, stop)

    @Test fun `collecting keeps view primary and the bound stop confirmation secondary`() {
        val state = state().copy(focus = NextAction(OperationState.COLLECTING, "수집 중", null, NextActionCommand.VIEW_RUN))
        val stop = GeoButtonAction("수집 종료", { calls += "stop:device:pipeline:run" })
        val actions = actions(state, stop)
        assertEquals("수집 화면 보기", actions.primary?.label)
        assertEquals(GeoButtonStyle.Primary, actions.primary?.style)
        assertEquals("수집 종료", actions.secondary?.label)
        assertEquals(GeoButtonStyle.DangerOutline, actions.secondary?.style)
        assertSame(stop.onClick, actions.secondary?.onClick)
        actions.primary!!.onClick()
        assertEquals(listOf("run"), calls)
        actions.secondary!!.onClick()
        assertEquals(listOf("run", "stop:device:pipeline:run"), calls)
    }

    @Test fun `unavailable stop retains its reason without disabling view`() {
        val state = state().copy(focus = NextAction(OperationState.COLLECTING, "수집 중", null, NextActionCommand.VIEW_RUN))
        val stop = GeoButtonAction("수집 종료", {}, enabled = false, disabledReason = "수집 상태를 다시 확인하세요.")
        val actions = actions(state, stop)
        assertTrue(actions.primary!!.enabled)
        assertFalse(actions.secondary!!.enabled)
        assertEquals(stop.disabledReason, actions.secondary!!.disabledReason)
        assertNull(this.actions(state).secondary)
    }

    @Test fun `pending start keeps its connection remedies and never substitutes stop`() {
        val state = state().copy(focus = NextAction(OperationState.START_PENDING, "시작 결과 확인", null,
            NextActionCommand.CHECK_START, TransportRemedy(NextActionCommand.RECONNECT, NextActionCommand.CONNECTION_HELP)))
        val actions = actions(state, GeoButtonAction("수집 종료", { fail("Must keep the start result gate") }))
        actions.primary!!.onClick()
        actions.secondary!!.onClick()
        assertEquals(listOf("RECONNECT", "CONNECTION_HELP"), calls)
        assertEquals(GeoButtonStyle.Secondary, actions.secondary!!.style)
    }

    @Test fun `report keeps the original guard label and command`() {
        val state = state().copy(focus = NextAction(OperationState.NEEDS_REPORT, "완료 보고", null, NextActionCommand.REPORT))
        val disabled = actions(state, reportEnabled = false)
        assertEquals("다시 보고", disabled.primary?.label)
        assertFalse(disabled.primary!!.enabled)
        assertNotNull(disabled.primary!!.disabledReason)
        assertNull(disabled.secondary)
        actions(state).primary!!.onClick()
        assertEquals(listOf("REPORT"), calls)
    }

    @Test fun `featured preparation uses the actual row id even for duplicate titles`() {
        val ticket = ServerTicket("row/first", "같은 구간 이름")
        val other = ticket.copy(rowId = "row/second")
        val state = state().copy(ticketSection = ServerTicketUiState(tickets = listOf(ticket, other)))
        val featured = requireNotNull(workHomeFeaturedTicket(state))
        actions(state).primary!!.onClick()
        assertEquals(listOf("ticket:${featured.rowId}"), calls)
        assertEquals(listOf(other), workHomeVisibleQueue(state.ticketSection!!, featured.rowId).open)
        assertNull(actions(state).secondary)
        assertNull(workHomeFeaturedTicket(state.copy(focus = NextAction(OperationState.RUN_UNCONFIRMED,
            "수집 상태를 확인하지 못했습니다", null, NextActionCommand.CHECK_STATE))))
    }

    @Test fun `reported focus omits only its matching completed row and preserves all other rows`() {
        val focus = ServerTicket("focus", "구간", status = ServerTicketStatus("완료", "COMPLETED"))
        val other = focus.copy(rowId = "other")
        val open = focus.copy(rowId = "open", status = ServerTicketStatus("할 일", "OPEN"))
        val visible = workHomeVisibleQueue(ServerTicketUiState(tickets = listOf(focus, other, open)), "focus")
        assertEquals(listOf(other), visible.completed)
        assertEquals(listOf(open), visible.open)
    }

    @Test fun `missing server loading and manual preparation keep distinct actions`() {
        actions(state()).primary!!.onClick()
        assertEquals(listOf("server"), calls)
        calls.clear()
        val loading = state().copy(ticketSection = ServerTicketUiState(isLoading = true))
        assertNull(actions(loading).primary)
        val empty = loading.copy(ticketSection = ServerTicketUiState())
        actions(empty).primary!!.onClick()
        assertEquals(listOf("manual"), calls)
        calls.clear()
        val chosen = empty.copy(pinned = empty.pinned.copy(scopeLabel = "프로젝트 · 구간", changeEnabled = true))
        actions(chosen).primary!!.onClick()
        assertEquals(listOf("start"), calls)
        assertFalse(actions(chosen.copy(pinned = chosen.pinned.copy(changeEnabled = false))).primary!!.enabled)
    }
}
