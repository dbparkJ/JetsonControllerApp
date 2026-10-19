package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.transfers.*
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant
import java.time.ZoneId

@Composable
fun UploadQueueScreen(
    queue: List<UploadJob>,
    targets: List<UploadTarget>,
    isLoading: Boolean,
    error: String?,
    message: String? = null,
    onRefresh: () -> Unit,
    onManageTargets: () -> Unit,
    onJobClick: (UploadJob) -> Unit,
    onDeleteJob: (UploadJob) -> Unit,
    onBack: () -> Unit,
    mutationEnabled: Boolean = true,
    deviceId: String? = null,
    developerModeEnabled: Boolean = false,
    onHome: () -> Unit = onBack,
    onFiles: () -> Unit = onBack,
    onDismissMessage: (String) -> Unit = {},
    jobDates: Map<String, String> = emptyMap(),
    onFixedDelete: ((TransferActionTarget) -> Unit)? = null,
    currentDeviceId: String? = deviceId,
    onDeviceConfirmationClosed: () -> Unit = {},
    ongoingRun: @Composable () -> Unit = {}
) {
    var selected by rememberSaveable(deviceId) { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var deletion by remember(deviceId) { mutableStateOf<Pair<TransferActionTarget, UploadJob>?>(null) }
    LaunchedEffect(currentDeviceId) {
        if (deletion != null && !deviceId.equals(currentDeviceId, true)) {
            deletion = null
            onDeviceConfirmationClosed()
        }
    }
    val options = listOf("전체", "실패", "완료", "취소")
    val visible = queue.filter { transferLogMatches(it, selected) }
    deletion?.let { (target, job) ->
        GeoConfirmDialog("전송 기록을 삭제할까요?", TransferRecordDeleteBody, "기록 삭제",
            onConfirm = { deletion = null; if (onFixedDelete != null) onFixedDelete(target) else onDeleteJob(job) },
            onDismissRequest = { deletion = null },
            confirmEnabled = mutationEnabled && !isLoading, disabledReason = "장치 연결을 확인하세요.")
    }
    if (picker) GeoSheet("전송 기록 필터", { picker = false }) {
        options.forEachIndexed { i, name -> GeoRadioRow(name, i == selected, { selected = i; picker = false }) }
    }
    Box(Modifier.fillMaxSize()) {
        TransferPage("전송 기록", onBack) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = transferGutter, vertical = 16.dp)) {
                item { ongoingRun() }
                if (queue.isNotEmpty()) item {
                    GeoSegmented("전송 기록 필터", options, selected, { selected = it }, { picker = true })
                }
                if (isLoading) item { GeoProgress() }
                error?.let { item { GeoInlineNote("전송 기록을 불러오지 못했습니다. 다시 시도하세요.",
                    actionLabel = "다시 확인", onAction = onRefresh) } }
                if (visible.isEmpty() && !isLoading) item {
                    GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "전송 기록이 없습니다",
                        "파일을 보내면 여기에 남습니다."), fullScreen = true)
                }
                visible.groupBy { transferLogDay(jobDates[it.id] ?: it.verifiedAt) }.forEach { (day, jobs) ->
                    item(key = day) {
                        GeoSection(day) { jobs.forEach { job ->
                            val actions = transferDetailActions(job)
                            var menu by remember(deviceId, job.id) { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                GeoRow(transferSourceName(job), { onJobClick(job) }, Modifier.weight(1f),
                                    meta = if (job.state == com.example.jetsoncontroller.model.UploadJobState.FAILED)
                                        "연결이 끊겨 멈췄습니다." else listOfNotNull(
                                            targets.firstOrNull { it.id == job.targetId }?.let(::transferTargetLabel),
                                            job.bytesTotal?.let(Formats::size)).joinToString(" · "),
                                    badge = { GeoBadge(actions.word, actions.tone) })
                                if (isDeletableUploadJob(job)) Box {
                                    GeoIconButton(GeoIcons.more, "${transferSourceName(job)} 메뉴", { menu = true })
                                    GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("기록 삭제", {
                                        deletion = transferActionTarget(deviceId.orEmpty(), job) to job
                                    }, destructive = true, enabled = mutationEnabled && !isLoading,
                                        disabledReason = "장치 연결을 확인하세요.")))
                                } else Spacer(Modifier.width(48.dp))
                            }
                        } }
                    }
                }
                item { GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { GeoOperationMessageHost(message, onDismissMessage) }
    }
}

internal fun transferLogDay(value: String?, now: Instant = Instant.now()): String {
    val date = value?.let { runCatching { Instant.parse(it).atZone(ZoneId.of("Asia/Seoul")).toLocalDate() }.getOrNull() }
        ?: return "전송 기록"
    val today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
    return when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> "${date.monthValue}월 ${date.dayOfMonth}일"
    }
}
