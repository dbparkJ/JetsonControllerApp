package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.components.StatusTone

internal data class TransferDeviceAttention(val deviceId: String, val name: String, val count: Int)

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
    sendEnabled: Boolean = true,
    controlAvailable: Boolean = sendEnabled,
    onFixedCancel: ((TransferActionTarget) -> Unit)? = null,
    onDeviceConfirmationClosed: () -> Unit = {},
    onConnectionRemedy: () -> Unit = {},
    ongoingRun: @Composable () -> Unit = {}
) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val scroll = rememberLazyListState()
    var cancel by remember { mutableStateOf<ActiveTransferState?>(null) }
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
            onDismissRequest = { cancel = null }, dismissLabel = "계속 보내기", kind = GeoConfirmKind.DANGER)
    }
    TransferPage("전송", header = { TransferRootHeader(deviceName, onDevicePicker, onAlerts) }) {
        GeoActionArea(visibilityKey = deviceId ?: "transfers", action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(readiness, style = t.body, color = c.textSecondary)
                if (!sendEnabled) GeoButton("연결 방법", onConnectionRemedy, style = GeoButtonStyle.Tertiary)
                GeoButton("파일 보내기", onPickFiles, Modifier.fillMaxWidth(), actionArea = true)
            }
        }) { inlineAction ->
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("다시 확인") { onRefresh(); true })
            }, contentPadding = PaddingValues(horizontal = transferGutter, vertical = 16.dp)) {
                item { ongoingRun() }
                if (state.active.isNotEmpty()) item {
                    GeoSection("보내는 중", first = true) {
                        state.active.forEach { active ->
                            Column(Modifier.fillMaxWidth().clickable { onOpenTransfer(active.jobId) }
                                .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GeoRow(active.title, { onOpenTransfer(active.jobId) },
                                    badge = { GeoBadge(active.stateLabel, active.stateTone) })
                                GeoProgress(active.progress)
                                Text(listOfNotNull(active.sentLabel, active.speedLabel?.replace("/s", "/초"),
                                    active.remainingLabel?.let { "$it 남음" }).joinToString(" · "),
                                    style = t.caption, color = c.textSecondary)
                                if (active.canCancel) GeoButton("전송 취소", { cancel = active },
                                    style = GeoButtonStyle.DangerOutline, enabled = controlAvailable,
                                    disabledReason = readiness)
                            }
                        }
                    }
                }
                if (state.failed.isNotEmpty()) item {
                    GeoSection("확인이 필요한 전송", first = state.active.isEmpty()) {
                        state.failed.forEach { row ->
                            GeoActionRow(row.title, "다시 보내기", { onOpenTransfer(row.jobId) },
                                { onRetryTransfer(row.jobId) }, meta = row.cause,
                                leading = { GeoBadge(row.stateLabel, StatusTone.ERROR) },
                                actionEnabled = sendEnabled, disabledReason = readiness)
                        }
                    }
                }
                if (state.sendableRuns.isNotEmpty()) item {
                    GeoSection("보낼 수집", first = state.active.isEmpty() && state.failed.isEmpty()) {
                        state.sendableRuns.forEach { row ->
                            GeoActionRow(row.title, "보내기", { onOpenRun(row.runId) }, { onSendRun(row.runId) },
                                meta = row.detail, actionEnabled = sendEnabled, disabledReason = readiness)
                        }
                    }
                }
                if (state.queued.isNotEmpty()) item {
                    GeoSection("대기 중") { state.queued.forEach { TransferHomeRow(it, onOpenTransfer) } }
                }
                if (state.awaitingVerification.isNotEmpty()) item {
                    GeoSection("수신 확인 중") { state.awaitingVerification.forEach { TransferHomeRow(it, onOpenTransfer) } }
                }
                item {
                    GeoSection("서버 수신 확인", actionLabel = "전체 기록", onAction = onOpenHistory) {
                        state.verified.forEach { TransferHomeRow(it, onOpenTransfer) }
                        state.emptyText?.let {
                            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "보낸 파일이 없습니다", it))
                        }
                    }
                }
                if (attention.isNotEmpty()) item {
                    GeoSection("다른 장치") { attention.forEach { other ->
                        GeoRow("${other.name} · 확인이 필요한 전송 ${other.count}건", { onSelectDevice(other.deviceId) },
                            meta = "이 장치로 바꾸기")
                    } }
                }
                item {
                    GeoSection("서버") {
                        GeoRow("서버 수신 결과", onOpenServerFiles)
                        state.deviceEventNotice?.let { notice ->
                            GeoFactRow("GEO& Work 알림", notice.text)
                        }
                    }
                    GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary)
                }
                item { inlineAction() }
            }
        }
    }
}

@Composable
private fun TransferHomeRow(row: TransferRowState, onOpen: (String) -> Unit) {
    GeoRow(row.title, { onOpen(row.jobId) }, meta = row.detail,
        badge = { GeoBadge(if (row.tone == StatusTone.SUCCESS) "수신 확인" else row.detail, row.tone) })
}
