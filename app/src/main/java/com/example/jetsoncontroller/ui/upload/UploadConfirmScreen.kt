package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
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
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadConfirmScreen(
    rootId: String,
    path: String,
    targets: List<UploadTarget>,
    serverUploadEnabled: Boolean,
    serverUploadDisabledReason: String?,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onManageTargets: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(targets) {
        if (targets.none { it.id == selectedTargetId }) {
            selectedTargetId = targets.firstOrNull()?.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 설정") },
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
                Button(
                    onClick = { selectedTargetId?.let(onConfirm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    enabled = serverUploadEnabled && selectedTargetId != null && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (isLoading) "준비 중" else "업로드 시작")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        text = "업로드할 위치",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = path.ifEmpty { "/" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "저장소 $rootId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                onClick = { selectedTargetId = target.id },
                                enabled = serverUploadEnabled
                            )
                        },
                        modifier = Modifier.clickable(enabled = serverUploadEnabled) {
                            selectedTargetId = target.id
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}
