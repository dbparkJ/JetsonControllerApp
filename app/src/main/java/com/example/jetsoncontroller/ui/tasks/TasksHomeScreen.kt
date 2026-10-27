package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    focusReportEnabled: Boolean = true,
    onCamera: (() -> Unit)? = null,
    onSendFiles: (() -> Unit)? = null,
    onDeviceMap: (() -> Unit)? = null,
    onDeviceFiles: (() -> Unit)? = null,
    /** Opens the existing stop confirmation with its bound target and availability. */
    stopAction: GeoButtonAction? = null,
    focusTicketRowId: String? = null
) {
    val window = LocalGeoWindow.current
    val twoPane = window.twoPane && !LocalGeoPane.current
    val leftScroll = rememberLazyListState()
    val rightScroll = rememberLazyListState()
    val tickets = state.ticketSection
    var pullRefreshing by remember(tickets?.profileId, tickets?.deviceId) { mutableStateOf(false) }
    LaunchedEffect(tickets?.isLoading) {
        if (tickets?.isLoading != true) pullRefreshing = false
    }
    val refreshByPull = {
        if (tickets != null && !tickets.isLoading) pullRefreshing = true
        onRefresh()
    }
    var completedTickets by rememberSaveable(tickets?.profileId, tickets?.deviceId, tickets?.selectedUnit) { mutableStateOf(false) }
    val actions = workHomeActions(state, onOpenTicket, onStartSurvey, onChangeSection, onOpenActiveRun,
        onFocusAction, onServerConnection, focusReportLabel, focusReportEnabled, stopAction)
    val featuredId = workHomeFeaturedTicket(state)?.rowId ?: focusTicketRowId
    val quickActions = buildList {
        onCamera?.let { add(GeoQuickAction("카메라 영상", GeoIcons.camera, it)) }
        onDeviceMap?.let { add(GeoQuickAction("장치 위치 지도", GeoIcons.myLocation, it)) }
        onSendFiles?.let { add(GeoQuickAction("파일 보내기", GeoIcons.send, it)) }
        add(GeoQuickAction("수집 기록", GeoIcons.restore, onOpenHistory))
        onDeviceFiles?.let { add(GeoQuickAction("장치 파일", GeoIcons.folder, it)) }
    }
    // Movable sections retain their composition when the window changes between one and two panes.
    val focusContent by rememberUpdatedState<@Composable () -> Unit>({
        WorkCurrentCard(state, focusReportMessage, onRefresh, Modifier.padding(horizontal = workContentGutter()))
    })
    val focus = remember { movableContentOf { focusContent() } }
    val deviceContent by rememberUpdatedState<@Composable () -> Unit>({
        Column(Modifier.padding(horizontal = workContentGutter(), vertical = 12.dp)) {
            if (state.checkingDevice) GeoSkeleton("장치를 찾고 있습니다", kind = GeoSkeletonKind.DeviceCard)
            else state.deviceStrip?.let { strip ->
                GeoDevicePhotoCard(strip.deviceName, strip.connectionWord, strip.connectionTone,
                    strip.freshness, state.deviceFigures.ifEmpty { workUnknownFigures() }, onClick = onOpenDevice)
                strip.firstAttention?.let { GeoInlineNote(it, tone = Tone.WARNING, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    })
    val device = remember { movableContentOf { deviceContent() } }
    val quickContent by rememberUpdatedState<@Composable () -> Unit>({
        if (window.widthDp < 600f) {
            GeoSection("빠른 기능", Modifier.padding(horizontal = workContentGutter()), first = true) {}
            GeoHorizontalTiles(quickActions, contentPadding = PaddingValues(horizontal = workContentGutter()))
        } else GeoSection("빠른 기능", Modifier.padding(horizontal = workContentGutter()), first = true) {
            GeoQuickActions(quickActions)
        }
    })
    val quick = remember { movableContentOf { quickContent() } }
    val recentContent by rememberUpdatedState<@Composable () -> Unit>({
        GeoSection("최근 수집", Modifier.padding(horizontal = workContentGutter()),
            actionLabel = if (state.recent.isEmpty()) null else "전체 기록", onAction = onOpenHistory) {
            if (state.recent.isEmpty()) {
                state.deviceListsNote?.let { DeviceListsNote(it, state.checkingDevice) }
                    ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "완료한 수집이 없습니다", "수집을 마치면 여기에 남습니다."))
            } else GeoRowList(GeoRowTrailing.Status) {
                state.recent.forEachIndexed { index, row ->
                    GeoRow(row.title, { onOpenRun(row.runId) }, meta = row.meta,
                        leading = { GeoIconTile(GeoIcons.restore) }, divider = index < state.recent.lastIndex,
                        badge = { GeoReceiptLine(row.receipt, compact = true) })
                }
            }
        }
    })
    val recent = remember { movableContentOf { recentContent() } }
    val queueContent by rememberUpdatedState<@Composable () -> Unit>({
        if (tickets != null) WorkTicketsSection(state, onOpenTicket, onSelectTicketUnit, onChangeSection,
            onRefresh, now, completedTickets, { completedTickets = it }, featuredId,
            showManual = state.focus?.primary != null || workHomeFeaturedTicket(state) != null,
            modifier = Modifier.padding(horizontal = workContentGutter()))
    })
    val queue = remember { movableContentOf { queueContent() } }
    val tasksContent by rememberUpdatedState<@Composable () -> Unit>({
        GeoSection("장치 작업", Modifier.padding(horizontal = workContentGutter())) {
            if (state.otherTasks.isEmpty()) {
                state.deviceListsNote?.let { DeviceListsNote(it, state.checkingDevice) }
                    ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "이 장치에 등록된 작업이 없습니다", "작업 등록은 관리자에게 요청하세요."))
            } else GeoRowList(GeoRowTrailing.Status) {
                state.otherTasks.forEachIndexed { index, task ->
                    GeoRow(task.title, { onOpenTask(task.pipelineId) }, meta = task.caption,
                        leading = { GeoIconTile(GeoIcons.tabDevice) }, divider = index < state.otherTasks.lastIndex,
                        badge = { GeoBadge(task.stateLabel, workTaskTone(task.stateLabel)) })
                }
            }
        }
    })
    val tasks = remember { movableContentOf { tasksContent() } }
    val date = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN).withZone(ZoneId.systemDefault()).format(now)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        GeoLargeTitleScaffold("작업", modifier = if (twoPane) Modifier else Modifier.widthIn(max = 720.dp),
            subtitle = date, primaryAction = actions.primary.takeUnless { twoPane },
            secondaryAction = actions.secondary.takeUnless { twoPane }, actions = {
                WorkDeviceSwitcher(state.deviceName, state.connectionTone, onSelectDevice, Modifier.focusRequester(deviceOpener))
                GeoIconButton(GeoIcons.bell, if (unreadCount > 0) "알림 ${unreadCount}개" else "알림", onAlerts)
            }) {
            PullToRefreshBox(isRefreshing = pullRefreshing && tickets?.isLoading == true, onRefresh = refreshByPull,
                modifier = Modifier.fillMaxSize().semantics {
                    customActions = listOf(CustomAccessibilityAction("새로 고침") { refreshByPull(); true })
                }) {
                if (twoPane) GeoPaneLayout(policy = GeoPanePolicy.WORK_HOME, list = {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(state = leftScroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                            item(key = "focus") { focus() }
                            item(key = "device") { device() }
                        }
                        actions.primary?.let { GeoFixedActionBar(it, secondaryAction = actions.secondary) }
                    }
                }, detail = {
                    LazyColumn(state = rightScroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item(key = "quick") { quick() }
                        item(key = "recent") { recent() }
                        item(key = "queue") { queue() }
                        item(key = "tasks") { tasks() }
                    }
                }) else LazyColumn(state = leftScroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "focus") { focus() }
                    item(key = "device") { device() }
                    item(key = "quick") { quick() }
                    item(key = "recent") { recent() }
                    item(key = "queue") { queue() }
                    item(key = "tasks") { tasks() }
                }
            }
        }
    }
}

@Composable
private fun workContentGutter() = if (LocalGeoPane.current) 0.dp else 24.dp

private fun workUnknownFigures() = listOf("저장 여유", "온도", "CPU", "GPU").map { GeoFigure(it, null) }

/** A ticket represented by the today card is omitted from the queue in every window size. */
internal fun workHomeFeaturedTicket(state: TasksHomeUiState): ServerTicket? =
    if (state.focus?.title != null && state.focus.primary != null) null
    else state.ticketSection?.let { workTicketQueue(it.tickets, it.selectedUnit).open.firstOrNull() }

internal fun workHomeVisibleQueue(tickets: ServerTicketUiState, featuredId: String?): WorkTicketQueue =
    workTicketQueue(tickets.tickets, tickets.selectedUnit).let { queue ->
        queue.copy(open = queue.open.filterNot { it.rowId == featuredId },
            completed = queue.completed.filterNot { it.rowId == featuredId })
    }

internal data class WorkHomeActions(val primary: GeoButtonAction?, val secondary: GeoButtonAction? = null)

internal fun workHomeActions(
    state: TasksHomeUiState, onTicket: (String) -> Unit, onStart: () -> Unit, onManual: () -> Unit,
    onRun: () -> Unit, onFocus: (NextActionCommand) -> Unit, onServer: () -> Unit,
    reportLabel: String, reportEnabled: Boolean, stopAction: GeoButtonAction?
): WorkHomeActions {
    val focus = state.focus
    val command = focus?.primary
    if (focus?.title != null && command != null) {
        if (focus.operation == OperationState.COLLECTING) return WorkHomeActions(
            GeoButtonAction("수집 화면 보기", onRun), stopAction?.copy(label = "수집 종료", style = GeoButtonStyle.DangerOutline, icon = GeoIcons.stop))
        return WorkHomeActions(GeoButtonAction(if (command == NextActionCommand.REPORT) reportLabel else command.label,
            { onFocus(command) }, enabled = command != NextActionCommand.REPORT || reportEnabled,
            disabledReason = "수집 결과와 서버 수신을 다시 확인하세요.".takeIf { command == NextActionCommand.REPORT && !reportEnabled }),
            focus.secondary?.let { secondary -> GeoButtonAction(secondary.label, { onFocus(secondary) }, style = GeoButtonStyle.Secondary) })
    }
    if (state.ticketSection == null) return WorkHomeActions(GeoButtonAction("서버 연결", onServer, icon = GeoIcons.link))
    workHomeFeaturedTicket(state)?.let { ticket -> return WorkHomeActions(GeoButtonAction("준비하기", { onTicket(ticket.rowId) }, icon = GeoIcons.play)) }
    if (state.ticketSection.isLoading) return WorkHomeActions(null)
    return WorkHomeActions(GeoButtonAction("준비하기", if (state.pinned.scopeLabel == null) onManual else onStart,
        enabled = state.pinned.changeEnabled, icon = GeoIcons.play, disabledReason = state.pinned.startBlockedReason))
}

@Composable
private fun WorkCurrentCard(state: TasksHomeUiState, reportMessage: String?, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    val focus = state.focus
    val command = focus?.primary
    val current = workHomeFeaturedTicket(state)
    when {
        focus?.title != null && command != null -> {
            val collecting = focus.operation == OperationState.COLLECTING
            GeoTodayCard(if (collecting) state.activeRun?.section ?: state.activeRun?.title ?: focus.title else focus.title,
                modifier, label = if (collecting) "현재 수집" else null,
                meta = listOfNotNull(if (collecting) listOfNotNull(state.deviceName, state.activeRun?.project).joinToString(" · ")
                    else if (command == NextActionCommand.REPORT) reportMessage ?: focus.body else focus.body,
                    focus.transportRemedy?.detail).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank),
                clock = state.activeClock.takeIf { collecting }, descriptionStatus = "수집 중".takeIf { collecting }) {
                if (collecting) {
                    Text("수집 용량 ${state.activeBytes ?: "미확인"}", style = LocalGeoType.current.body.copy(fontFeatureSettings = "tnum"),
                        color = LocalGeoColors.current.text)
                    state.activePosition?.let { GeoBadge(it, Tone.UNKNOWN, variant = GeoBadgeVariant.INLINE) }
                }
            }
        }
        state.ticketSection == null -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "서버 연결 정보가 없습니다",
            "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다."), modifier)
        current != null -> GeoTodayCard(current.title, modifier, label = "오늘의 수집 구간",
            meta = current.project, deadline = "마감 ${ticketDueLabel(current.dueDate)}",
            descriptionStatus = ticketTodayStatusLabel(current), statusTone = ticketTone(current))
        state.ticketSection.isLoading -> GeoSkeleton("수집 구간을 불러오는 중", modifier, kind = GeoSkeletonKind.Rows)
        workHomeShowsTicketError(state) -> GeoStateBlock(GeoStateContent(GeoStateKind.ERROR,
            "수집 구간을 불러오지 못했습니다", ticketLoadErrorDescription(state.ticketSection.quietError.orEmpty()),
            actionLabel = "다시 확인"), modifier, onAction = onRefresh)
        else -> GeoTodayCard(state.pinned.scopeLabel ?: "아직 고른 구간이 없습니다", modifier,
            label = "오늘의 수집 구간", meta = state.pinned.startBlockedReason ?: state.pinned.scopeCaption,
            descriptionStatus = "수집 전".takeIf { state.pinned.scopeLabel != null })
    }
}

@Composable
private fun WorkTicketsSection(
    state: TasksHomeUiState, onOpen: (String) -> Unit, onUnit: (String?) -> Unit,
    onManual: () -> Unit, onRefresh: () -> Unit, now: Instant,
    completed: Boolean, onCompletedChange: (Boolean) -> Unit, featuredId: String?, showManual: Boolean,
    modifier: Modifier = Modifier
) {
    val tickets = state.ticketSection ?: return
    GeoSection(if (featuredId == null) "수집 구간" else "다른 수집 구간", modifier,
        actionLabel = "구간 직접 고르기".takeIf { showManual }, onAction = onManual) {
        GeoFilters(listOf(state.deviceName, "전체"), if (tickets.selectedUnit == null) 1 else 0,
            { onUnit(if (it == 0) tickets.deviceUnit else null) })
        ticketSyncLabel(tickets, now)?.let { Text(it, style = LocalGeoType.current.caption,
            color = LocalGeoColors.current.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
        tickets.quietError?.takeUnless { workHomeShowsTicketError(state) }?.let { GeoInlineNote(it, tone = Tone.WARNING, kind = GeoNoteKind.LIVE_ERROR,
            actionLabel = "다시 확인", onAction = onRefresh) }
        if (tickets.quietError == null && ticketRefreshVisible(tickets, now)) GeoButton("다시 확인", onRefresh,
            style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !tickets.isLoading)
        Spacer(Modifier.height(16.dp))
        val queue = workTicketQueue(tickets.tickets, tickets.selectedUnit)
        val visible = workHomeVisibleQueue(tickets, featuredId)
        val open = visible.open
        val finished = visible.completed
        when {
            tickets.isLoading && tickets.tickets.isEmpty() -> GeoSkeleton("수집 구간을 불러오는 중", kind = GeoSkeletonKind.Rows)
            queue.open.isEmpty() && queue.completed.isEmpty() && tickets.quietError == null -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                "이 호기에 배정된 수집 구간이 없습니다", "GEO& Work에서 배정되면 여기에 표시됩니다."))
            else -> {
                if (open.isNotEmpty()) GeoRowList(GeoRowTrailing.Status) {
                    open.forEachIndexed { index, ticket ->
                        GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket), divider = index < open.lastIndex,
                            leading = { GeoIconTile(GeoIcons.fitZone) }, badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                    }
                }
                if (finished.isNotEmpty()) GeoRowList(GeoRowTrailing.Status,
                    Modifier.padding(top = if (open.isEmpty()) 0.dp else 12.dp)) {
                    GeoRow("완료 ${finished.size}개", { onCompletedChange(!completed) }, leading = { GeoIconTile(GeoIcons.restore) },
                        modifier = Modifier.semantics { stateDescription = if (completed) "펼침" else "접힘" }, divider = completed)
                    if (completed) finished.forEachIndexed { index, ticket ->
                        GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket), divider = index < finished.lastIndex,
                            leading = { GeoIconTile(GeoIcons.fitZone) }, badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                    }
                }
            }
        }
    }
}

/** The full error and retry belong to one place; cached rows keep their local warning. */
internal fun workHomeShowsTicketError(state: TasksHomeUiState): Boolean =
    state.focus?.let { it.title != null && it.primary != null } != true &&
        state.ticketSection?.let { !it.isLoading && it.tickets.isEmpty() && it.quietError != null } == true

internal fun workTaskTone(word: String): Tone = when (word) {
    "수집 중" -> Tone.INFO
    "실패" -> Tone.ERROR
    "재시도 중" -> Tone.WARNING
    "시작 요청 중", "중지 요청 중", "시간 맞추는 중" -> Tone.PENDING
    else -> Tone.UNKNOWN
}

@Composable
private fun DeviceListsNote(text: String, checking: Boolean) {
    if (checking) GeoSkeleton(text, kind = GeoSkeletonKind.Rows)
    else GeoInlineNote(text, tone = Tone.UNKNOWN)
}
