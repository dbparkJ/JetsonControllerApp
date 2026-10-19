package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.OperationMessageHost
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.transfers.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import java.time.Duration

@Composable
fun UploadProgressScreen(
    job: UploadJob?,
    verification: UploadVerification?,
    isLoading: Boolean,
    message: String?,
    error: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onVerify: () -> Unit,
    onDeleteSource: () -> Unit,
    onRestoreSource: () -> Unit,
    onBack: () -> Unit,
    serverMutationEnabled: Boolean = true,
    serverMutationDisabledReason: String? = null,
    deviceDeletionEnabled: Boolean = true,
    developerModeEnabled: Boolean = false,
    targetLabel: String? = null,
    onServerData: () -> Unit = onBack,
    onHome: () -> Unit = onBack,
    onFiles: () -> Unit = onBack,
    onDismissMessage: (String) -> Unit = {},
    deviceId: String = "",
    deviceName: String = "선택한 장치",
    currentDeviceId: String? = deviceId,
    onDeviceConfirmationClosed: () -> Unit = {},
    linkedToTicket: Boolean = false,
    ticketTitle: String? = null,
    reported: Boolean = false,
    reportPending: Boolean = false,
    reportEnabled: Boolean = true,
    reportMessage: String? = null,
    onReportTicketCompletion: () -> Unit = {},
    onResult: () -> Unit = onBack,
    onConnectionRemedy: () -> Unit = onBack,
    onFixedAction: ((TransferActionTarget, TransferMutation) -> Unit)? = null,
    ongoingRun: @Composable () -> Unit = {},
    reportFailed: Boolean = false,
    ticketReportTargetKey: String? = null
) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val actions = job?.let { transferDetailActions(it, verification, linkedToTicket, reported) }
    val fixedTarget = job?.let { transferActionTarget(deviceId, it) }
    var confirmation by remember(deviceId, job?.id) { mutableStateOf<Pair<TransferActionTarget, TransferMutation>?>(null) }
    var reportTarget by remember(deviceId, job?.id) { mutableStateOf<TransferActionTarget?>(null) }
    var reportKey by remember(deviceId, job?.id) { mutableStateOf<String?>(null) }
    var menu by remember(deviceId, job?.id) { mutableStateOf(false) }
    LaunchedEffect(currentDeviceId) {
        if (!deviceId.equals(currentDeviceId, true) && (confirmation != null || reportTarget != null)) {
            confirmation = null
            reportTarget = null
            onDeviceConfirmationClosed()
        }
    }
    val perform: (TransferActionTarget, TransferMutation) -> Unit = { target, action ->
        if (onFixedAction != null) onFixedAction(target, action) else when (action) {
            TransferMutation.CANCEL -> onCancel()
            TransferMutation.RETRY -> onRetry()
            TransferMutation.VERIFY -> onVerify()
            TransferMutation.CLEAN_UP -> onDeleteSource()
            TransferMutation.RESTORE -> onRestoreSource()
            TransferMutation.DELETE_RECORD -> Unit
        }
    }
    confirmation?.let { (target, mutation) ->
        val cancel = mutation == TransferMutation.CANCEL
        val cleanUp = mutation == TransferMutation.CLEAN_UP
        GeoConfirmDialog(
            title = when {
                cancel -> "${job?.let(::transferSourceName)} 전송을 취소할까요?"
                cleanUp -> "장치 원본을 휴지통으로 옮길까요?"
                else -> "전송 기록을 삭제할까요?"
            },
            body = when {
                cancel -> TransferCancelBody
                cleanUp -> "서버와 다시 대조한 뒤 ${deviceName}의 원본 폴더를 휴지통으로 옮깁니다. 휴지통에서 복원할 수 있습니다."
                else -> TransferRecordDeleteBody
            },
            confirmLabel = when { cancel -> "전송 취소"; cleanUp -> "휴지통으로 이동"; else -> "기록 삭제" },
            onConfirm = { confirmation = null; perform(target, mutation) },
            onDismissRequest = { confirmation = null },
            dismissLabel = if (cancel) "계속 보내기" else "취소",
            kind = if (cancel) GeoConfirmKind.DANGER else GeoConfirmKind.STANDARD,
            confirmEnabled = !isLoading && job?.let { transferMutationAllowed(target, deviceId,
                it.copy(verification = matchingTransferVerification(it, verification)), mutation,
                deviceDeletionEnabled, serverMutationEnabled) } == true,
            disabledReason = if (cleanUp) "서버 수신 내용이 바뀌어 정리할 수 없습니다." else "장치 연결과 전송 대상을 다시 확인하세요."
        )
    }
    reportTarget?.let { target ->
        GeoConfirmDialog("${ticketTitle ?: "수집 구간"}을 완료로 보고할까요?",
            "GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다.", "완료 보고",
            onConfirm = {
                if (reportEnabled && !reported && !reportPending && fixedTarget == target &&
                    reportKey == ticketReportTargetKey && actions?.primary == TransferDetailAction.REPORT) onReportTicketCompletion()
                reportTarget = null
            },
            onDismissRequest = { reportTarget = null },
            confirmEnabled = reportEnabled && !reported && !reportPending && fixedTarget == target && reportKey == ticketReportTargetKey &&
                actions?.primary == TransferDetailAction.REPORT,
            disabledReason = reportMessage ?: "보고 대상을 다시 확인하세요.")
    }
    Box(Modifier.fillMaxSize()) {
        TransferPage("전송", onBack, actions = {
            if (job != null && isDeletableUploadJob(job)) Box {
                GeoIconButton(GeoIcons.more, "더 보기", { menu = true }, chrome = true)
                GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("기록 삭제", {
                    fixedTarget?.let { confirmation = it to TransferMutation.DELETE_RECORD }
                }, destructive = true, enabled = deviceDeletionEnabled,
                    disabledReason = "장치 연결을 확인하세요.")))
            }
        }) {
            if (job == null || actions == null) {
                Column(Modifier.padding(transferGutter)) {
                    if (isLoading) GeoProgress()
                    GeoStateBlock(GeoStateContent(if (error == null) GeoStateKind.EMPTY else GeoStateKind.ERROR,
                        if (error == null) "전송을 확인하고 있습니다" else "전송을 확인하지 못했습니다",
                        error ?: "전송 정보가 도착하면 표시됩니다."), fullScreen = true)
                }
            } else {
                val receipt = matchingTransferVerification(job, verification)
                val primaryEnabled = !isLoading && when (actions.primary) {
                    TransferDetailAction.CANCEL -> deviceDeletionEnabled
                    TransferDetailAction.RETRY -> serverMutationEnabled && transferActionAllowed(job.copy(verification = receipt), TransferDetailAction.RETRY)
                    TransferDetailAction.VERIFY -> serverMutationEnabled
                    TransferDetailAction.REPORT -> reportEnabled && !reportPending
                    else -> true
                }
                val primaryReason = if (actions.primary == TransferDetailAction.REPORT) reportMessage
                    ?: "완료 보고를 확인하고 있습니다." else serverMutationDisabledReason ?: "장치 연결을 확인하세요."
                val serverMeta = when {
                    job.state == UploadJobState.FAILED -> "연결이 끊겨 멈췄습니다."
                    receipt?.matched == false -> "서버에 받은 내용이 보낸 내용과 일치하지 않습니다."
                    receipt?.matched == true -> "내용 일치 · ${Formats.itemCount((receipt.filesTotal ?: job.filesTotal)?.toLong())} · ${Formats.size(receipt.bytesTotal ?: job.bytesTotal)}"
                    job.state == UploadJobState.COMPLETED -> "보내기는 끝났고 서버 수신을 확인하고 있습니다."
                    job.state == UploadJobState.CANCELLED -> "전송을 취소했습니다."
                    else -> "보내기가 끝나면 서버 수신을 확인합니다."
                }
                GeoActionArea(visibilityKey = "$deviceId:${job.id}:${actions.primary}", action = {
                    if (actions.primary != TransferDetailAction.NONE) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when (actions.primary) {
                            TransferDetailAction.CANCEL -> "$deviceName · 취소하면 처음부터 다시 보내야 합니다."
                            TransferDetailAction.REPORT -> "$deviceName · 서버 수신 확인 · 완료를 보고하세요."
                            else -> "$deviceName · ${actions.word}"
                        }, style = t.body, color = c.textSecondary)
                        if (!primaryEnabled && actions.primary in setOf(TransferDetailAction.RETRY, TransferDetailAction.VERIFY)) {
                            GeoButton("장치 Wi-Fi 설정", onConnectionRemedy, style = GeoButtonStyle.Tertiary)
                        }
                        GeoButton(if (actions.primary == TransferDetailAction.REPORT && reportFailed) "다시 보고" else actions.primary.label, {
                            fixedTarget?.let { target -> when (actions.primary) {
                                TransferDetailAction.CANCEL -> confirmation = target to TransferMutation.CANCEL
                                TransferDetailAction.RETRY -> perform(target, TransferMutation.RETRY)
                                TransferDetailAction.VERIFY -> perform(target, TransferMutation.VERIFY)
                                TransferDetailAction.REPORT -> { reportKey = ticketReportTargetKey; reportTarget = target }
                                TransferDetailAction.RESULT -> onResult()
                                TransferDetailAction.SERVER -> onServerData()
                                TransferDetailAction.NONE -> Unit
                            } }
                        }, Modifier.fillMaxWidth(), style = if (actions.primary == TransferDetailAction.CANCEL)
                            GeoButtonStyle.DangerOutline else GeoButtonStyle.Primary,
                            enabled = primaryEnabled, disabledReason = primaryReason,
                            busy = isLoading || (reportPending && actions.primary == TransferDetailAction.REPORT),
                            busyLabel = "확인 중", actionArea = true)
                    }
                }) { inlineAction ->
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = transferGutter, vertical = 16.dp)) {
                        item { ongoingRun() }
                        item {
                            TransferHeading(transferSourceName(job), "$deviceName → ${targetLabel ?: "받는 서버"}",
                                badge = { GeoBadge(actions.word, actions.tone) })
                            if (isActiveUploadState(job.state)) {
                                val rate = estimateTransferRate(emptyList(), job.bytesTotal, job.bytesTransferred,
                                    job.throughputBytesPerSecond, job.etaSeconds)
                                GeoProgress(if ((job.bytesTotal ?: 0L) > 0 && job.bytesTransferred != null)
                                    job.bytesTransferred.toFloat() / job.bytesTotal!!.toFloat() else null)
                                Spacer(Modifier.height(24.dp))
                                GeoFigureGrid(listOf(
                                    GeoFigure("보낸 용량", job.bytesTransferred?.let(Formats::size), detail = "${Formats.size(job.bytesTotal)} 중"),
                                    GeoFigure("파일", job.filesTransferred?.toLong()?.let(Formats::itemCount), detail = "${Formats.itemCount(job.filesTotal?.toLong())} 중"),
                                    GeoFigure("남은 시간", rate.remainingSeconds?.let { Formats.eta(Duration.ofSeconds(it))?.removeSuffix(" 남음") }),
                                    GeoFigure("속도", rate.bytesPerSecond?.toLong()?.let(Formats::speed))
                                ))
                                Spacer(Modifier.height(24.dp))
                            }
                            GeoSection("처리 상태", first = true) {
                                GeoReceipt(GeoReceiptState(
                                    collection = if (job.context != null) GeoReceiptItem("종료", StatusTone.UNKNOWN, done = true) else GeoReceiptItem(),
                                    storage = if (job.context != null) GeoReceiptItem("저장 확인", StatusTone.SUCCESS, done = true) else GeoReceiptItem(),
                                    server = GeoReceiptItem(actions.word, actions.tone, serverMeta, receipt?.matched == true),
                                    report = GeoReceiptItem(when { reported -> "보고됨"; reportPending -> "보고 대기"; reportFailed -> "보고 실패"; else -> "보고 전" },
                                        when { reported -> StatusTone.SUCCESS; reportPending -> StatusTone.PENDING; reportFailed -> StatusTone.WARNING; else -> StatusTone.UNKNOWN },
                                        reportMessage ?: if (linkedToTicket && receipt?.matched == true)
                                            "${ticketTitle ?: "수집 구간"} · 완료를 보고하세요."
                                        else "서버 수신을 확인한 뒤 보고할 수 있습니다.", reported)
                                ))
                            }
                            if (job.state == UploadJobState.COMPLETED) GeoSection("관리", danger = true,
                                note = "서버와 다시 대조한 뒤 장치 원본을 휴지통으로 옮길 수 있습니다.") {
                                Text(uploadSourceLifecycleLabel(job), style = t.body, color = c.textSecondary)
                                Spacer(Modifier.height(8.dp))
                                if (actions.canRestore) GeoButton("장치 원본 복원", {
                                    fixedTarget?.let { perform(it, TransferMutation.RESTORE) }
                                }, Modifier.fillMaxWidth(), style = GeoButtonStyle.Secondary,
                                    enabled = deviceDeletionEnabled && !isLoading, disabledReason = "장치 연결을 확인하세요.")
                                else if (actions.canCleanUp) GeoButton("장치 원본 정리", {
                                    fixedTarget?.let { confirmation = it to TransferMutation.CLEAN_UP }
                                }, Modifier.fillMaxWidth(), style = GeoButtonStyle.DangerOutline,
                                    enabled = serverMutationEnabled && deviceDeletionEnabled && !isLoading,
                                    disabledReason = serverMutationDisabledReason ?: "장치 연결을 확인하세요.")
                            }
                            error?.let { GeoInlineNote("전송 상태를 다시 확인하고 시도하세요.", tone = StatusTone.ERROR) }
                            if (developerModeEnabled) GeoTechInfo(listOf(
                                GeoTechnicalValue("작업", job.id), GeoTechnicalValue("원본", job.relativePath)),
                                developerMode = true, showTechnicalInfo = true)
                        }
                        item { inlineAction() }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { OperationMessageHost(message, onDismissMessage) }
    }
}
internal fun uploadSourceLifecycleLabel(job: UploadJob): String = when (job.sourceDeletionState) {
    "PURGING" -> "장치 원본 영구 삭제 처리 중"
    "PURGED" -> "장치 원본이 영구 삭제되었습니다"
    "TRASHED" -> if (job.sourceRecoverable) "장치 원본을 휴지통에서 복원할 수 있습니다" else "장치 원본 휴지통 상태 확인 필요"
    "UNKNOWN" -> "장치 원본 상태 확인 필요"
    else -> when {
        job.sourceRecoverable -> "장치 원본을 휴지통에서 복원할 수 있습니다"
        job.sourceDeleted -> "장치 원본이 삭제되었습니다"
        else -> "장치 원본 보관 중"
    }
}

internal fun uploadProgressBytesLabel(transferred: Long?, total: Long?): String = when {
    transferred != null && total != null -> "${formatSize(transferred)} / ${formatSize(total)}"
    transferred != null -> "${formatSize(transferred)} 전송 · 전체 용량 미확인"
    else -> "전송 용량 미확인"
}

internal fun formatEta(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remainingSeconds = safe % 60
    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        minutes > 0 -> "${minutes}분 ${remainingSeconds}초"
        else -> "${remainingSeconds}초"
    }
}

internal fun stateLabel(state: UploadJobState): String = when (state) {
    UploadJobState.QUEUED -> "대기 중"
    UploadJobState.SCANNING -> "파일 확인 중"
    UploadJobState.UPLOADING -> "보내는 중"
    UploadJobState.COMPLETED -> "수신 확인 중"
    UploadJobState.FAILED -> "실패"
    UploadJobState.CANCELLED -> "취소됨"
}

internal fun formatSize(bytes: Long): String =
    com.example.jetsoncontroller.ui.model.Formats.size(bytes)
