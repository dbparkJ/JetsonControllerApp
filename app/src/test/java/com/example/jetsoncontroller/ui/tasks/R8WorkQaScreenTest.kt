package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketStatus
import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import com.example.jetsoncontroller.ui.model.NextAction
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import org.junit.Assert.*
import org.junit.Test

class R8WorkQaScreenTest {
    private fun state(tickets: ServerTicketUiState) = buildTasksHomeUiState(TasksHomeInput(
        "GEO-X-1", true, SurveyRunUiState(runStateConfirmed = true), emptyList(), emptyList(), emptyList(), false
    )).copy(focus = null, ticketSection = tickets)

    @Test fun todayUsesAnExplicitCollectionStateWithoutChangingTheQueueStatus() {
        val open = ServerTicket("row", "구간", status = ServerTicketStatus("할 일", "OPEN"))
        assertEquals("수집 전", ticketTodayStatusLabel(open))
        assertEquals("할 일", ticketStatusLabel(open))
        listOf("IN_PROGRESS", "COMPLETED", "FUTURE").forEach { category ->
            val ticket = open.copy(status = ServerTicketStatus("상태", category))
            assertEquals(ticketStatusLabel(ticket), ticketTodayStatusLabel(ticket))
        }
    }

    @Test fun aFullErrorOwnsOneRecoveryWhileCachedRowsKeepTheirWarning() {
        val error = ServerTicketUiState(quietError = "서버에 연결할 수 없습니다.")
        assertTrue(workHomeShowsTicketError(state(error)))
        assertFalse(workHomeShowsTicketError(state(error.copy(isLoading = true))))
        assertFalse(workHomeShowsTicketError(state(error.copy(tickets = listOf(ServerTicket("row", "구간"))))))
        assertFalse(workHomeShowsTicketError(state(error).copy(focus = NextAction(
            OperationState.COLLECTING, "수집 중", null, NextActionCommand.VIEW_RUN))))
        assertEquals("서버 연결을 확인하세요.", ticketLoadErrorDescription(
            "수집 구간을 불러오지 못했습니다. 서버 연결을 확인하세요."))
        assertEquals("서버 연결 상태를 확인한 뒤 다시 시도하세요.",
            ticketLoadErrorDescription("수집 구간을 불러오지 못했습니다."))
        assertEquals("서버 연결 상태를 확인한 뒤 다시 시도하세요.",
            ticketLoadErrorDescription("수집 구간을 불러오지 못했습니다"))
        assertEquals("서버에서 최신 목록을 받지 못했습니다. 서버 연결 상태를 확인한 뒤 다시 시도하세요.",
            ticketLoadErrorDescription("티켓 목록을 새로고침하지 못했습니다. 저장된 목록을 표시합니다."))
        assertEquals("연동 설정이 없습니다.", ticketLoadErrorDescription("연동 설정이 없습니다."))
    }

    @Test fun homeDelegatesHeaderReflowDeadlineAndDescriptionStatusToSharedComponents() {
        val home = r8UiSource("tasks/TasksHomeScreen.kt")
        val actions = home.substringAfter("secondaryAction = actions.secondary.takeUnless").substringBefore("PullToRefreshBox")
        assertTrue(actions.contains("WorkDeviceSwitcher("))
        assertTrue(actions.contains("GeoIconButton(GeoIcons.bell"))
        assertFalse(actions.contains("Column("))
        val card = home.substringAfter("private fun WorkCurrentCard").substringBefore("private fun WorkTicketsSection")
        assertTrue(card.contains("descriptionStatus = ticketTodayStatusLabel(current)"))
        assertTrue(card.contains("deadline = \"마감 ${'$'}{ticketDueLabel(current.dueDate)}\""))
        assertFalse(card.contains("status ="))
        assertTrue(card.contains("actionLabel = \"다시 확인\""))
        assertTrue(card.contains("onAction = onRefresh"))
    }

    @Test fun pullRefreshHasItsOwnIntentAndNeverMirrorsInitialLoading() {
        val home = r8UiSource("tasks/TasksHomeScreen.kt")
        assertTrue(home.contains("isRefreshing = pullRefreshing && tickets?.isLoading == true"))
        assertTrue(home.contains("onRefresh = refreshByPull"))
        assertTrue(home.contains("if (tickets?.isLoading != true) pullRefreshing = false"))
        assertTrue(home.contains("if (tickets != null && !tickets.isLoading) pullRefreshing = true"))
    }

    @Test fun manualChoiceAndEmptyStateUseSectionActionAndFilterSpacing() {
        val home = r8UiSource("tasks/TasksHomeScreen.kt")
        assertTrue(home.contains("GeoSection(\"장치 작업\""))
        val queue = home.substringAfter("private fun WorkTicketsSection").substringBefore("internal fun workHomeShowsTicketError")
        assertTrue(queue.contains("actionLabel = \"구간 직접 고르기\".takeIf { showManual }, onAction = onManual"))
        assertFalse(queue.contains("GeoButton(\"구간 직접 고르기\""))
        assertTrue(queue.substringAfter("GeoFilters(").substringBefore("val queue =").contains("Spacer(Modifier.height(16.dp))"))
        assertTrue(queue.contains("takeUnless { workHomeShowsTicketError(state) }"))
    }

    @Test fun detailStatusAndReadinessUseSharedHeaderAndFooterSlots() {
        val detail = r8UiSource("tasks/TicketDetailScreen.kt")
        assertEquals(2, Regex("subtitleStatus = GeoStatusSpec").findAll(detail).count())
        assertEquals(2, Regex("note = \\{").findAll(detail).count())
        assertFalse(detail.contains("GeoReadinessLine("))
        assertFalse(detail.contains("GeoBadge("))
        assertTrue(detail.contains("작업 홈에서 장치와 작업 목록을 다시 확인하세요."))
        assertTrue(detail.contains("GeoButtonAction(\"시작 전 점검\", onStart"))
    }
}
