package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.ui.theme.Button
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

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
    deviceName: String = "선택된 장비 없음"
) {
    var selectedTargetId by rememberSaveable(deviceId, rootId, path) { mutableStateOf<String?>(null) }
    val matchingSourceSummary = sourceSummary?.takeIf {
        it.matchesUploadSource(rootId, path)
    }
    LaunchedEffect(targets) {
        if (targets.none { it.id == selectedTargetId }) {
            selectedTargetId = targets.firstOrNull()?.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("전송 확인 · $deviceName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "서버 목록 새로고침")
                    }
                    IconButton(onClick = onManageTargets) {
                        Icon(Icons.Default.Dns, contentDescription = "업로드 서버 관리")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(shape = MaterialTheme.shapes.small,
                    onClick = { selectedTargetId?.let(onConfirm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp).heightIn(min = 52.dp),
                    enabled = serverUploadEnabled && selectedTargetId != null &&
                        matchingSourceSummary != null && !isLoading && !isCalculatingSource &&
                        (linkedRunId == null || linkedRun != null)
                ) {
                    if (isLoading || isCalculatingSource) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        when {
                            isCalculatingSource -> "용량 계산 중"
                            isLoading -> "준비 중"
                            else -> "선택한 폴더 1개 전송"
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState())
        ) {
            Text("대상 장비: $deviceName\n${deviceId.orEmpty()}\n폴더 전체를 전송하며 원본은 유지합니다. 대상 서버 접근과 인증은 전송 시 확인합니다.",
                modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            Surface(color = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.sectionSoft) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        text = "업로드할 위치",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = matchingSourceSummary?.folderName
                            ?: path.substringAfterLast('/').ifEmpty { "/" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isCalculatingSource -> "폴더 용량 계산 중…"
                            matchingSourceSummary != null ->
                                "${matchingSourceSummary.filesTotal}개 파일 · " +
                                    formatSize(matchingSourceSummary.bytesTotal)
                            else -> "저장소 $rootId · 용량 미확인"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (linkedRunId != null) {
                Surface(
                    color = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.sectionRaised,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("조사 실행 연결", style = MaterialTheme.typography.titleMedium)
                        if (linkedRun == null) {
                            Text("장비가 보증한 실행·결과 컨텍스트를 확인하는 중입니다.",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("${linkedRun.contextSnapshot.surveyProjectLabel} · ${linkedRun.contextSnapshot.surveySectionLabel}")
                            Text("Run ${linkedRun.runId}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                linkedRun.output.manifest?.let { manifest ->
                                    "결과 ${manifest.fileCount}개 · ${formatSize(manifest.bytesTotal)} · 기대 결과 ${expectationLabel(manifest.expectationState)}"
                                } ?: "결과 manifest ${linkedRun.output.manifestState}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("전송 시 장비가 반환한 업로드 컨텍스트를 그대로 사용합니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Text(
                text = "업로드 대상",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            if (!serverUploadEnabled && !serverUploadDisabledReason.isNullOrBlank()) {
                InlineMessage(
                    message = serverUploadDisabledReason,
                    isError = false,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            error?.let {
                InlineMessage(
                    message = it,
                    isError = true,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (targets.isEmpty() && !isLoading) {
                EmptyState(
                    title = "업로드 대상이 없습니다",
                    message = "현재 선택할 수 있는 업로드 서버가 없습니다.",
                    actionLabel = "서버 관리",
                    onAction = onManageTargets
                )
            } else {
                targets.forEach { target ->
                    ListItem(
                        headlineContent = { Text(target.label) },
                        supportingContent = {
                            Text(
                                target.baseUrl ?: target.id,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null)
                        },
                        trailingContent = {
                            RadioButton(
                                selected = selectedTargetId == target.id,
                                onClick = null,
                                enabled = serverUploadEnabled
                            )
                        },
                        modifier = Modifier.selectable(selected = selectedTargetId == target.id, enabled = serverUploadEnabled, role = Role.RadioButton, onClick = { selectedTargetId = target.id })
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

private fun expectationLabel(state: String): String = when (state.uppercase()) {
    "SATISFIED" -> "충족"
    "NOT_SATISFIED" -> "미충족"
    else -> "확인 중"
}
