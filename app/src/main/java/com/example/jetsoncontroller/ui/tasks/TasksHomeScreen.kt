package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.background
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
import com.example.jetsoncontroller.ui.theme.*
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
    focusReportEnabled: Boolean = true,
    onCamera: (() -> Unit)? = null,
    onSendFiles: (() -> Unit)? = null,
    onDeviceMap: (() -> Unit)? = null,
    onDeviceFiles: (() -> Unit)? = null,
    /** Opens the existing stop confirmation with its bound target and availability. */
    stopAction: GeoButtonAction? = null
) {
    val c = LocalGeoColors.current
    val window = LocalGeoWindow.current
    val gutter = window.gutterDp.dp
    val tablet = window.widthDp >= 600f
    val portraitGrid = tablet && !window.twoPane && !com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane.current &&
        com.example.jetsoncontroller.ui.components.geo.shell.GeoTabletRules.homeGrid(
            minOf(720f, window.paneWidthDp) - 2f * window.gutterDp, LocalDensity.current.fontScale)
    val leftScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val rightScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val tickets = state.ticketSection
    var completedTickets by rememberSaveable(tickets?.profileId, tickets?.deviceId, tickets?.selectedUnit) { mutableStateOf(false) }
    val deviceContent by rememberUpdatedState<@Composable () -> Unit>({
        if (state.checkingDevice) GeoSkeleton("장치를 찾고 있습니다", kind = GeoSkeletonKind.DeviceCard)
        else state.deviceStrip?.let { strip ->
            GeoDevicePhotoCard(strip.deviceName, strip.connectionWord, strip.connectionTone,
                strip.freshness, state.deviceFigures.ifEmpty { workUnknownFigures() }, onClick = onOpenDevice)
            strip.firstAttention?.let {
                GeoInlineNote(it, tone = Tone.WARNING, modifier = Modifier.padding(top = 12.dp))
            }
        }
    })
    val device = remember { movableContentOf { deviceContent() } }
    val focusContent by rememberUpdatedState<@Composable () -> Unit>({
        WorkCurrentCard(state, onOpenTicket, onStartSurvey, onChangeSection, onOpenActiveRun,
            onFocusAction, onServerConnection, focusReportLabel, focusReportMessage,
            focusReportEnabled, stopAction, Modifier.padding(top = if (portraitGrid) 0.dp else 24.dp))
    })
    val focus = remember { movableContentOf { focusContent() } }
    val quickContent by rememberUpdatedState<@Composable () -> Unit>({
        GeoSection("빠른 기능") {
            GeoQuickActions(buildList {
                onCamera?.let { add(GeoQuickAction("카메라\n영상", GeoIcons.camera, it)) }
                onSendFiles?.let { add(GeoQuickAction("파일\n보내기", GeoIcons.send, it)) }
                onDeviceMap?.let { add(GeoQuickAction("장치 위치\n지도", GeoIcons.myLocation, it)) }
                add(GeoQuickAction("수집\n기록", GeoIcons.restore, onOpenHistory))
                onDeviceFiles?.let { add(GeoQuickAction("장치\n파일", GeoIcons.folder, it)) }
            })
        }
    })
    val quick = remember { movableContentOf { quickContent() } }
    val recentContent by rememberUpdatedState<@Composable () -> Unit>({
        GeoSection("최근 수집", actionLabel = if (state.recent.isEmpty()) null else "전체 기록", onAction = onOpenHistory) {
            if (state.recent.isEmpty()) state.deviceListsNote?.let {
                if (state.checkingDevice) GeoSkeleton(it, kind = GeoSkeletonKind.Rows) else DeviceListsNote(it)
            }
                ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "완료한 수집이 없습니다", "수집을 마치면 여기에 남습니다."))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.recent.forEach { row ->
                    WorkSurfaceCard {
                        GeoRowList(GeoRowTrailing.None) {
                            GeoRow(row.title, { onOpenRun(row.runId) }, meta = row.meta,
                                leading = { GeoIconTile(GeoIcons.restore) }, divider = false,
                                contentPadding = PaddingValues(0.dp),
                                extra = { GeoReceiptLine(row.receipt) })
                        }
                    }
                }
            }
        }
    })
    val recent = remember { movableContentOf { recentContent() } }
    val queueContent by rememberUpdatedState<@Composable () -> Unit>({
        WorkTicketsSection(state, onOpenTicket, onSelectTicketUnit, onChangeSection, onRefresh, onServerConnection, now,
            completedTickets, { completedTickets = it }, first = window.twoPane)
    })
    val queue = remember { movableContentOf { queueContent() } }
    val tasksContent by rememberUpdatedState<@Composable () -> Unit>({
        GeoSection("장치 작업") {
            if (state.otherTasks.isEmpty()) state.deviceListsNote?.let {
                if (state.checkingDevice) GeoSkeleton(it, kind = GeoSkeletonKind.Rows) else DeviceListsNote(it)
            }
                ?: GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "이 장치에 등록된 작업이 없습니다", "작업 등록은 관리자에게 요청하세요."))
            if (state.otherTasks.isNotEmpty()) WorkSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                GeoRowList(GeoRowTrailing.Status) {
                    state.otherTasks.forEachIndexed { index, task ->
                        GeoRow(task.title, { onOpenTask(task.pipelineId) }, meta = task.caption,
                            leading = { GeoIconTile(GeoIcons.tabDevice) }, divider = index < state.otherTasks.lastIndex,
                            badge = { GeoBadge(task.stateLabel, workTaskTone(task.stateLabel)) })
                    }
                }
            }
        }
    })
    val tasks = remember { movableContentOf { tasksContent() } }
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        WorkRootHeader(state, onSelectDevice, onAlerts, unreadCount, deviceOpener)
        PullToRefreshBox(isRefreshing = state.ticketSection?.isLoading == true, onRefresh = onRefresh,
            modifier = Modifier.weight(1f)) {
            if (window.twoPane) {
                com.example.jetsoncontroller.ui.components.geo.shell.GeoPaneLayout(
                    policy = com.example.jetsoncontroller.ui.components.geo.shell.GeoPanePolicy.WORK_HOME,
                    list = {
                        LazyColumn(state = leftScroll, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)) {
                            item(key = "device") { device() }
                            item(key = "focus") { focus() }
                            item(key = "quick") { quick() }
                            item(key = "tasks") { tasks() }
                        }
                    }, detail = {
                        LazyColumn(state = rightScroll, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)) {
                            if (state.ticketSection != null) item(key = "queue") { queue() }
                            if (state.ticketSection != null || state.recent.isNotEmpty()) item(key = "recent") { recent() }
                        }
                    })
            } else LazyColumn(state = leftScroll, modifier = Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("새로 고침") { onRefresh(); true })
            }, contentPadding = PaddingValues(start = gutter, end = gutter, top = if (tablet) 24.dp else 16.dp, bottom = if (tablet) 32.dp else 40.dp)) {
                if (portraitGrid) {
                    item(key = "device") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(Modifier.weight(1f)) { device() }
                            Box(Modifier.weight(1f)) { focus() }
                        }
                    }
                } else {
                    item(key = "device") { device() }
                    item(key = "focus") { focus() }
                }
                item(key = "quick") { quick() }
                if (portraitGrid) {
                    item(key = "recent") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            if (state.ticketSection != null || state.recent.isNotEmpty()) Box(Modifier.weight(1f)) { recent() }
                            if (state.ticketSection != null) Box(Modifier.weight(1f)) { queue() }
                        }
                    }
                } else {
                    if (state.ticketSection != null || state.recent.isNotEmpty()) item(key = "recent") { recent() }
                    if (state.ticketSection != null) item(key = "queue") { queue() }
                }
                item(key = "tasks") { tasks() }
            }
        }
    }
}

private fun workUnknownFigures() = listOf("저장 여유", "온도", "CPU", "GPU").map { GeoFigure(it, null) }

@Composable
private fun WorkCurrentCard(
    state: TasksHomeUiState, onTicket: (String) -> Unit, onStart: () -> Unit, onManual: () -> Unit,
    onRun: () -> Unit, onFocus: (NextActionCommand) -> Unit, onServer: () -> Unit,
    reportLabel: String, reportMessage: String?, reportEnabled: Boolean, stopAction: GeoButtonAction?, modifier: Modifier
) {
    val focus = state.focus
    val command = focus?.primary
    val current = state.ticketSection?.let { workTicketQueue(it.tickets, it.selectedUnit).open.firstOrNull() }
    when {
        focus?.title != null && command != null -> {
            val collecting = focus.operation == OperationState.COLLECTING
            GeoWorkCard(if (collecting) state.activeRun?.section ?: state.activeRun?.title ?: focus.title else focus.title,
                action = if (collecting && stopAction != null) stopAction.copy(label = "수집 종료",
                    style = GeoButtonStyle.DangerFilled, icon = GeoIcons.stop, disabledReason = null)
                else GeoButtonAction(if (collecting) "수집 화면 보기" else if (command == NextActionCommand.REPORT) reportLabel else command.label,
                    { if (collecting) onRun() else onFocus(command) },
                    enabled = command != NextActionCommand.REPORT || reportEnabled),
                modifier = modifier, label = if (collecting) "수집 중" else null,
                meta = listOfNotNull(if (collecting) listOfNotNull(state.deviceName, state.activeRun?.project).joinToString(" · ")
                    else if (command == NextActionCommand.REPORT) reportMessage ?: focus.body else focus.body,
                    focus.transportRemedy?.detail).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank),
                clock = state.activeClock.takeIf { collecting }, status = "진행 중".takeIf { collecting }) {
                if (collecting) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("수집 용량", style = LocalGeoType.current.supporting, color = LocalGeoColors.current.text)
                        WorkFigureValue(state.activeBytes ?: "미확인", unknown = state.activeBytes == null)
                    }
                    state.activePosition?.let { GeoBadge(it, Tone.UNKNOWN, variant = GeoBadgeVariant.INLINE) }
                    if (stopAction != null) {
                        val chosen = geoWorkCardColors(LocalGeoPalette.current)
                        GeoButton("수집 화면 보기", onRun, Modifier.fillMaxWidth(), actionArea = true,
                            containerColor = chosen.action, contentColor = chosen.onAction)
                    }
                    stopAction?.takeIf { !it.enabled }?.disabledReason?.let {
                        Text(it, color = LocalGeoColors.current.text, style = LocalGeoType.current.supporting)
                    }
                }
                focus.secondary?.let { secondary ->
                    val chosen = geoWorkCardColors(LocalGeoPalette.current)
                    GeoButton(secondary.label, { onFocus(secondary) }, Modifier.fillMaxWidth(), style = GeoButtonStyle.Secondary,
                        containerColor = chosen.background, contentColor = chosen.content)
                }
            }
        }
        state.ticketSection == null -> WorkSurfaceCard(modifier, contentPadding = PaddingValues(24.dp)) {
            GeoIconTile(GeoIcons.router)
            Spacer(Modifier.height(24.dp))
            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "서버 연결 정보가 없습니다",
                "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다."))
            GeoButton("서버 연결", onServer, Modifier.fillMaxWidth().padding(top = 12.dp), icon = GeoIcons.link)
        }
        current != null -> GeoWorkCard(current.title, GeoButtonAction("준비하기", { onTicket(current.rowId) }, icon = GeoIcons.play),
            modifier, label = "오늘의 수집 구간", meta = listOfNotNull(current.project,
                "마감 ${ticketDueLabel(current.dueDate)}").joinToString(" · "),
            status = ticketStatusLabel(current), statusTone = ticketTone(current))
        state.ticketSection.isLoading -> GeoSkeleton("수집 구간을 불러오는 중", modifier, kind = GeoSkeletonKind.Rows)
        else -> GeoWorkCard(state.pinned.scopeLabel ?: "아직 고른 구간이 없습니다",
            GeoButtonAction("준비하기", if (state.pinned.scopeLabel == null) onManual else onStart,
                enabled = state.pinned.changeEnabled, icon = GeoIcons.play),
            modifier, label = "오늘의 수집 구간", meta = state.pinned.startBlockedReason ?: state.pinned.scopeCaption,
            status = "대기")
    }
}

@Composable
private fun WorkTicketsSection(
    state: TasksHomeUiState, onOpen: (String) -> Unit, onUnit: (String?) -> Unit,
    onManual: () -> Unit, onRefresh: () -> Unit, onServer: () -> Unit, now: Instant,
    completed: Boolean, onCompletedChange: (Boolean) -> Unit, first: Boolean = false
) {
    val tickets = state.ticketSection
    val deviceUnit = tickets?.deviceUnit
    GeoSection("수집 구간", first = first) {
        if (tickets == null) {
            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "서버 연결 정보가 없습니다",
                "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다.", "서버 연결"), onAction = onServer)
        } else {
            GeoFilters(listOf(state.deviceName, "전체"), if (tickets.selectedUnit == null) 1 else 0,
                { onUnit(if (it == 0) deviceUnit else null) })
            ticketSyncLabel(tickets, now)?.let { sync ->
                Text(sync, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (ticketRefreshVisible(tickets, now)) GeoButton("다시 확인", onRefresh,
                style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !tickets.isLoading)
            val queue = workTicketQueue(tickets.tickets, tickets.selectedUnit)
            when {
                tickets.isLoading && tickets.tickets.isEmpty() -> GeoSkeleton("수집 구간을 불러오는 중",
                    Modifier.padding(top = 12.dp), kind = GeoSkeletonKind.Rows)
                queue.open.isEmpty() && queue.completed.isEmpty() -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                    "이 호기에 배정된 수집 구간이 없습니다", "GEO& Work에서 배정되면 여기에 표시됩니다."), Modifier.padding(top = 16.dp))
                else -> {
                    if (queue.open.isNotEmpty()) WorkSurfaceCard(Modifier.padding(top = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        GeoRowList(GeoRowTrailing.Status) {
                            queue.open.forEachIndexed { index, ticket ->
                                GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket), divider = index < queue.open.lastIndex,
                                    leading = { GeoIconTile(GeoIcons.fitZone) },
                                    badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                            }
                        }
                    }
                    if (queue.completed.isNotEmpty()) {
                        GeoRow("완료 ${queue.completed.size}개", { onCompletedChange(!completed) },
                            modifier = Modifier.semantics { stateDescription = if (completed) "펼침" else "접힘" }, divider = completed)
                        if (completed) WorkSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            GeoRowList(GeoRowTrailing.Status) {
                                queue.completed.forEachIndexed { index, ticket ->
                                    GeoRow(ticket.title, { onOpen(ticket.rowId) }, meta = ticketQueueMeta(ticket), divider = index < queue.completed.lastIndex,
                                        leading = { GeoIconTile(GeoIcons.fitZone) },
                                        badge = { GeoBadge(ticketStatusLabel(ticket), ticketTone(ticket)) })
                                }
                            }
                        }
                    }
                }
            }
            GeoButton("구간 직접 고르기", onManual, Modifier.fillMaxWidth().padding(top = 12.dp), style = GeoButtonStyle.Secondary)
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

