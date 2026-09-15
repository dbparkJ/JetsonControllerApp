package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

@OptIn(ExperimentalMaterial3Api::class)
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
    deviceName: String = "선택된 장비 없음",
    developerModeEnabled: Boolean = false
) {
    val colors = LocalGeoColors.current
    var selectedTargetId by rememberSaveable(deviceId, rootId, path) { mutableStateOf<String?>(null) }
    val summary = sourceSummary?.takeIf { it.matchesUploadSource(rootId, path) }
    val selectedTarget = targets.firstOrNull { it.id == selectedTargetId }
    val ready = serverUploadEnabled && selectedTarget != null && summary != null &&
        !isLoading && !isCalculatingSource && (linkedRunId == null || linkedRun != null)

    LaunchedEffect(targets) {
        if (targets.none { it.id == selectedTargetId }) selectedTargetId = targets.firstOrNull()?.id
    }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("전송 확인", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                } },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "서버 목록 새로고침")
                    }
                    if (developerModeEnabled) IconButton(onClick = onManageTargets) {
                        Icon(Icons.Default.Dns, contentDescription = "업로드 서버 관리")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = colors.surface, contentColor = colors.ink) {
                AdaptiveContent(maxWidth = 760.dp) {
                    Button(
                        shape = MaterialTheme.shapes.small,
                        onClick = { selectedTargetId?.let(onConfirm) },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(vertical = GeoSpace.md).heightIn(min = GeoSize.primaryAction)
                    ) {
                        if (isLoading || isCalculatingSource) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(GeoSpace.sm))
                        Text(when {
                            isCalculatingSource -> "용량 계산 중"
                            isLoading -> "준비 중"
                            else -> "전송 시작"
                        })
                    }
                }
            }
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 760.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = GeoSpace.sm, bottom = GeoSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.section)
            ) {
                item { Text("보낼 자료를 확인하세요", style = MaterialTheme.typography.headlineMedium) }
                item {
                    Surface(
                        color = colors.surface,
                        contentColor = colors.ink,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(GeoSpace.xxl), verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                                Icon(
                                    if (summary?.sourceType.equals("file", true)) Icons.AutoMirrored.Filled.InsertDriveFile
                                    else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        summary?.folderName ?: path.substringAfterLast('/').ifEmpty { "수집 자료" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        when {
                                            isCalculatingSource -> "자료를 확인하는 중입니다"
                                            summary != null -> "${summary.filesTotal}개 파일 · ${formatSize(summary.bytesTotal)}"
                                            else -> "용량 미확인"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.border)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("총 용량", Modifier.weight(1f), color = colors.muted)
                                Text(summary?.let { formatSize(it.bytesTotal) } ?: "미확인",
                                    style = MaterialTheme.typography.headlineMedium)
                            }
                            Text(
                                if (developerModeEnabled) "보내는 장치 $deviceName · 저장소 $rootId"
                                else "보내는 장치 $deviceName",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                    }
                }
                if (linkedRunId != null) item {
                    Surface(color = colors.surface, contentColor = colors.ink,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(GeoSpace.lg), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                            Text("조사 실행 연결", style = MaterialTheme.typography.titleMedium)
                            if (linkedRun == null) Text("조사 결과를 확인하는 중입니다.", color = colors.muted)
                            else {
                                Text("${linkedRun.contextSnapshot.surveyProjectLabel} · ${linkedRun.contextSnapshot.surveySectionLabel}")
                                linkedRun.output.manifest?.let {
                                    Text("결과 ${it.fileCount}개 · ${formatSize(it.bytesTotal)}",
                                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                                }
                                if (developerModeEnabled) Text("Run ${linkedRun.runId}",
                                    style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            }
                        }
                    }
                }
                if (!serverUploadEnabled && !serverUploadDisabledReason.isNullOrBlank()) item {
                    AppBanner(serverUploadDisabledReason, StatusTone.WARNING)
                }
                error?.let { detail -> item {
                    AppBanner(
                        if (developerModeEnabled) detail else "전송 준비 정보를 확인하지 못했습니다. 다시 시도하세요.",
                        StatusTone.ERROR,
                        actionLabel = "다시 확인",
                        onAction = onRefresh
                    )
                } }
                item { Text("받는 서버", style = com.example.jetsoncontroller.ui.theme.GeoType.eyebrow, color = colors.muted) }
                if (targets.isEmpty() && !isLoading) item {
                    EmptyState(
                        title = "선택할 서버가 없습니다",
                        message = "관리자에게 전송 서버 설정을 요청해 주세요.",
                        actionLabel = "서버 관리".takeIf { developerModeEnabled },
                        onAction = onManageTargets
                    )
                } else items(targets, key = { it.id }) { target ->
                    val selected = selectedTargetId == target.id
                    Surface(
                        color = if (selected) colors.brandSoft else colors.surface,
                        contentColor = colors.ink,
                        border = BorderStroke(GeoSize.hairline, if (selected) colors.primary else colors.border),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = selected,
                            enabled = serverUploadEnabled,
                            role = Role.RadioButton,
                            onClick = { selectedTargetId = target.id }
                        )
                    ) {
                        Row(Modifier.padding(GeoSpace.lg), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = colors.primary)
                            Column(Modifier.weight(1f)) {
                                Text(target.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (developerModeEnabled) target.baseUrl ?: target.id else "등록된 서버",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            RadioButton(selected = selected, onClick = null, enabled = serverUploadEnabled)
                        }
                    }
                }
                item { Text("장치 원본은 그대로 보관됩니다.",
                    style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            }
        }
    }
}
