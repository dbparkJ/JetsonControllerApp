package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TasksHomeScreen(
    state: TasksHomeUiState,
    onSelectDevice: () -> Unit,
    onStartSurvey: () -> Unit,
    onChangeSection: () -> Unit,
    onOpenActiveRun: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTicket: (String) -> Unit = {},
    onSelectTicketUnit: (String?) -> Unit = {},
    onOpenDevice: () -> Unit = onSelectDevice,
    onFocusAction: (NextActionCommand) -> Unit = {},
    onRefresh: () -> Unit = {},
    onServerConnection: () -> Unit = {},
    onAlerts: () -> Unit = {},
    unreadCount: Int = 0,
    now: Instant = Instant.now(),
    deviceOpener: FocusRequester = remember { FocusRequester() },
    focusReportLabel: String = "완료 보고",
    focusReportMessage: String? = null,
    focusReportEnabled: Boolean = true
) {
    val c = LocalGeoColors.current
    val gutter = LocalGeoWindow.current.gutterDp.dp
    Column(Modifier.fillMaxSize().background(c.surface)) {
        WorkRootHeader(state, onSelectDevice, onAlerts, unreadCount, deviceOpener)
        PullToRefreshBox(isRefreshing = state.ticketSection?.isLoading == true, onRefresh = onRefresh,
            modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("새로 고침") { onRefresh(); true })
            }, contentPadding = PaddingValues(horizontal = gutter, vertical = 16.dp)) {
                item(key = "device") { state.deviceStrip?.let { GeoDeviceStrip(it, onOpenDevice) } }
                val focus = state.focus
                if (focus?.title != null && focus.primary != null) {
                    item(key = "focus") {
                        val tone = when (focus.operation) {
                            OperationState.START_PENDING, OperationState.RUN_UNCONFIRMED -> Tone.PENDING
                            OperationState.DISCONNECTED, OperationState.MULTIPLE_RUNS, OperationState.BASIC_CONNECTION -> Tone.WARNING
                            else -> Tone.INFO
                        }
                        GeoFocusBlock(GeoFocusBlockState(
                            focus.title, listOfNotNull(
                                if (focus.primary == NextActionCommand.REPORT) focusReportMessage ?: focus.body else focus.body?.takeIf(String::isNotBlank),
                                focus.transportRemedy?.detail).joinToString(" · "), tone,
                            GeoOverlayAction(if (focus.primary == NextActionCommand.REPORT) focusReportLabel else focus.primary!!.label,
                                { onFocusAction(focus.primary!!) },
                                enabled = focus.primary != NextActionCommand.REPORT || focusReportEnabled,
                                disabledReason = focusReportMessage),
                            focus.secondary?.let { command -> GeoOverlayAction(command.label, { onFocusAction(command) }) }
                        ), Modifier.padding(top = 16.dp))
                    }
                }
                item(key = "queue") {
                    WorkTicketsSection(state, onOpenTicket, onSelectTicketUnit, onChangeSection, onRefresh, onServerConnection, now)
                }
                item(key = "recent") {
                    GeoSection("최근 수집", actionLabel = if (state.recent.isEmpty()) null else "전체 기록", onAction = onOpenHistory) {
                        if (state.recent.isEmpty()) state.deviceListsNote?.let { DeviceListsNote(it) }
                            ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "완료한 수집이 없습니다", "수집을 마치면 여기에 남습니다."))
                        state.recent.forEach { row ->
                            Column(Modifier.clickable { onOpenRun(row.runId) }.semantics(mergeDescendants = true) {}) {
                                GeoRow(row.title, { onOpenRun(row.runId) }, meta = row.meta)
                                FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (row.cells + listOfNotNull(row.report?.let {
                                        com.example.jetsoncontroller.ui.field.RunReceiptCell("GEO& Work 보고", it.word, it.tone)
                                    })).forEach { cell ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(cell.title, style = LocalGeoType.current.caption, color = c.textSecondary)
                                            Text("●", style = LocalGeoType.current.count, color = when (cell.tone) {
                                                Tone.SUCCESS -> c.success
                                                Tone.WARNING -> c.warning
                                                Tone.ERROR -> c.danger
                                                Tone.INFO -> c.primary
                                                Tone.PENDING -> c.pending
                                                else -> c.neutral
                                            })
                                            Text(cell.value, style = LocalGeoType.current.badge, color = c.text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "tasks") {
                    GeoSection("장치 작업") {
                        if (state.otherTasks.isEmpty()) state.deviceListsNote?.let { DeviceListsNote(it) }
                            ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "이 장치에 등록된 작업이 없습니다", "작업 등록은 관리자에게 요청하세요."))
                        state.otherTasks.forEach { task ->
                            GeoRow(task.title, { onOpenTask(task.pipelineId) }, meta = task.caption,
                                badge = { GeoBadge(task.stateLabel, workTaskTone(task.stateLabel)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkTicketsSection(
    state: TasksHomeUiState, onOpen: (String) -> Unit, onUnit: (String?) -> Unit,
    onManual: () -> Unit, onRefresh: () -> Unit, onServer: () -> Unit, now: Instant
) {
    val tickets = state.ticketSection
    var picker by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable(tickets?.profileId, tickets?.deviceId, tickets?.selectedUnit) { mutableStateOf(false) }
    val opener = remember { FocusRequester() }
    val deviceUnit = tickets?.deviceUnit
    if (picker) GeoSheet("수집 구간 보기", { picker = false }, openerFocusRequester = opener) {
        GeoRadioRow(state.deviceName, tickets?.selectedUnit != null, { onUnit(deviceUnit); picker = false })
        GeoRadioRow("전체", tickets?.selectedUnit == null, { onUnit(null); picker = false })
    }
    GeoSection("수집 구간", first = state.deviceStrip == null && state.focus == null,
        actionLabel = "구간 직접 고르기".takeIf { tickets != null }, onAction = onManual) {
        if (tickets == null) {
            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "서버 연결 정보가 없습니다",
                "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다.", "서버 연결"), onAction = onServer)
        } else {
            GeoSegmented("수집 구간 보기", listOf(state.deviceName, "전체"),
                if (tickets.selectedUnit == null) 1 else 0,
                { onUnit(if (it == 0) deviceUnit else null) }, { picker = true },
                Modifier.focusRequester(opener))
            ticketSyncLabel(tickets, now)?.let { sync ->
                Text(sync, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (ticketRefreshVisible(tickets, now)) GeoButton("다시 확인", onRefresh,
                style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !tickets.isLoading)
            val queue = workTicketQueue(tickets.tickets, tickets.selectedUnit)
            when {
                tickets.isLoading && tickets.tickets.isEmpty() -> GeoSkeleton(true, Modifier.padding(top = 12.dp))
                queue.open.isEmpty() && queue.completed.isEmpty() -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                    "이 호기에 배정된 수집 구간이 없습니다", "GEO& Work에서 배정되면 여기에 표시됩니다."), Modifier.padding(top = 16.dp))
                else -> {
                    queue.open.forEach { ticket ->
                        GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket),
                            badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                    }
                    if (queue.completed.isNotEmpty()) {
                        GeoRow("완료 ${queue.completed.size}개", { completed = !completed },
                            modifier = Modifier.semantics { stateDescription = if (completed) "펼침" else "접힘" })
                        if (completed) queue.completed.forEach { ticket ->
                            GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket),
                                badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkRootHeader(state: TasksHomeUiState, onDevice: () -> Unit, onAlerts: () -> Unit,
    unread: Int, opener: FocusRequester) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.topBar).padding(horizontal = 4.dp)) {
        val required = measurer.measure(AnnotatedString("작업"), type.heading).size.width +
            measurer.measure(AnnotatedString(state.deviceName), type.titleSm).size.width +
            with(density) { 152.dp.roundToPx() }
        val stacked = required > constraints.maxWidth
        val device: @Composable () -> Unit = {
            WorkDeviceSwitcher(state.deviceName, state.connectionTone, onDevice, Modifier.focusRequester(opener))
        }
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("작업", color = c.onTopBar, style = type.heading, modifier = Modifier.weight(1f))
                if (!stacked) Box(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * .65f)) { device() }
                Box {
                    GeoIconButton(GeoIcons.bell, if (unread > 0) "알림 ${unread}개" else "알림", onAlerts, chrome = true)
                    if (unread > 0) Text(unread.toString(), color = c.onAttention, style = type.count,
                        modifier = Modifier.align(Alignment.TopEnd).background(c.attention,
                            androidx.compose.foundation.shape.CircleShape).padding(horizontal = 5.dp, vertical = 1.dp)
                            .clearAndSetSemantics {})
                }
            }
            if (stacked) Box(Modifier.padding(start = 16.dp)) { device() }
        }
    }
}

internal fun workTaskTone(word: String): Tone = when (word) {
    "수집 중" -> Tone.INFO
    "실패" -> Tone.ERROR
    "재시도 중" -> Tone.WARNING
    "시작 요청 중", "중지 요청 중", "시간 맞추는 중" -> Tone.PENDING
    else -> Tone.UNKNOWN
}

@Composable
private fun DeviceListsNote(text: String) {
    Text(text, style = LocalGeoType.current.body, color = LocalGeoColors.current.textSecondary)
}
