package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.field.runFolderDisplayName
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
internal fun TicketDetailScreen(
    ticket: ServerTicket,
    role: String?,
    reportMessage: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    readiness: TicketPassiveReadiness = TicketPassiveReadiness(),
    taskLabel: String = "수집 작업 선택",
    onSelectTask: () -> Unit = {},
    onRemedy: () -> Unit = {},
    startOpener: FocusRequester = remember { FocusRequester() },
    ongoingRun: @Composable () -> Unit = {},
    mapCard: @Composable () -> Unit = { com.example.jetsoncontroller.ui.map.GeoTicketMapCard(ticket, readiness.deviceName) },
    syncLabel: String? = null
) {
    val availability = ticketStartAvailability(role, ticket)
    val hasMap = remember(ticket) { ticketHasMap(ticket) }
    val reason = availability.reason ?: readiness.blockedReason
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        GeoLargeTitleScaffold(ticket.title, Modifier.widthIn(max = 720.dp), onBack = onBack,
            subtitle = listOfNotNull(ticket.project?.takeIf(String::isNotBlank), readiness.deviceName).joinToString(" · ")) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp)) {
                    item { ongoingRun() }
                    item {
                        GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket))
                        syncLabel?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary) }
                    }
                    item {
                        GeoGroupedSection("정보") {
                            GeoPropertyList(listOf("담당", "마감", "GEO& Work 보고", "수집 작업"), Modifier.padding(horizontal = 16.dp)) {
                                GeoFactRow("담당", ticket.assignees.filter(String::isNotBlank).joinToString().ifBlank { "미지정" })
                                GeoFactRow("마감", ticketDueLabel(ticket.dueDate))
                                GeoPropertyRow("GEO& Work 보고", ticketReportWord(ticket).word, tone = ticketReportWord(ticket).tone)
                                GeoPropertyRow("수집 작업", taskLabel, onClick = onSelectTask)
                            }
                        }
                    }
                    if (hasMap) item(key = "zone-map") { GeoSection("지도 미리보기") { mapCard() } }
                    item {
                        GeoGroupedSection("준비 상태", trailingColumn = GeoRowTrailing.Status) {
                            val rows = ticketReadinessRows(readiness)
                            rows.forEachIndexed { index, row ->
                                GeoPreparationStatusRow(row.label, row.value, row.status, row.tone, divider = index < rows.lastIndex)
                            }
                        }
                    }
                    item {
                        val note = ticketConflictCopy(ticket) ?: reportMessage?.let { "GEO& Work가 보고를 받지 않았습니다. 잠시 후 다시 보고하세요." }
                        note?.let { GeoInlineNote(it, modifier = Modifier.padding(top = 16.dp)) }
                        GeoReadinessLine("${readiness.deviceName} · ${readiness.connection} · ${reason ?: "시작할 수 있습니다."}",
                            if (reason == null && readiness.controlAvailable) Tone.SUCCESS else Tone.WARNING)
                        readiness.remedyLabel?.let { GeoButton(it, onRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start) }
                    }
                }
                GeoFixedActionBar(GeoButtonAction("수집 시작", onStart,
                    enabled = availability.enabled && readiness.controlAvailable && reason == null, disabledReason = reason),
                    modifier = Modifier.focusRequester(startOpener))
            }
        }
    }
}

/** The route adapter keeps the task target and preparation callback unchanged. */
@Composable
internal fun WorkTaskDetailScreen(
    pipeline: ManagedPipeline, readiness: TicketPassiveReadiness, onBack: () -> Unit,
    onPrepare: () -> Unit, ongoingRun: @Composable () -> Unit = {},
    lastRun: TaskRun? = null, onOpenFiles: (() -> Unit)? = null,
    mapCard: (@Composable () -> Unit)? = null
) {
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        GeoLargeTitleScaffold(pipeline.label, Modifier.widthIn(max = 720.dp), subtitle = readiness.deviceName, onBack = onBack,
            primaryAction = GeoButtonAction("이 작업으로 수집 준비", onPrepare,
                enabled = readiness.controlAvailable && readiness.blockedReason == null, disabledReason = readiness.blockedReason)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp)) {
                item { ongoingRun() }
                item {
                    GeoGroupedSection("정보", first = true) {
                        val rows = taskDetailRows(pipeline, readiness.controlAvailable, lastRun = lastRun)
                        GeoPropertyList(rows.map { it.label }, Modifier.padding(horizontal = 16.dp)) {
                            rows.forEach { row ->
                                if (row.tone != null) WorkBadgeFact(row.label, row.value, row.tone)
                                else if (row.label == "결과 저장 폴더" && onOpenFiles != null) GeoPropertyRow(row.label,
                                    value = runFolderDisplayName(row.value, lastRun?.startedAt, lastRun?.runId, lastRun?.output?.manifest?.runId) ?: "미확인",
                                    onClick = onOpenFiles, enabled = readiness.controlAvailable,
                                    disabledReason = "장치에 연결한 뒤 저장 폴더를 볼 수 있습니다.")
                                else GeoFactRow(row.label, if (row.label == "결과 저장 폴더")
                                    runFolderDisplayName(row.value, lastRun?.startedAt, lastRun?.runId, lastRun?.output?.manifest?.runId) ?: "미확인" else row.value)
                            }
                        }
                    }
                }
                mapCard?.let { preview -> item { GeoSection("지도 미리보기") { preview() } } }
                item {
                    GeoGroupedSection("준비 상태", trailingColumn = GeoRowTrailing.Status) {
                        val rows = ticketReadinessRows(readiness)
                        rows.forEachIndexed { index, row ->
                            GeoPreparationStatusRow(row.label, row.value, row.status, row.tone, divider = index < rows.lastIndex)
                        }
                    }
                    GeoReadinessLine("${readiness.deviceName} · ${readiness.connection} · ${readiness.blockedReason ?: "시작할 수 있습니다."}",
                        if (readiness.blockedReason == null && readiness.controlAvailable) Tone.SUCCESS else Tone.WARNING)
                }
            }
        }
    }
}

@Composable
private fun WorkBadgeFact(label: String, value: String, tone: Tone) {
    GeoPropertyRow(label, value, tone = tone)
}

@Composable
internal fun WorkMissingScreen(ticket: Boolean, onBack: () -> Unit, onWork: () -> Unit) {
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        GeoLargeTitleScaffold(if (ticket) "수집 구간" else "작업", Modifier.widthIn(max = 720.dp), onBack = onBack,
            primaryAction = GeoButtonAction(if (ticket) "목록으로" else "작업으로", onWork)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = gutter, vertical = 24.dp)) {
                item { GeoStateBlock(GeoStateContent(GeoStateKind.MISSING,
                    if (ticket) "수집 구간을 찾을 수 없습니다" else "작업 정보를 찾을 수 없습니다",
                    if (ticket) "GEO& Work에서 지워졌거나 다른 호기로 옮겨졌을 수 있습니다." else "")) }
            }
        }
    }
}
