package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.storage.R7Section

internal data class TransferDeviceAttention(val deviceId: String, val name: String, val count: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransfersHomeScreen(
    state: TransfersHomeUiState,
    onPickFiles: () -> Unit,
    onOpenServerFiles: () -> Unit,
    onOpenTransfer: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (String) -> Unit,
    onSendRun: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRun: (String) -> Unit = onSendRun,
    deviceId: String? = null,
    deviceName: String = "선택된 장치 없음",
    readiness: String = state.explanation,
    attention: List<TransferDeviceAttention> = emptyList(),
    onSelectDevice: (String) -> Unit = {},
    onDevicePicker: () -> Unit = {},
    onAlerts: () -> Unit = {},
    onRefresh: () -> Unit = {},
    /** The board has no standing refresh button; it appears only when the transfer list could not be read. */
    refreshVisible: Boolean = false,
    sendEnabled: Boolean = true,
    controlAvailable: Boolean = sendEnabled,
    onFixedCancel: ((TransferActionTarget) -> Unit)? = null,
    onDeviceConfirmationClosed: () -> Unit = {},
    onConnectionRemedy: () -> Unit = {},
    ongoingRun: @Composable () -> Unit = {},
    connectionTone: Tone = Tone.UNKNOWN
) {
    val c = LocalGeoColors.current
    val tablet = com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f
    val t = LocalGeoType.current
    val scroll = com.example.jetsoncontroller.ui.components.geo.shell.rememberGeoPaneListState("transfers")
    var filter by rememberSaveable(deviceId) { mutableIntStateOf(0) }
    var cancel by rememberSaveable(deviceId, stateSaver = geoSnapshotSaver(ActiveTransferState::class.java)) {
        mutableStateOf<ActiveTransferState?>(null)
    }
    // The first transfer group starts without a section divider.
    val firstSection = when {
        state.active.isNotEmpty() -> "보내는 중"
        state.failed.isNotEmpty() -> "확인이 필요한 전송"
        state.sendableRuns.isNotEmpty() -> "보낼 수집"
        state.queued.isNotEmpty() -> "대기 중"
        state.awaitingVerification.isNotEmpty() -> "수신 확인 중"
        else -> "서버 수신 확인"
    }
    LaunchedEffect(deviceId) {
        if (cancel != null && cancel?.target?.deviceId?.equals(deviceId, true) == false) {
            cancel = null
            onDeviceConfirmationClosed()
        }
    }
    cancel?.let { fixed ->
        GeoConfirmDialog("${fixed.title} 전송을 취소할까요?", TransferCancelBody, "전송 취소",
            onConfirm = {
                cancel = null
                if (fixed.target != null && onFixedCancel != null) onFixedCancel(fixed.target)
                else onCancelTransfer(fixed.jobId)
            },
            onDismissRequest = { cancel = null }, dismissLabel = "계속 보내기", kind = GeoConfirmKind.DANGER,
            facts = {
                GeoCopyableValue(fixed.title, label = "보내는 자료")
                GeoPropertyRow("보내는 장치", deviceName)
                fixed.target?.let { GeoCopyableValue(it.path, label = "원본 경로") }
            })
    }
    TransferPage("전송", subtitle = deviceName, actions = {
        GeoIconButton(GeoIcons.tabDevice, "장치 선택", onDevicePicker)
        GeoIconButton(GeoIcons.bell, "알림", onAlerts)
    }, primaryAction = GeoButtonAction("파일 보내기", onPickFiles)) {
        GeoFilterChipRow(listOf("전체", "보내는 중 ${state.active.size}", "확인 필요 ${state.failed.size}",
            "보낼 수집 ${state.sendableRuns.size}", "수신 확인 ${state.verified.size}"), filter, { filter = it },
            contentPadding = PaddingValues(horizontal = transferGutter))
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("다시 확인") { onRefresh(); true })
            }, contentPadding = PaddingValues(start = transferGutter, end = transferGutter, top = if (tablet) 24.dp else 16.dp, bottom = if (tablet) 32.dp else 40.dp)) {
                item { ongoingRun() }
                if (filter != 0 && transferHomeVisibleCount(state, filter) == 0) item {
                    GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "표시할 전송이 없습니다",
                        "다른 종류를 선택하면 전송과 수집 자료를 볼 수 있습니다."))
                }
                if ((filter == 0 || filter == 1) && state.active.isNotEmpty()) item {
                    state.active.forEachIndexed { index, active ->
                        if (index == 0) {
                        GeoTodayCard(active.title,
                            status = active.stateLabel, statusTone = active.stateTone, meta = listOfNotNull(active.subtitle,
                                active.remainingLabel?.let { "$it 남음" }).joinToString(" · ")) {
                            active.speedLabel?.let { Text(it.replace("/s", "/초"), style = t.supporting.copy(fontFeatureSettings = "tnum"),
                                color = LocalGeoColors.current.text) }
                            GeoProgress(active.progress)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                active.progress?.let { Text("${(it * 100).toInt()}%", style = t.caption.copy(fontFeatureSettings = "tnum"), color = LocalGeoColors.current.text) }
                                Text(active.sentLabel, style = t.caption.copy(fontFeatureSettings = "tnum"), color = LocalGeoColors.current.text)
                            }
                        }
                        GeoRowList(GeoRowTrailing.None) {
                            if (active.canCancel) GeoActionRow(active.title, "전송 취소",
                                { onOpenTransfer(active.jobId) }, { cancel = active },
                                leading = { GeoIconTile(GeoIcons.tabTransfer) }, actionEnabled = controlAvailable,
                                disabledReason = readiness, divider = false)
                            else GeoRow("전송 상세", { onOpenTransfer(active.jobId) }, divider = false)
                        }
                        } else R7Section(active.stateLabel, trailingColumn = GeoRowTrailing.Status) {
                            GeoRow(active.title, { onOpenTransfer(active.jobId) }, divider = false,
                                meta = active.subtitle,
                                leading = { GeoIconTile(GeoIcons.tabTransfer) },
                                badge = { GeoBadge(active.stateLabel, active.stateTone) }, extra = {
                                    GeoProgress(active.progress)
                                    Text(listOfNotNull(active.sentLabel, active.speedLabel?.replace("/s", "/초"),
                                        active.remainingLabel?.let { "$it 남음" }).joinToString(" · "),
                                        style = t.supporting, color = c.textSecondary)
                                    if (active.canCancel) GeoButton("전송 취소", { cancel = active },
                                        style = GeoButtonStyle.DangerOutline, enabled = controlAvailable,
                                        disabledReason = readiness)
                                })
                        }
                    }
                }
                if ((filter == 0 || filter == 2) && state.failed.isNotEmpty()) item {
                    R7Section("확인이 필요한 전송", first = firstSection == "확인이 필요한 전송", trailingColumn = GeoRowTrailing.None) {
                        state.failed.forEachIndexed { index, row ->
                            GeoActionRow(row.title, "다시 보내기", { onOpenTransfer(row.jobId) },
                                { onRetryTransfer(row.jobId) }, meta = "${row.stateLabel} · ${row.cause}", divider = index < state.failed.lastIndex,
                                leading = { GeoIconTile(GeoIcons.warning, tone = Tone.ERROR) },
                                actionEnabled = sendEnabled, disabledReason = readiness)
                        }
                    }
                }
                if ((filter == 0 || filter == 3) && state.sendableRuns.isNotEmpty()) item {
                    R7Section("보낼 수집", first = firstSection == "보낼 수집", trailingColumn = GeoRowTrailing.None) {
                        state.sendableRuns.forEachIndexed { index, row ->
                            GeoActionRow(row.title, "보내기", { onOpenRun(row.runId) }, { onSendRun(row.runId) },
                                meta = row.detail, leading = { GeoIconTile(GeoIcons.folder) },
                                divider = index < state.sendableRuns.lastIndex, actionEnabled = sendEnabled, disabledReason = readiness)
                        }
                    }
                }
                if ((filter == 0 || filter == 1) && state.queued.isNotEmpty()) item {
                    R7Section("대기 중", first = firstSection == "대기 중", trailingColumn = GeoRowTrailing.Status) { state.queued.forEachIndexed { index, row -> TransferHomeRow(row, onOpenTransfer, index < state.queued.lastIndex) } }
                }
                if ((filter == 0 || filter == 1) && state.awaitingVerification.isNotEmpty()) item {
                    R7Section("수신 확인 중", first = firstSection == "수신 확인 중", trailingColumn = GeoRowTrailing.Status) { state.awaitingVerification.forEachIndexed { index, row -> TransferHomeRow(row, onOpenTransfer, index < state.awaitingVerification.lastIndex) } }
                }
                if (filter == 0 || filter == 4) item {
                    R7Section("서버 수신 확인", first = firstSection == "서버 수신 확인", actionLabel = "전체 기록", onAction = onOpenHistory,
                        trailingColumn = GeoRowTrailing.Status) {
                        state.verified.forEachIndexed { index, row -> TransferHomeRow(row, onOpenTransfer, index < state.verified.lastIndex) }
                        state.emptyText?.let {
                            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "보낸 파일이 없습니다", it),
                                Modifier.padding(horizontal = 8.dp, vertical = 24.dp))
                        }
                    }
                }
                if (attention.isNotEmpty()) item {
                    R7Section("다른 장치", trailingColumn = GeoRowTrailing.None) { attention.forEachIndexed { index, other ->
                        GeoRow("${other.name} · 확인이 필요한 전송 ${other.count}건", { onSelectDevice(other.deviceId) },
                            meta = "이 장치로 바꾸기", leading = { GeoIconTile(GeoIcons.tabDevice) }, divider = index < attention.lastIndex)
                    } }
                }
                item {
                    R7Section("서버", trailingColumn = GeoRowTrailing.None) {
                        GeoRow("서버 수신 결과", onOpenServerFiles, leading = { GeoIconTile(GeoIcons.router) }, divider = false)
                        state.deviceEventNotice?.let { notice ->
                            GeoFactRow("작업 알림", notice.text)
                        }
                    }
                    if (refreshVisible) GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                }
                item {
                    GeoReadinessLine(readiness, if (sendEnabled) connectionTone else Tone.WARNING)
                    if (!sendEnabled) GeoButton("연결 방법", onConnectionRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                }
            }
    }
}

@Composable
private fun TransferHomeRow(row: TransferRowState, onOpen: (String) -> Unit, divider: Boolean = true) {
    GeoRow(row.title, { onOpen(row.jobId) }, meta = row.detail, divider = divider,
        leading = { GeoIconTile(GeoIcons.tabTransfer) },
        badge = { GeoBadge(if (row.tone == Tone.SUCCESS) "수신 확인" else row.detail, row.tone) })
}

/** Count only records in the selected presentation group; progress is not receipt evidence. */
internal fun transferHomeVisibleCount(state: TransfersHomeUiState, filter: Int): Int = when (filter) {
    1 -> state.active.size + state.queued.size + state.awaitingVerification.size
    2 -> state.failed.size
    3 -> state.sendableRuns.size
    4 -> state.verified.size
    else -> state.active.size + state.queued.size + state.awaitingVerification.size +
        state.failed.size + state.sendableRuns.size + state.verified.size
}
