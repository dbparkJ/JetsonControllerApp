package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunResultScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    run: PipelineRun?,
    uploadEnabled: Boolean,
    uploadDisabledReason: String,
    serverReceiptLabel: String?,
    serverReceiptTone: StatusTone = if (serverReceiptLabel == null) StatusTone.UNKNOWN else StatusTone.SUCCESS,
    online: Boolean,
    developerModeEnabled: Boolean = false,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareUpload: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewSurvey: () -> Unit
) {
    val c = LocalGeoColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    val manifest = run?.output?.manifest
    val stored = runOutputStored(run)
    val finishedTone = when {
        run == null -> StatusTone.UNKNOWN
        run.active || run.finishedAt == null -> StatusTone.PENDING
        run.exitCode != null && run.exitCode != 0 -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val storedTone = when {
        stored -> StatusTone.SUCCESS
        run?.output?.manifestState.equals("PENDING", true) || run?.output?.manifestState.equals("RUNNING", true) -> StatusTone.PENDING
        run?.output?.manifestState.equals("FAILED", true) || run?.output?.manifestState.equals("ERROR", true) -> StatusTone.WARNING
        else -> StatusTone.UNKNOWN
    }
    val canUpload = run != null && stored && uploadEnabled

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text("수집 결과") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "수집 결과 메뉴")
                    }
                    DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("장치 선택") },
                            onClick = { menuExpanded = false; onDevices() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (unreadCount > 0) "알림 ${unreadCount}개" else "알림") },
                            onClick = { menuExpanded = false; onAlerts() }
                        )
                        DropdownMenuItem(
                            text = { Text("결과 새로고침") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = online,
                            onClick = { menuExpanded = false; onRefresh() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas, titleContentColor = c.ink)
            )
        },
        bottomBar = {
            Surface(color = c.surface) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
                ) {
                    GeoPrimaryAction(
                        label = "파일 전송 준비",
                        onClick = onPrepareUpload,
                        enabled = canUpload,
                        icon = Icons.Default.CloudUpload
                    )
                    com.example.jetsoncontroller.ui.theme.TextButton(
                        onClick = onNewSurvey,
                        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
                    ) { Text("다음 구간 수집 준비") }
                }
            }
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1000.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = GeoSpace.sm, bottom = GeoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
            ) {
                if (run == null) {
                    item {
                        GeoStateBlock(
                            title = "확인할 수집 결과가 없습니다",
                            impact = "최근 수집 결과를 다시 확인하거나 수집 이력에서 찾아보세요.",
                            tone = StatusTone.UNKNOWN,
                            primaryLabel = "다시 확인",
                            onPrimary = onRefresh,
                            secondaryLabel = "수집 이력 열기",
                            onSecondary = onBack
                        )
                    }
                    return@LazyColumn
                }

                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stack = maxWidth < 600.dp || LocalDensity.current.fontScale > 1.3f
                        val summary: @Composable ColumnScope.() -> Unit = {
                            ResultHero(run, finishedTone, deviceName)
                            Spacer(Modifier.height(GeoSpace.xl))
                            ResultMetrics(run)
                            Spacer(Modifier.height(GeoSpace.lg))
                            RunQualityEvidence(run.quality)
                            Spacer(Modifier.height(GeoSpace.lg))
                            ReceiptCard(
                                run = run,
                                manifest = manifest,
                                finishedTone = finishedTone,
                                storedTone = storedTone,
                                serverReceiptLabel = serverReceiptLabel,
                                serverReceiptTone = serverReceiptTone,
                                developerModeEnabled = developerModeEnabled
                            )
                        }
                        val actions: @Composable ColumnScope.() -> Unit = {
                            CollectedFilesAction(run, stored, onOpenFiles)
                            if (!online) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                AppBanner(
                                    "장치 연결이 끊겼습니다. 표시된 결과는 마지막으로 확인한 값입니다.",
                                    StatusTone.UNKNOWN,
                                    actionLabel = "다시 연결",
                                    onAction = onDevices
                                )
                            }
                            if (!canUpload) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                Text(
                                    when {
                                        !stored -> "장치 저장 결과를 확인한 뒤 파일을 전송할 수 있습니다."
                                        !uploadEnabled -> uploadDisabledReason
                                        else -> "파일 전송 조건을 확인해 주세요."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.muted
                                )
                            }
                            if (developerModeEnabled) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                TechnicalResultDetails(run, manifest)
                            }
                        }
                        if (stack) {
                            Column {
                                summary()
                                Spacer(Modifier.height(GeoSpace.xl))
                                actions()
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                                Column(Modifier.weight(1.35f), content = summary)
                                Column(Modifier.weight(1f), content = actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMetrics(run: PipelineRun) {
    val telemetry = verifiedRunTelemetry(run)
    val manifest = run.output.manifest?.takeIf { runOutputStored(run) }
    val duration = runDurationLabel(telemetry?.durationMillis)
    val bytes = runBytesLabel(telemetry?.collectedBytes ?: manifest?.bytesTotal)
    val precision = precisionMetricLabel(run.quality)
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(20.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(GeoSpace.xl)) {
            val stack = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    ResultMetric("수집 시간", duration, Modifier.fillMaxWidth())
                    ResultMetric("수집 용량", bytes, Modifier.fillMaxWidth())
                    ResultMetric("정밀 위치", precision, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    ResultMetric("수집 시간", duration, Modifier.weight(1f))
                    ResultMetric("수집 용량", bytes, Modifier.weight(1f))
                    ResultMetric("정밀 위치", precision, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = GeoType.numeric, color = c.ink)
    }
}

@Composable
private fun ResultHero(run: PipelineRun, tone: StatusTone, deviceName: String) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(tone)
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Icon(visuals.icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(44.dp))
        Text(
            when (tone) {
                StatusTone.SUCCESS -> "수집을 마쳤습니다"
                StatusTone.PENDING -> "수집 종료를 확인하고 있습니다"
                StatusTone.WARNING, StatusTone.ERROR -> "수집 종료를 확인했습니다"
                else -> "수집 결과를 확인해 주세요"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink
        )
        Text(run.contextSnapshot.surveySectionLabel, style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text(
            listOfNotNull(run.finishedAt?.takeIf { it.isNotBlank() }, deviceName).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
    }
}

@Composable
private fun ReceiptCard(
    run: PipelineRun,
    manifest: PipelineOutputManifest?,
    finishedTone: StatusTone,
    storedTone: StatusTone,
    serverReceiptLabel: String?,
    serverReceiptTone: StatusTone,
    developerModeEnabled: Boolean
) {
    val finishedDetail = when (finishedTone) {
        StatusTone.SUCCESS -> "장치에서 실행 종료를 확인했습니다."
        StatusTone.PENDING -> "장치의 종료 응답을 기다리고 있습니다."
        StatusTone.WARNING, StatusTone.ERROR -> if (developerModeEnabled && run.exitCode != null) {
            "정상적으로 끝나지 않았습니다. exit ${run.exitCode}"
        } else "정상적으로 끝나지 않았습니다."
        else -> "장치의 종료 결과를 확인하지 못했습니다."
    }
    val storedDetail = when (storedTone) {
        StatusTone.SUCCESS -> manifest?.let { "${it.fileCount}개 파일 · ${formatBytes(it.bytesTotal)}" } ?: "저장 확인됨"
        StatusTone.PENDING -> "장치에서 파일 목록을 확인하고 있습니다."
        StatusTone.WARNING, StatusTone.ERROR -> "장치 저장 결과를 다시 확인해 주세요."
        else -> "장치에 저장되었는지 아직 확인하지 못했습니다."
    }
    val serverTitle = when (serverReceiptTone) {
        StatusTone.SUCCESS -> "서버 수신 확인"
        StatusTone.PENDING -> "서버 수신 확인 중"
        StatusTone.WARNING, StatusTone.ERROR -> "서버 수신 확인 필요"
        else -> "서버 수신 미확인"
    }
    val serverDetail = serverReceiptLabel ?: when (serverReceiptTone) {
        StatusTone.PENDING -> "서버의 수신 결과를 확인하고 있습니다."
        StatusTone.WARNING, StatusTone.ERROR -> "서버의 수신 결과를 다시 확인해 주세요."
        else -> "서버의 수신 결과를 확인해 주세요."
    }

    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(GeoSpace.xl)) {
            ReceiptRow(statusVisuals(finishedTone).icon, finishedTone, "수집 종료 확인", finishedDetail)
            HorizontalDivider(Modifier.padding(vertical = GeoSpace.lg), color = c.border)
            ReceiptRow(Icons.Default.Storage, storedTone, storedTitle(storedTone), storedDetail)
            HorizontalDivider(Modifier.padding(vertical = GeoSpace.lg), color = c.border)
            ReceiptRow(Icons.Default.Cloud, serverReceiptTone, serverTitle, serverDetail)
        }
    }
}

private fun storedTitle(tone: StatusTone): String = when (tone) {
    StatusTone.SUCCESS -> "장치 저장 완료"
    StatusTone.PENDING -> "장치 저장 확인 중"
    StatusTone.WARNING, StatusTone.ERROR -> "장치 저장 확인 필요"
    else -> "장치 저장 미확인"
}

@Composable
private fun ReceiptRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: StatusTone,
    title: String,
    detail: String
) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(tone)
    Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(GeoSize.iconMd))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun CollectedFilesAction(run: PipelineRun, stored: Boolean, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(onClick = onClick, enabled = stored, color = c.surface, contentColor = c.ink, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(GeoSpace.lg), verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
            Text("저장 폴더", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(
                if (stored) run.output.path.substringAfterLast('/').ifBlank { "폴더 확인 중" } else "저장 확인 중",
                style = MaterialTheme.typography.titleMedium
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = c.primary)
                Text(
                    if (stored) "수집한 파일 보기" else "파일 목록 확인 대기",
                    Modifier.padding(start = GeoSpace.md).weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (stored) c.primary else c.muted
                )
                Icon(Icons.Default.ChevronRight, null, tint = if (stored) c.primary else c.muted)
            }
        }
    }
}

@Composable
private fun TechnicalResultDetails(run: PipelineRun, manifest: PipelineOutputManifest?) {
    GeoSection {
        GeoSectionHeader("결과 세부 정보", eyebrow = "개발자 모드")
        GeoIdentifier("경로", "${run.output.rootId}/${run.output.path}")
        GeoIdentifier("Run", run.runId)
        GeoIdentifier("Output", run.output.outputId)
        manifest?.let { ExpectationSection(it) }
    }
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

@Composable
private fun ExpectationSection(manifest: PipelineOutputManifest) {
    val c = LocalGeoColors.current
    val expected = manifest.expected
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text("기대 조건 검사 · ${manifest.expectationState}", style = MaterialTheme.typography.titleSmall)
        if (expected.minFiles > 0) Text("최소 파일 수 ${expected.minFiles}개 · 실제 ${manifest.fileCount}개", color = c.muted)
        if (expected.minBytes > 0) Text("최소 용량 ${formatBytes(expected.minBytes)} · 실제 ${formatBytes(manifest.bytesTotal)}", color = c.muted)
        manifest.matchedPatterns.forEach { Text("${it.pattern} · ${it.fileCount}개", color = c.muted) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
