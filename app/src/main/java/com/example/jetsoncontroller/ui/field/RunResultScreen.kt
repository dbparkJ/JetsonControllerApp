package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.zone.resultObservation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Suppress("UNUSED_PARAMETER") // Retain the graph signature until its owner wires the new seams.
@Composable
internal fun RunResultScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: Tone,
    run: PipelineRun?,
    uploadEnabled: Boolean,
    uploadDisabledReason: String,
    serverReceiptLabel: String?,
    serverReceiptTone: Tone = if (serverReceiptLabel == null) Tone.UNKNOWN else Tone.SUCCESS,
    ticketTitle: String? = null,
    ticketCompletionEnabled: Boolean = false,
    ticketCompletionMessage: String? = null,
    ticketCompletionPending: Boolean = false,
    onReportTicketCompletion: () -> Unit = {},
    online: Boolean,
    developerModeEnabled: Boolean = false,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareUpload: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewSurvey: () -> Unit,
    historyEntry: TaskRun? = null,
    resultMessage: String? = null,
    onOpenLog: (() -> Unit)? = null,
    savedLog: String? = null,
    onDismissLog: () -> Unit = {},
    uploadJobs: List<UploadJob> = emptyList(),
    ticketCompletionReported: Boolean = false,
    onOpenTransfer: ((String) -> Unit)? = null,
    onVerifyReceipt: ((String) -> Unit)? = null,
    onDeviceWifi: () -> Unit = onDevices,
    onConnectionMethods: () -> Unit = onDevices,
    onOpenHistory: () -> Unit = onBack,
    onTrashRecord: (() -> Unit)? = null,
    historyState: FieldState? = null,
    onUndoDelete: () -> Unit = {},
    onDismissMessage: (String) -> Unit = {},
    mapCard: (@Composable () -> Unit)? = null,
    zoneObservation: ResultZoneObservation? = null,
    zoneRevisionChanged: Boolean = false,
    ticketCompletionFailed: Boolean = false,
    ticketReportTargetKey: String? = null,
    statusStrip: @Composable () -> Unit = {}
) {
    val storedZone = com.example.jetsoncontroller.ui.zone.rememberRunZoneOutput(run?.deviceId ?: historyEntry?.deviceId, run?.runId ?: historyEntry?.runId)
    val observedZone = zoneObservation ?: storedZone?.resultObservation()
    val screenState = rememberSaveableStateHolder()
    val snackbar: @Composable () -> Unit = {
        GeoOperationMessageHost(historyState?.message, onDismissMessage,
            actionLabel = "실행 취소".takeIf { historyState?.undoTrashId != null },
            onAction = onUndoDelete.takeIf { historyState?.undoTrashId != null })
    }
    if (run == null && historyEntry != null) {
        HistoryEntryScreen(historyEntry, deviceName, online, developerModeEnabled, onBack,
            onOpenFiles, onOpenLog, savedLog, onDismissLog, resultMessage, onTrashRecord, snackbar, statusStrip,
            mapCard = { mapCard?.invoke() ?: com.example.jetsoncontroller.ui.map.GeoResultMapCard(null, historyEntry, historyState, observedZone, zoneRevisionChanged) })
        return
    }
    if (developerModeEnabled && savedLog != null) {
        SavedHistoryLogScreen(run?.contextSnapshot?.surveySectionLabel.orEmpty(), savedLog, onDismissLog, statusStrip)
        return
    }
    screenState.SaveableStateProvider("result:${run?.deviceId}:${run?.runId}") {
        val chain = run?.let {
            resultChain(it, uploadJobs, uploadEnabled, uploadDisabledReason, connectionLabel, online,
                !ticketTitle.isNullOrBlank(), ticketCompletionEnabled, ticketCompletionPending,
                ticketCompletionReported, ticketCompletionMessage, ticketCompletionFailed)
        }
        var reportTarget by rememberSaveable(run?.deviceId, run?.runId) { mutableStateOf<String?>(null) }
        val reportFocus = remember { FocusRequester() }
        val receipt = uploadJobs.firstOrNull { it.id == chain?.jobId }
        val reportKey = run?.let { listOf(it.deviceId, it.runId, chain?.jobId, receipt?.targetId,
            receipt?.remoteSessionId, receipt?.verification?.contentSha256, receipt?.verification?.verifiedAt,
            ticketTitle, ticketReportTargetKey).toString() }
        LaunchedEffect(reportKey, chain?.enabled, chain?.action) {
            if (reportTarget != reportKey || chain?.enabled != true || chain.action != ResultAction.REPORT) reportTarget = null
        }
        if (reportTarget != null && run != null) {
            GeoConfirmDialog(
                title = "${ticketTitle.orEmpty()}을 완료로 보고할까요?",
                body = "GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다.",
                confirmLabel = "완료 보고",
                onConfirm = {
                    if (reportTarget == reportKey && chain?.action == ResultAction.REPORT && chain.enabled) {
                        reportTarget = null
                        onReportTicketCompletion()
                    }
                },
                onDismissRequest = { reportTarget = null },
                openerFocusRequester = reportFocus
            )
        }
        FieldDetailFrame("수집 결과", onBack, onTrash = onTrashRecord,
            trashEnabled = online && run?.active != true && historyState?.deletingRunId == null,
            trashDisabledReason = if (!online) "장치에 연결한 뒤 기록을 휴지통으로 옮길 수 있습니다." else "수집 상태를 확인한 뒤 다시 시도하세요.",
            snackbar = snackbar, statusStrip = statusStrip) {
            if (run == null || chain == null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalGeoWindow.current.gutterDp.dp)) {
                    GeoStateBlock(GeoStateContent(GeoStateKind.MISSING,
                        "수집 결과를 찾을 수 없습니다", "수집 기록에서 다시 찾아보세요.", "수집 기록"),
                        fullScreen = true, onAction = onOpenHistory)
                }
                return@FieldDetailFrame
            }
            val c = LocalGeoColors.current
            val gutter = LocalGeoWindow.current.gutterDp.dp
            GeoActionArea(visibilityKey = "${run.deviceId}:${run.runId}", action = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$deviceName · $connectionLabel · ${chain.explanation}",
                        style = LocalGeoType.current.body, color = c.textSecondary)
                    val callbackAvailable = when (chain.action) {
                        ResultAction.TRANSFER -> onOpenTransfer != null
                        ResultAction.VERIFY -> onVerifyReceipt != null
                        else -> true
                    }
                    GeoButton(chain.actionLabel, onClick = {
                        when (chain.action) {
                            ResultAction.SEND -> onPrepareUpload()
                            ResultAction.TRANSFER -> chain.jobId?.let { onOpenTransfer?.invoke(it) }
                            ResultAction.VERIFY -> chain.jobId?.let { onVerifyReceipt?.invoke(it) }
                            ResultAction.REPORT -> reportTarget = reportKey
                            ResultAction.NEXT -> onNewSurvey()
                        }
                }, modifier = Modifier.fillMaxWidth().focusRequester(reportFocus), enabled = chain.enabled && callbackAvailable,
                        actionArea = true)
                    chain.remedy?.let { remedy ->
                        GeoButton(remedy, {
                            when (remedy) {
                                "장치 Wi-Fi 설정" -> onDeviceWifi()
                                "다시 확인" -> onRefresh()
                                else -> onConnectionMethods()
                            }
                        }, style = GeoButtonStyle.Tertiary)
                    }
                }
            }) { inlineAction ->
                LazyColumn(Modifier.fillMaxSize().semantics {
                    customActions = listOf(CustomAccessibilityAction("새로 고침") { onRefresh(); true })
                }, contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = gutter)) {
                            resultMessage?.let { GeoInlineNote(it) }
                            Text(run.contextSnapshot.surveySectionLabel, color = c.text,
                                style = LocalGeoType.current.boardTitle, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(8.dp))
                            Text("${resultTime(run.finishedAt)} 종료 · ${resultDuration(run)} · $deviceName",
                                style = LocalGeoType.current.caption, color = c.textSecondary)
                            Spacer(Modifier.height(32.dp))
                            if (LocalGeoWindow.current.compactHeight) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GeoReceipt(chain.receipt, Modifier.weight(1f))
                                    Column(Modifier.weight(1f)) { mapCard?.invoke() ?: com.example.jetsoncontroller.ui.map.GeoResultMapCard(run, historyEntry, historyState, observedZone, zoneRevisionChanged) }
                                }
                            } else GeoReceipt(chain.receipt)
                            Spacer(Modifier.height(24.dp))
                            ResultMetrics(run, observedZone)
                            if (!LocalGeoWindow.current.compactHeight) {
                                Spacer(Modifier.height(24.dp))
                                mapCard?.invoke() ?: com.example.jetsoncontroller.ui.map.GeoResultMapCard(run, historyEntry, historyState, observedZone, zoneRevisionChanged)
                            }
                            observedZone?.let {
                                Text("구역 밖 기록 ${it.excursions}회 · ${qualityDurationLabel(it.outsideMillis)} · 관찰하지 못한 시간 ${qualityDurationLabel(it.unobservedMillis)}",
                                    color = c.textSecondary, style = LocalGeoType.current.caption)
                                Text("관찰한 시간 기준", color = c.textSecondary, style = LocalGeoType.current.caption)
                            }
                            if (zoneRevisionChanged) GeoInlineNote("수집 구역이 바뀌었습니다. 이 수집은 시작할 때의 구역으로 표시합니다.")
                            GeoSection("저장 폴더") {
                                GeoRow(run.output.path.trimEnd('/').substringAfterLast('/').ifBlank { "미확인" },
                                    onOpenFiles, enabled = online && runOutputStored(run),
                                    disabledReason = if (!online) "장치에 연결한 뒤 저장 폴더를 볼 수 있습니다." else "장치 저장 결과를 확인한 뒤 폴더를 볼 수 있습니다.")
                            }
                            RunQualityEvidence(run.quality, zoneObservation = observedZone,
                                subtitle = "${run.contextSnapshot.surveySectionLabel} · ${resultDuration(run)}")
                            FieldTechnicalInfo(run.runId, buildList {
                                add(GeoTechnicalValue("수집 ID", run.runId))
                                add(GeoTechnicalValue("경로", "${run.output.rootId}/${run.output.path}"))
                                add(GeoTechnicalValue("출력 ID", run.output.outputId))
                                run.exitCode?.let { add(GeoTechnicalValue("종료 코드", it.toString())) }
                                run.output.manifest?.let { manifest ->
                                    add(GeoTechnicalValue("기대 조건 검사", manifest.expectationState))
                                    if (manifest.expected.minFiles > 0) add(GeoTechnicalValue("최소 파일 수",
                                        "${manifest.expected.minFiles}개 · 실제 ${manifest.fileCount}개"))
                                    if (manifest.expected.minBytes > 0) add(GeoTechnicalValue("최소 용량",
                                        "${Formats.size(manifest.expected.minBytes)} · 실제 ${Formats.size(manifest.bytesTotal)}"))
                                    manifest.matchedPatterns.forEach { add(GeoTechnicalValue(it.pattern, "${it.fileCount}개")) }
                                }
                            }, developerModeEnabled, online, onOpenLog)
                        }
                    }
                    item { inlineAction() }
                }
            }
        }
    }
}

@Composable
private fun ResultMetrics(run: PipelineRun, observation: ResultZoneObservation?) {
    val telemetry = verifiedRunTelemetry(run)
    val manifest = run.output.manifest?.takeIf { runOutputStored(run) }
    GeoFigureGrid(listOf(
        GeoFigure("수집 시간", resultDuration(run)),
        GeoFigure("수집 용량", Formats.size(telemetry?.collectedBytes ?: manifest?.bytesTotal)),
        GeoFigure("정밀 위치", precisionMetricLabel(run.quality)),
        GeoFigure("구역 밖 기록", observation?.let { "${it.excursions}회" }, detail = "관찰한 시간 기준")
    ))
}

/** Compatibility for the root while it migrates to the shared receipt line. */
@Composable
internal fun RunReceiptStrip(cells: List<RunReceiptCell>, modifier: Modifier = Modifier) {
    fun cell(index: Int) = cells.getOrNull(index)?.let { GeoReceiptItem(it.value, it.tone) } ?: GeoReceiptItem()
    GeoReceiptLine(GeoReceiptState(cell(0), cell(1), cell(2)), modifier)
}

/** A FINAL marker is accepted only when the manifest and upload identity belong to this run. */
internal fun runOutputStored(run: PipelineRun?): Boolean {
    if (run == null || run.active || run.finishedAt == null) return false
    val output = run.output
    val manifest = output.manifest ?: return false
    val context = run.uploadContext
    return output.manifestState.equals("FINAL", ignoreCase = true) &&
        manifest.runId == run.runId && output.rootId.isNotBlank() && output.path.isNotBlank() &&
        context.runId == run.runId && context.deviceId.equals(run.deviceId, ignoreCase = true) &&
        context.pipelineId == run.pipelineId && context.sourceRevision == run.sourceRevision &&
        context.configSha256 == run.configRevision && context.outputId == output.outputId
}
