package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketReportState
import com.example.jetsoncontroller.data.server.ServerTicketState
import com.example.jetsoncontroller.ui.components.StatusTone

internal data class TicketActionAvailability(
    val enabled: Boolean,
    val reason: String? = null
)

internal data class TicketCompletionPresentation(
    val showButton: Boolean,
    val enabled: Boolean,
    val message: String? = null
)

internal fun defaultTicketUnit(deviceUnit: String?, @Suppress("UNUSED_PARAMETER") units: List<String>): String? =
    deviceUnit?.takeIf(String::isNotBlank)

internal fun filterTicketsByUnit(tickets: List<ServerTicket>, selectedUnit: String?): List<ServerTicket> =
    if (selectedUnit == null) tickets else tickets.filter { it.unit == selectedUnit }

internal fun ticketStartAvailability(role: String?, ticket: ServerTicket): TicketActionAvailability = when {
    role.equals("VIEWER", ignoreCase = true) -> TicketActionAvailability(
        false, "수집 시작은 작업자 권한이 필요합니다."
    )
    (!role.equals("OPERATOR", ignoreCase = true) && !role.equals("ADMIN", ignoreCase = true)) -> TicketActionAvailability(
        false, "작업자 권한을 확인할 수 없습니다."
    )
    ticketConflictCopy(ticket) != null -> TicketActionAvailability(false, ticketConflictCopy(ticket))
    ticket.status?.isBlocked == true -> TicketActionAvailability(false, TICKET_BLOCKED_COPY)
    ticket.status?.normalizedCategory != ServerTicketState.OPEN -> TicketActionAvailability(
        false, "열린 티켓에서만 수집을 시작할 수 있습니다."
    )
    else -> TicketActionAvailability(true)
}

internal fun ticketCompletionPresentation(
    attached: Boolean,
    serverReceiptConfirmed: Boolean,
    role: String?,
    conflicted: Boolean = false,
    startRejected: Boolean = false,
    startRejectedMessage: String? = null
): TicketCompletionPresentation {
    if (!attached) return TicketCompletionPresentation(showButton = false, enabled = false)
    if (!serverReceiptConfirmed) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = "서버 수신 확인 뒤에 완료를 보고할 수 있습니다. 티켓은 '진행 중'으로 둡니다."
    )
    if (conflicted) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요."
    )
    if (startRejected) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = startRejectedMessage ?: "티켓에서 수집을 시작하지 못했습니다. GEO& Work에서 확인해 주세요."
    )
    if (role.equals("VIEWER", ignoreCase = true)) return TicketCompletionPresentation(
        showButton = true,
        enabled = false,
        message = "완료 보고는 작업자 권한이 필요합니다."
    )
    if (!role.equals("OPERATOR", ignoreCase = true) && !role.equals("ADMIN", ignoreCase = true)) return TicketCompletionPresentation(
        showButton = true,
        enabled = false,
        message = "작업자 권한을 확인할 수 없습니다."
    )
    return TicketCompletionPresentation(showButton = true, enabled = true)
}

internal fun ticketConflictCopy(ticket: ServerTicket): String? =
    "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요.".takeIf {
        ticket.report?.normalizedState == ServerTicketReportState.CONFLICT &&
            ticket.report.code == "HUMAN_CHANGED"
    }

internal fun ticketReportStateLabel(ticket: ServerTicket): String {
    ticketConflictCopy(ticket)?.let { return it }
    val report = ticket.report ?: return "보고 상태 확인 중"
    val state = when (report.normalizedState) {
        ServerTicketReportState.PENDING -> "대기 중"
        ServerTicketReportState.SENT -> "전송 완료"
        ServerTicketReportState.DEDUP -> "중복 확인"
        ServerTicketReportState.CONFLICT -> "충돌"
        ServerTicketReportState.ERROR -> "오류"
        ServerTicketReportState.UNKNOWN -> report.state ?: "확인 중"
    }
    return listOfNotNull("보고 $state", report.code?.takeIf(String::isNotBlank)).joinToString(" · ")
}

internal fun ticketStatusLabel(ticket: ServerTicket): String =
    ticket.status?.name?.takeIf(String::isNotBlank) ?: when (ticket.status?.normalizedCategory) {
        ServerTicketState.OPEN -> "할 일"
        ServerTicketState.IN_PROGRESS -> "진행 중"
        ServerTicketState.COMPLETED -> "완료"
        else -> "상태 확인 중"
    }

/** A person marked the ticket 막힘; the app never starts it or moves it on their behalf. */
internal const val TICKET_BLOCKED_COPY = "막힘 상태입니다. GEO& Work에서 상태를 바꾼 뒤 시작하세요."

internal fun ticketStatusTone(ticket: ServerTicket): StatusTone = if (ticket.status?.isBlocked == true) {
    StatusTone.WARNING
} else when (ticket.status?.normalizedCategory) {
    ServerTicketState.OPEN -> StatusTone.INFO
    ServerTicketState.IN_PROGRESS -> StatusTone.PENDING
    ServerTicketState.COMPLETED -> StatusTone.SUCCESS
    else -> StatusTone.UNKNOWN
}
