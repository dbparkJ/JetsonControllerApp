package com.example.jetsoncontroller.ui.upload

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(
    job: UploadJob?,
    isLoading: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    val active = job?.state in setOf(
        UploadJobState.QUEUED,
        UploadJobState.SCANNING,
        UploadJobState.UPLOADING
    )

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("업로드를 취소할까요?") },
            text = { Text("이미 전송된 일부 데이터는 수신 대상에 남아 있을 수 있습니다.") },
            confirmButton = {
                Button(onClick = {
                    showCancelDialog = false
                    onCancel()
                }) { Text("업로드 취소") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("계속 업로드") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
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
                    text = stateLabel(job.state),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = job.relativePath.ifEmpty { "/" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(32.dp))
                if ((job.bytesTotal ?: 0) > 0 || job.state == UploadJobState.COMPLETED) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatSize(job.bytesTransferred ?: 0)} / ${formatSize(job.bytesTotal ?: 0)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(28.dp))
                job.currentFile?.let { file ->
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

                job.errorMessage?.let {
                    Spacer(Modifier.height(20.dp))
                    InlineMessage(message = it, isError = true)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    InlineMessage(message = it, isError = true)
                }

                Spacer(Modifier.weight(1f))
                if (active) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("업로드 취소")
                    }
                } else if (job.state == UploadJobState.FAILED) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
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
                } else {
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("완료")
                    }
                }
            }
        }
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
