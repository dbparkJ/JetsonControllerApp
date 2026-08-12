package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadHistoryScreen(
    history: List<UploadJob>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onJobClick: (UploadJob) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
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
                if (history.isEmpty() && !isLoading) {
                    item {
                        EmptyState(
                            title = "업로드 기록이 없습니다",
                            message = "저장소에서 폴더를 선택해 첫 업로드를 시작하세요."
                        )
                    }
                }
                items(history, key = { job -> job.id }) { job ->
                    val (icon, color) = when (job.state) {
                        UploadJobState.COMPLETED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                        UploadJobState.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        UploadJobState.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.outline
                        else -> Icons.Default.CloudUpload to MaterialTheme.colorScheme.secondary
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                job.relativePath.substringAfterLast('/').ifEmpty { "/" },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                "${stateLabel(job.state)} · ${job.targetId}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(icon, contentDescription = null, tint = color)
                        },
                        trailingContent = {
                            job.bytesTotal?.let { Text(formatSize(it)) }
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
