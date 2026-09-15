package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.BorderStroke
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.AdaptiveContent
import com.example.jetsoncontroller.ui.components.OperationMessageHost
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismissMessage: (String) -> Unit = {}
) {
    var showCancelDialog by remember(job?.id, deviceDeletionEnabled) { mutableStateOf(false) }
    var showDeleteDialog by remember(job?.id, deviceDeletionEnabled) { mutableStateOf(false) }
    val active = job?.state?.let(::isActiveUploadState) == true
    val deleteSourceAllowed = job?.state == UploadJobState.COMPLETED &&
        verification?.matched == true && verification.deletionAllowed &&
        job.deletionEligible && deviceDeletionEnabled && !isLoading

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("업로드를 취소할까요?") },
            text = { Text("이미 전송된 일부 데이터는 수신 대상에 남아 있을 수 있습니다.") },
            confirmButton = {
                Button(enabled = deviceDeletionEnabled, onClick = {
                    showCancelDialog = false
                    onCancel()
                }) { Text("업로드 취소") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("계속 업로드") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("장치의 원본을 휴지통으로 옮길까요?") },
            text = {
                Text("서버 데이터와 다시 대조한 뒤 장치의 원본 폴더를 휴지통으로 옮깁니다. 이동이 확인되면 복원할 수 있습니다.")
            },
            confirmButton = {
                Button(enabled = deleteSourceAllowed, onClick = {
                    showDeleteDialog = false
                    onDeleteSource()
                }) { Text("확인 후 휴지통 이동") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        snackbarHost = { OperationMessageHost(message, onDismissMessage) },
        topBar = {
            TopAppBar(
                title = { Text(if (job?.state == UploadJobState.COMPLETED) "전송 결과" else "전송") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "홈으로")
                    }
                    IconButton(onClick = onFiles) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "파일로")
                    }
                }
            )
        }
    ) { paddingValues ->
        AdaptiveContent(Modifier.fillMaxSize().padding(paddingValues), maxWidth = 760.dp) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (job == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("업로드 작업을 준비하고 있습니다")
                        } else {
                            InlineMessage(message = error, isError = true)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack) { Text("돌아가기") }
                        }
                    }
                }
            } else {
                val progress = when {
                    job.state == UploadJobState.COMPLETED -> 1f
                    (job.bytesTotal ?: 0) > 0 ->
                        ((job.bytesTransferred ?: 0).toFloat() / job.bytesTotal!!.toFloat())
                            .coerceIn(0f, 1f)
                    else -> 0f
                }
                val icon = when (job.state) {
                    UploadJobState.COMPLETED -> Icons.Default.CheckCircle
                    UploadJobState.FAILED -> Icons.Default.Error
                    UploadJobState.CANCELLED -> Icons.Default.Cancel
                    else -> Icons.Default.CloudUpload
                }
                val color = when (job.state) {
                    UploadJobState.COMPLETED -> MaterialTheme.colorScheme.primary
                    UploadJobState.FAILED -> MaterialTheme.colorScheme.error
                    UploadJobState.CANCELLED -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.secondary
                }

                Spacer(Modifier.height(24.dp))
                Icon(icon, contentDescription = null, tint = color)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        job.state == UploadJobState.COMPLETED && verification?.matched == true ->
                            "서버 수신이 확인됐습니다"
                        job.state == UploadJobState.COMPLETED && verification?.matched == false ->
                            "서버 수신을 확인하지 못했습니다"
                        job.state == UploadJobState.COMPLETED -> "전송이 완료됐습니다"
                        else -> stateLabel(job.state)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = job.folderName ?: job.sourceName
                        ?: job.relativePath.substringAfterLast('/').ifEmpty { "/" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                job.context?.let { context ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (developerModeEnabled) {
                            "조사 ${context.surveyProjectId} · 구간 ${context.surveySectionId}\nRun ${context.runId}"
                        } else {
                            "조사 실행과 연결된 전송"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                job.remoteSessionId?.takeIf { developerModeEnabled }?.let { sessionId ->
                    Spacer(Modifier.height(6.dp))
                    Text("수신 세션 $sessionId", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(32.dp))
                val hasKnownTotal = (job.bytesTotal ?: 0) > 0
                if (hasKnownTotal) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (active) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (hasKnownTotal) "${(progress * 100).toInt()}%" else "진행률 미확인",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        uploadProgressBytesLabel(job.bytesTransferred, job.bytesTotal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (active) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            (job.throughputBytesPerSecond ?: 0L).takeIf { it > 0L }
                                ?.let { "${formatSize(it)}/초" },
                            job.etaSeconds?.takeIf { it >= 0L }?.let { "예상 ${formatEta(it)}" }
                        ).joinToString(" · ").ifEmpty { "전송 속도와 예상 시간을 계산 중입니다" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(28.dp))
                job.currentFile?.takeIf { developerModeEnabled }?.let { file ->
                    Text("현재 파일", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (job.filesTotal != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "파일 ${job.filesTransferred ?: 0} / ${job.filesTotal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (job.state == UploadJobState.COMPLETED) {
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.material3.Surface(
                        color = LocalGeoColors.current.surface,
                        contentColor = LocalGeoColors.current.ink,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (verification?.matched) {
                                        true -> Icons.Default.CheckCircle
                                        false -> Icons.Default.Error
                                        null -> Icons.Default.CloudUpload
                                    },
                                    contentDescription = null,
                                    tint = when (verification?.matched) {
                                        true -> LocalGeoColors.current.success
                                        false -> LocalGeoColors.current.warning
                                        null -> LocalGeoColors.current.unknown
                                    }
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    if (verification?.matched == true) "서버 수신 확인" else "서버 수신 확인 필요",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            targetLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            val receiptFileCount = job.filesTotal ?: verification?.filesTotal
                            val receiptBytes = job.bytesTotal ?: verification?.bytesTotal
                            Text(
                                listOf(
                                    receiptFileCount?.let { "${it}개 파일" } ?: "개수 미확인",
                                    receiptBytes?.let(::formatSize) ?: "용량 미확인"
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalGeoColors.current.muted
                            )
                            Text(
                                uploadSourceLifecycleLabel(job),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalGeoColors.current.muted
                            )
                        }
                    }
                }

                job.errorMessage?.let { technicalError ->
                    Spacer(Modifier.height(20.dp))
                    InlineMessage(
                        message = if (developerModeEnabled) technicalError
                            else "업로드에 실패했습니다. 연결을 확인하고 다시 시도하세요.",
                        isError = true
                    )
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(
                        message = if (developerModeEnabled) it
                            else "전송 상태를 확인하지 못했습니다. 다시 시도하세요.",
                        isError = true
                    )
                }
                if (job.sourceDeleted) {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(message = "장치의 업로드 원본이 삭제되었습니다.", isError = false)
                }
                if (job.sourceDeletionState == "PURGING") {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(message = "장치 원본을 영구 삭제하는 중입니다. 완료될 때까지 복원할 수 없습니다.", isError = false)
                }
                if (job.sourceRecoverable && job.sourceTrashId != null) {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(message = "장치 원본이 휴지통에 있으며 복원할 수 있습니다.", isError = false)
                }
                val serverMutationActionVisible = job.state == UploadJobState.FAILED ||
                    (
                        job.state == UploadJobState.COMPLETED &&
                            !job.sourceDeleted &&
                            verification?.matched == false
                    )
                if (
                    serverMutationActionVisible && !serverMutationEnabled &&
                    !serverMutationDisabledReason.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(
                        message = serverMutationDisabledReason,
                        isError = false
                    )
                }

                Spacer(Modifier.height(28.dp))
                if (active) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = deviceDeletionEnabled && !isLoading
                    ) {
                        Text("업로드 취소")
                    }
                } else if (job.state == UploadJobState.FAILED) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && serverMutationEnabled
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (isLoading) "준비 중" else "다시 시도")
                    }
                } else if (job.state == UploadJobState.COMPLETED) {
                    if (verification?.matched != true) {
                        OutlinedButton(
                            onClick = onVerify,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text(if (isLoading) "확인 중" else "서버 수신 확인")
                        }
                    } else {
                        Button(onClick = onServerData, modifier = Modifier.fillMaxWidth()) {
                            Text("서버 파일 보기")
                        }
                    }
                    if (verification?.matched == false) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && serverMutationEnabled
                        ) {
                            Text(if (isLoading) "준비 중" else "다시 업로드")
                        }
                    }
                    if (job.sourceRecoverable && job.sourceTrashId != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRestoreSource,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && deviceDeletionEnabled
                        ) { Text("장치 원본 복원") }
                    } else if (verification?.matched == true && verification.deletionAllowed && job.deletionEligible &&
                        job.sourceDeletionState !in setOf("PURGING", "PURGED")) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && deviceDeletionEnabled,
                            border = BorderStroke(1.dp, LocalGeoColors.current.dangerBorder),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = LocalGeoColors.current.danger
                            )
                        ) {
                            Text("확인된 장치 원본 휴지통 이동")
                        }
                    }
                } else {
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("완료")
                    }
                }
            }
        }
        }
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
    UploadJobState.UPLOADING -> "업로드 중"
    UploadJobState.COMPLETED -> "업로드 완료"
    UploadJobState.FAILED -> "업로드 실패"
    UploadJobState.CANCELLED -> "업로드 취소됨"
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}
