package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.ui.field.runFolderDisplayName
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
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
    mapCard: @Composable () -> Unit = { com.example.jetsoncontroller.ui.map.GeoTicketMapCard(ticket, readiness.deviceName) }
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val gutter = LocalGeoWindow.current.gutterDp.dp
    val availability = ticketStartAvailability(role, ticket)
    val reason = availability.reason ?: readiness.blockedReason
    Column(Modifier.fillMaxSize().background(c.surface)) {
        WorkDetailHeader("수집 구간", onBack)
        ongoingRun()
        GeoActionArea(visibilityKey = "${ticket.rowId}:${readiness.deviceName}", action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GeoReadinessLine("${readiness.deviceName} · ${readiness.connection} · ${reason ?: "시작할 수 있습니다."}",
                    if (reason == null && readiness.controlAvailable) Tone.SUCCESS else Tone.WARNING)
                GeoButton("수집 시작", onStart, Modifier.fillMaxWidth().focusRequester(startOpener),
                    enabled = availability.enabled && readiness.controlAvailable && reason == null, actionArea = true)
                readiness.remedyLabel?.let { GeoButton(it, onRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start) }
            }
        }) { inlineAction ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 40.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ticket.title, style = type.boardTitle, color = c.text, modifier = Modifier.semantics { heading() })
                        GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket))
                        GeoBadge(readiness.deviceName, Tone.INFO, variant = GeoBadgeVariant.TAG)
                        Text(listOfNotNull(ticket.project?.takeIf(String::isNotBlank),
                            "마감 ${ticketDueLabel(ticket.dueDate)}").joinToString(" · "), style = type.caption, color = c.textSecondary)
                    }
                }
                item {
                    GeoSection("정보", first = true, modifier = Modifier.padding(top = 24.dp)) {
                      GeoPropertyList(listOf("담당", "마감", "GEO& Work 보고", "수집 작업")) {
                        GeoFactRow("담당", ticket.assignees.orEmpty().filter(String::isNotBlank).joinToString().ifBlank { "미지정" })
                        GeoFactRow("마감", ticketDueLabel(ticket.dueDate))
                        GeoPropertyRow("GEO& Work 보고", ticketReportWord(ticket).word, tone = ticketReportWord(ticket).tone)
                        GeoPropertyRow("수집 작업", taskLabel, onClick = onSelectTask)
                      }
                    }
                }
                item { Box(Modifier.padding(top = 24.dp)) { mapCard() } }
                item {
                    GeoSection("준비 상태") {
                      GeoPropertyList(listOf("연결", "저장 공간", "위치 품질", "카메라", "보정 정보 중계")) {
                        GeoFactRow("연결", readiness.connection)
                        GeoFactRow("저장 공간", readiness.storage)
                        GeoFactRow("위치 품질", readiness.position)
                        GeoFactRow("카메라", readiness.camera)
                        readiness.relay?.let { GeoFactRow("보정 정보 중계", it) }
                      }
                    }
                }
                item {
                    val note = ticketConflictCopy(ticket) ?: reportMessage?.let {
                        "GEO& Work가 보고를 받지 않았습니다. 잠시 후 다시 보고하세요."
                    }
                    note?.let { GeoInlineNote(it, modifier = Modifier.padding(top = 16.dp)) }
                }
                item { inlineAction() }
            }
        }
    }
}

@Composable
internal fun WorkDetailHeader(title: String, onBack: () -> Unit) {
    val c = LocalGeoColors.current
    Row(Modifier.fillMaxWidth().background(c.topBar).heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
        Text(title, color = c.onTopBar, style = LocalGeoType.current.title, modifier = Modifier.weight(1f))
    }
}

/** The route adapter replaces the legacy task screen without changing its public signature. */
@Composable
internal fun WorkTaskDetailScreen(
    pipeline: ManagedPipeline, readiness: TicketPassiveReadiness, onBack: () -> Unit,
    onPrepare: () -> Unit, ongoingRun: @Composable () -> Unit = {},
    lastRun: TaskRun? = null, onOpenFiles: (() -> Unit)? = null
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val gutter = LocalGeoWindow.current.gutterDp.dp
    Column(Modifier.fillMaxSize().background(c.surface)) {
        WorkDetailHeader("작업", onBack)
        ongoingRun()
        GeoActionArea(pipeline.id, action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GeoReadinessLine("${readiness.deviceName} · ${readiness.connection} · ${readiness.blockedReason ?: "시작할 수 있습니다."}",
                    if (readiness.blockedReason == null && readiness.controlAvailable) Tone.SUCCESS else Tone.WARNING)
                GeoButton("이 작업으로 수집 준비", onPrepare, Modifier.fillMaxWidth(),
                    enabled = readiness.controlAvailable && readiness.blockedReason == null, actionArea = true)
            }
        }) { inlineAction ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 40.dp)) {
                item {
                    Text(pipeline.label, style = type.boardTitle, color = c.text, modifier = Modifier.semantics { heading() })
                    GeoBadge(readiness.deviceName, Tone.INFO, variant = GeoBadgeVariant.TAG)
                }
                item {
                    GeoSection("정보", first = true, modifier = Modifier.padding(top = 24.dp)) {
                      val rows = taskDetailRows(pipeline, readiness.controlAvailable, lastRun = lastRun)
                      GeoPropertyList(rows.map { it.label }) {
                        rows.forEach { row ->
                            if (row.tone != null) WorkBadgeFact(row.label, row.value, row.tone)
                            else if (row.label == "결과 저장 폴더" && onOpenFiles != null) {
                                GeoPropertyRow(row.label,
                                    value = runFolderDisplayName(row.value, lastRun?.startedAt, lastRun?.runId, lastRun?.output?.manifest?.runId) ?: "미확인",
                                    onClick = onOpenFiles,
                                    enabled = readiness.controlAvailable,
                                    disabledReason = "장치에 연결한 뒤 저장 폴더를 볼 수 있습니다.")
                            } else GeoFactRow(row.label, if (row.label == "결과 저장 폴더")
                                runFolderDisplayName(row.value, lastRun?.startedAt, lastRun?.runId, lastRun?.output?.manifest?.runId) ?: "미확인"
                                else row.value)
                        }
                      }
                    }
                }
                item { inlineAction() }
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
    val c = LocalGeoColors.current
    Column(Modifier.fillMaxSize().background(c.surface)) {
        WorkDetailHeader(if (ticket) "수집 구간" else "작업", onBack)
        GeoStateBlock(GeoStateContent(GeoStateKind.MISSING,
            if (ticket) "수집 구간을 찾을 수 없습니다" else "작업 정보를 찾을 수 없습니다",
            if (ticket) "GEO& Work에서 지워졌거나 다른 호기로 옮겨졌을 수 있습니다." else "",
            if (ticket) "목록으로" else "작업으로"), Modifier.padding(horizontal = LocalGeoWindow.current.gutterDp.dp),
            fullScreen = true, onAction = onWork)
    }
}
