package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRunDashboard(state: FieldState, pipelines: List<ManagedPipeline>,
    onBack: () -> Unit, onNew: () -> Unit, onRefresh: () -> Unit, onMore: () -> Unit,
    onLog: (TaskRun) -> Unit, onRoute: (TaskRun) -> Unit, onDismissLog: () -> Unit,
    onPipeline: (ManagedPipeline) -> Unit,
    onDeleteRun: (TaskRun) -> Unit = {},
    onDismissMessage: (String) -> Unit = {},
    onUploadOutput: (TaskRun) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onTrash: () -> Unit = {},
    onOpenResult: (TaskRun) -> Unit = {},
    developerModeEnabled: Boolean = false,
    uploadJobs: List<UploadJob> = emptyList(),
    statusStrip: @Composable () -> Unit = {}
) {
    var tab by rememberSaveable(state.deviceId) { mutableIntStateOf(0) }
    var showFilter by rememberSaveable(state.deviceId) { mutableStateOf(false) }
    var revealedRunId by rememberSaveable(state.deviceId) { mutableStateOf<String?>(null) }
    val filterFocus = remember { FocusRequester() }
    val options = listOf("전체", "진행 중", "완료")
    val runs = state.runs.filter { when (tab) {
        1 -> it.isActiveRun()
        2 -> !it.isActiveRun()
        else -> true
    } }
    LaunchedEffect(revealedRunId, state.deviceId, state.online, state.deletingRunId, state.runs) {
        val revealed = state.runs.firstOrNull { it.id == revealedRunId }
        if (revealed == null || !state.online || state.deletingRunId != null || revealed.isActiveRun()) revealedRunId = null
    }
    if (showFilter) GeoSheet("보기", { showFilter = false }, openerFocusRequester = filterFocus) {
        options.forEachIndexed { index, label -> GeoRow(label, { tab = index; showFilter = false }) }
    }
    val log = state.log
    if (developerModeEnabled && log != null) {
        SavedHistoryLogScreen(state.runs.firstOrNull { it.id == state.logEntryId }?.label ?: "수집 기록", log, onDismissLog, statusStrip)
        return
    }
    FieldDetailFrame("수집 기록", onBack, statusStrip = statusStrip, snackbar = {
        GeoOperationMessageHost(state.message, onDismissMessage,
            actionLabel = "실행 취소".takeIf { state.undoTrashId != null },
            onAction = onUndoDelete.takeIf { state.undoTrashId != null })
    }) {
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { if (state.online) onRefresh() }) {
            LazyColumn(Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("새로 고침") { onRefresh(); true })
            }, contentPadding = PaddingValues(horizontal = LocalGeoWindow.current.gutterDp.dp,
                vertical = 16.dp)) {
                item {
                    GeoSegmented("보기", options, tab, { tab = it }, { showFilter = true },
                        modifier = Modifier.focusRequester(filterFocus))
                    state.error?.let { GeoInlineNote(it, tone = Tone.ERROR, kind = GeoNoteKind.LIVE_ERROR,
                        actionLabel = "다시 시도", onAction = onRefresh) }
                    if (!state.online && state.runs.isNotEmpty()) GeoInlineNote(
                        "장치 연결이 끊겼습니다. 표시된 기록은 마지막으로 확인한 값입니다.", tone = Tone.UNKNOWN)
                    HistoryFreshness(state, onRefresh)
                    if (developerModeEnabled) state.technicalError?.let {
                        FieldTechnicalInfo("history-error", listOf(GeoTechnicalValue("오류", it)), true, state.online, null)
                    }
                }
                val groups = runs.groupBy { historyDayLabel(it.startedAt) }
                groups.forEach { (day, dayRuns) ->
                    item(key = "day-$day") {
                        GeoSection(day) {
                            dayRuns.forEach { run ->
                                key(state.deviceId, run.id) {
                                    val canDelete = state.online && state.deletingRunId == null && !run.isActiveRun()
                                    val trash = { if (canDelete) { revealedRunId = null; onDeleteRun(run) } }
                                    TwoStageHistorySwipe(run.id, canDelete, revealedRunId == run.id,
                                        { reveal -> revealedRunId = run.id.takeIf { reveal } }, trash) {
                                        HistoryRecordRow(run, historyReceipt(run, uploadJobs),
                                            onOpen = { onOpenResult(run) }, onTrash = trash, canDelete = canDelete,
                                            disabledReason = if (!state.online) "장치에 연결한 뒤 기록을 휴지통으로 옮길 수 있습니다."
                                                else "수집 중이거나 다른 기록을 처리하고 있습니다.")
                                    }
                                }
                            }
                        }
                    }
                }
                if (runs.isEmpty() && !state.loading && state.error == null) item {
                    Spacer(Modifier.height(32.dp))
                    if (!state.online && state.runs.isEmpty()) {
                        GeoStateBlock(GeoStateContent(GeoStateKind.OFFLINE, "수집 기록을 확인할 수 없습니다",
                            "장치에 연결한 뒤 수집 기록을 확인하세요."))
                    } else GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                        "수집 기록이 없습니다", "수집을 마치면 여기에 남습니다."))
                }
                if (state.nextOffset != null) item {
                    GeoButton("이전 기록 더 불러오기", onMore, style = GeoButtonStyle.Tertiary,
                        enabled = state.online && !state.loading && state.deletingRunId == null)
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryFreshness(state: FieldState, onRefresh: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.historyUpdatedAt) {
        while (true) { now = Instant.now(); kotlinx.coroutines.delay(60_000) }
    }
    val updated = state.historyUpdatedAt?.let(Instant::ofEpochMilli) ?: return
    val age = Duration.between(updated, now).coerceAtLeast(Duration.ZERO)
    if (age.seconds > 60 || !state.online) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Formats.freshness(age, updated, now), Modifier.weight(1f),
                style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
            GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary, enabled = state.online)
        }
    }
}

@Composable
private fun HistoryRecordRow(run: TaskRun, receipt: GeoReceiptState, onOpen: () -> Unit,
    onTrash: () -> Unit, canDelete: Boolean, disabledReason: String) {
    var menu by rememberSaveable(run.id) { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(canDelete) { if (!canDelete) menu = false }
    val colors = LocalGeoColors.current
    Row(Modifier.fillMaxWidth().background(colors.surface).contentDivider(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).contentTarget(onClick = onOpen).semantics(mergeDescendants = true) {
            contentDescription = "${run.contextSnapshot?.surveySectionLabel ?: run.label}, 상세 보기"
        }.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(run.contextSnapshot?.surveySectionLabel
                ?: run.output?.path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: run.label, style = LocalGeoType.current.titleSm,
                color = colors.text)
            val output = run.output
            Text(listOfNotNull(output?.path?.trimEnd('/')?.substringAfterLast('/'), resultTime(run.startedAt),
                elapsedResultDuration(run.startedAt, run.finishedAt)?.let(Formats::duration),
                output?.manifest?.bytesTotal?.let(Formats::size)).joinToString(" · "),
                style = LocalGeoType.current.caption, color = colors.textSecondary)
            GeoReceiptLine(receipt)
        }
        Box {
            GeoIconButton(GeoIcons.more, "${run.label} 메뉴", { menu = true },
                modifier = Modifier.focusRequester(menuFocus))
            GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("휴지통으로 이동", onTrash,
                destructive = true, enabled = canDelete, disabledReason = disabledReason)), openerFocusRequester = menuFocus)
        }
    }
}

internal fun historyReceipt(run: TaskRun, jobs: List<UploadJob>): GeoReceiptState {
    val cells = taskRunReceiptCells(run, emptyList())
    val exact = jobs.filter { job ->
        val context = run.contextSnapshot
        val output = run.output
        val upload = job.context
        canonicalHistoryRunId(run) != null && upload != null && context != null && output != null &&
            upload.runId == canonicalHistoryRunId(run) && upload.deviceId.equals(context.deviceId, true) &&
            upload.pipelineId == run.pipelineId && upload.outputId == output.outputId &&
            upload.surveyProjectId == context.surveyProjectId && upload.surveySectionId == context.surveySectionId &&
            job.rootId == output.rootId && job.relativePath.trim('/') == output.path.trim('/')
    }
    val server = when {
        exact.any { it.state == UploadJobState.COMPLETED && resultVerification(it)?.matched == true } ->
            GeoReceiptItem("수신 확인", Tone.SUCCESS, done = true)
        exact.any { it.state in setOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING) } ->
            GeoReceiptItem("전송 중", Tone.PENDING)
        exact.any { resultVerification(it)?.matched == false } -> GeoReceiptItem("내용 불일치", Tone.WARNING)
        exact.any { it.state == UploadJobState.COMPLETED } -> GeoReceiptItem("수신 미확인", Tone.PENDING)
        else -> GeoReceiptItem()
    }
    return GeoReceiptState(GeoReceiptItem(cells[0].value, cells[0].tone),
        GeoReceiptItem(cells[1].value, cells[1].tone), server)
}

@Composable
private fun TwoStageHistorySwipe(runId: String, enabled: Boolean, revealed: Boolean,
    onRevealChange: (Boolean) -> Unit, onTrash: () -> Unit, content: @Composable () -> Unit) {
    val revealPx = with(LocalDensity.current) { 84.dp.toPx() }
    var offsetPx by remember(runId, revealed) { mutableFloatStateOf(if (revealed) revealPx else 0f) }
    var sent by remember(runId, revealed) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) { offsetPx = 0f; onRevealChange(false) } }
    Box(Modifier.fillMaxWidth().testTag("task-run-$runId").semantics {
        if (enabled) customActions = listOf(CustomAccessibilityAction("작업 이력을 휴지통으로 이동") {
            if (!sent) { sent = true; onTrash() }; true
        })
    }) {
        if (offsetPx > 0f) GeoButton("휴지통", {
            if (enabled && !sent) { sent = true; onTrash() }
        }, modifier = Modifier.matchParentSize().testTag("task-run-trash-$runId"),
            enabled = enabled, style = GeoButtonStyle.DangerFilled)
        Box(Modifier.fillMaxWidth().offset { IntOffset(offsetPx.roundToInt(), 0) }
            .testTag("task-run-content-$runId").draggable(enabled = enabled,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { offsetPx = (offsetPx + it).coerceIn(0f, revealPx * 2f) },
                onDragStopped = {
                    when {
                        !enabled -> { offsetPx = 0f; onRevealChange(false) }
                        revealed && offsetPx >= revealPx * 1.65f -> if (!sent) { sent = true; onTrash() }
                        offsetPx >= revealPx * .55f -> { offsetPx = revealPx; onRevealChange(true) }
                        else -> { offsetPx = 0f; onRevealChange(false) }
                    }
                })) { content() }
    }
}

internal fun historyRunPresentation(run: TaskRun, online: Boolean, historyCurrent: Boolean): Pair<String, Tone> = when {
    run.isActiveRun() && (!online || !historyCurrent) -> "최근 실행 보고 · 현재 미확인" to Tone.WARNING
    run.isActiveRun() -> "실행 보고" to Tone.INFO
    run.state == "COMPLETED" -> "완료" to Tone.SUCCESS
    run.state == "STOPPED" -> "중지" to Tone.INFO
    run.state == "FAILED" -> "실패" to Tone.ERROR
    else -> "결과 미확인" to Tone.WARNING
}

internal fun canonicalHistoryRunId(run: TaskRun): String? {
    if (run.contextSnapshot == null || !run.output?.manifestState.equals("FINAL", true)) return null
    val manifestId = run.output?.manifest?.runId?.takeIf(String::isNotBlank) ?: return null
    return run.runId?.takeIf { it == manifestId } ?: manifestId.takeIf { run.runId == null }
}
