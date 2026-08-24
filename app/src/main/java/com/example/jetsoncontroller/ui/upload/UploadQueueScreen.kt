package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadQueueScreen(
    queue: List<UploadJob>,
    targets: List<UploadTarget>,
    isLoading: Boolean,
    error: String?,
    message: String? = null,
    onRefresh: () -> Unit,
    onManageTargets: () -> Unit,
    onJobClick: (UploadJob) -> Unit,
    onDeleteJob: (UploadJob) -> Unit,
    onBack: () -> Unit
) {
    val targetLabels = targets.associate { it.id to it.label }
    var pendingDeletion by remember { mutableStateOf<UploadJob?>(null) }
    pendingDeletion?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("업로드 기록을 삭제할까요?") },
            text = {
                Text(
                    "선택한 완료·실패·취소 기록만 목록에서 삭제합니다. " +
                        "장치의 원본과 서버에 업로드된 데이터는 삭제되지 않습니다."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletion = null
                        onDeleteJob(job)
                    }
                ) { Text("기록 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("취소") }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 확인") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onManageTargets) {
                        Icon(Icons.Default.Dns, contentDescription = "업로드 서버 관리")
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                error?.let {
                    item {
                        InlineMessage(
                            message = it,
                            isError = true,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                message?.let {
                    item {
                        InlineMessage(
                            message = it,
                            isError = false,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                if (queue.isEmpty() && !isLoading) {
                    item {
                        EmptyState(
                            title = "업로드 기록이 없습니다",
                            message = "업로드를 시작하면 진행 상태와 완료·실패 기록이 표시됩니다."
                        )
                    }
                }
                items(queue, key = { job -> job.id }) { job ->
                    val total = job.bytesTotal ?: 0L
                    val transferred = job.bytesTransferred ?: 0L
                    val progress = if (total > 0L) {
                        (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                job.folderName ?: job.sourceName
                                    ?: job.relativePath.substringAfterLast('/').ifEmpty { "/" },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    listOfNotNull(
                                        stateLabel(job.state),
                                        targetLabels[job.targetId] ?: job.targetId,
                                        job.currentFile?.substringAfterLast('/'),
                                        job.etaSeconds?.takeIf {
                                            isActiveUploadState(job.state) && it >= 0L
                                        }
                                            ?.let { "예상 ${formatEta(it)}" }
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                    Text(
                                        "${formatSize(transferred)} / ${formatSize(total)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            if (isDeletableUploadJob(job)) {
                                IconButton(
                                    onClick = { pendingDeletion = job },
                                    enabled = !isLoading
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "업로드 기록 삭제"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onJobClick(job) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}
