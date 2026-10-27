package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.ui.storage.R7InlineNote as GeoInlineNote
import com.example.jetsoncontroller.ui.storage.R7Section as GeoSection
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.transfers.*
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant

@Composable
fun UploadConfirmScreen(
    rootId: String,
    path: String,
    targets: List<UploadTarget>,
    sourceSummary: UploadSourceSummary?,
    linkedRunId: String? = null,
    linkedRun: PipelineRun? = null,
    isCalculatingSource: Boolean,
    serverUploadEnabled: Boolean,
    serverUploadDisabledReason: String?,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onManageTargets: () -> Unit,
    onConfirm: (String) -> Unit,
    deviceId: String? = null,
    deviceName: String = "선택된 장치 없음",
    developerModeEnabled: Boolean = false,
    onConnectionRemedy: () -> Unit = onBack,
    ongoingRun: @Composable () -> Unit = {}
) {
    var targetId by rememberSaveable(deviceId, rootId, path, linkedRunId) { mutableStateOf<String?>(null) }
    val selectedTarget = targets.firstOrNull { it.id == targetId } ?: targets.firstOrNull()
    val summary = sourceSummary?.takeIf { it.matchesUploadSource(rootId, path) }
    val runValid = linkedRunId == null || (linkedRun != null && linkedRun.runId == linkedRunId &&
        linkedRunMatchesSource(linkedRun, deviceId, rootId, path) && linkedRun.output.manifestState == "FINAL")
    val ready = serverUploadEnabled && selectedTarget != null && summary != null && runValid &&
        !isLoading && !isCalculatingSource
    val reason = when {
        !serverUploadEnabled -> serverUploadDisabledReason ?: "장치 연결을 확인하세요."
        summary == null -> "보낼 자료를 확인하고 있습니다."
        !runValid -> "수집 결과를 다시 확인하세요."
        else -> "전송을 준비하고 있습니다."
    }
    TransferPage("전송 확인", onBack, primaryAction = GeoButtonAction("전송 시작",
        { selectedTarget?.let { onConfirm(it.id) } }, enabled = ready,
        busy = isLoading || isCalculatingSource, busyLabel = "확인 중", disabledReason = reason)
        .takeIf { targets.isNotEmpty() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = transferGutter, vertical = 16.dp)) {
                item { ongoingRun() }
                item {
                    TransferHeading(linkedRun?.contextSnapshot?.surveySectionLabel ?: summary?.folderName
                        ?: path.substringAfterLast('/').ifBlank { "수집 자료" },
                        listOfNotNull(linkedRun?.contextSnapshot?.surveyProjectLabel, summary?.folderName).joinToString(" · "))
                    GeoSection("보낼 자료", first = true, note = "장치 원본은 그대로 보관됩니다.") {
                        GeoCopyableValue(path, label = "원본 경로")
                        GeoPropertyList(listOf("파일", "용량", "보내는 장치", "수집")) {
                        if (isCalculatingSource) GeoSkeleton("보낼 자료를 확인하고 있습니다.", kind = GeoSkeletonKind.Rows)
                        GeoFactRow("파일", Formats.itemCount(summary?.filesTotal?.toLong()), numeric = true)
                        GeoFactRow("용량", Formats.size(summary?.bytesTotal), numeric = true)
                        GeoFactRow("보내는 장치", deviceName)
                        linkedRun?.let { run -> GeoFactRow("수집", Formats.time(run.startedAt.toTransferInstant())) }
                        }
                    }
                    GeoSection("받는 서버") {
                        if (isLoading) GeoSkeleton("받는 서버를 확인하고 있습니다.", kind = GeoSkeletonKind.Rows)
                        when (targets.size) {
                            0 -> {
                                GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "받을 서버가 없습니다",
                                    "관리자에게 받는 서버 설정을 요청하세요."))
                                if (developerModeEnabled) GeoRow("업로드 서버", onManageTargets)
                            }
                            1 -> GeoFactRow("받는 서버", transferTargetLabel(targets.single()))
                            else -> targets.forEach { target ->
                                GeoRadioRow(transferTargetLabel(target), selectedTarget?.id == target.id, { targetId = target.id })
                            }
                        }
                    }
                    error?.let {
                        GeoInlineNote("전송 준비 정보를 확인하지 못했습니다. 다시 시도하세요.",
                            actionLabel = "다시 확인", onAction = onRefresh)
                    }
                    GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                    GeoReadinessLine(if (serverUploadEnabled) "$deviceName · 같은 네트워크 · 지금 보낼 수 있습니다." else reason,
                        if (serverUploadEnabled) Tone.INFO else Tone.WARNING)
                    if (!serverUploadEnabled) GeoButton("장치 Wi-Fi 설정", onConnectionRemedy,
                        style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                }
            }
    }
}

private fun String.toTransferInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
