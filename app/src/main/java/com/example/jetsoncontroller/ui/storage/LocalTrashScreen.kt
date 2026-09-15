package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.TrashEntry
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.DismissibleNoticeBanner
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.OperationMessageHost
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.Button

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTrashScreen(
    state: LocalTrashUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onDismissMessage: (String) -> Unit
) {
    Scaffold(
        snackbarHost = { OperationMessageHost(state.message, onDismissMessage) },
        topBar = { TopAppBar(
            title = { Text("장비 휴지통") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
            } },
            actions = { IconButton(onClick = onRefresh, enabled = state.online && !state.loading) {
                Icon(Icons.Default.Refresh, "휴지통 새로고침")
            } }
        ) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DismissibleNoticeBanner(
                    noticeKey = "storage.local-trash-retention.v1",
                    message = "휴지통 비우기는 지원하지 않습니다. 복원 가능한 항목만 원래 위치로 되돌릴 수 있습니다.",
                    tone = StatusTone.INFO
                )
            }
            if (state.loading || state.restoringId != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { InlineMessage(it, true) } }
            items(state.entries, key = { it.trashId }) { entry ->
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${categoryLabel(entry.category)} · ${stateLabel(entry.state)}",
                            style = MaterialTheme.typography.bodySmall)
                        entry.relativePath?.let { Text("${entry.rootId.orEmpty()}/$it", style = MaterialTheme.typography.bodySmall) }
                        entry.lastError?.let { InlineMessage(it, true) }
                        Button(
                            onClick = { onRestore(entry) },
                            enabled = state.online && entry.restoreSupported && entry.state == "TRASHED" && state.restoringId == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (entry.restoreSupported) "원래 위치로 복원" else "복원 지원 안 함")
                        }
                    }
                }
            }
            if (state.entries.isEmpty() && !state.loading) item {
                EmptyState("휴지통이 비어 있습니다", "휴지통으로 이동한 장비 데이터와 작업 이력이 여기에 표시됩니다.")
            }
        }
    }
}

private fun categoryLabel(category: String): String = when (category) {
    "STORAGE" -> "장비 데이터"
    "UPLOADED_SOURCE" -> "업로드 원본"
    "RUN_HISTORY" -> "작업 이력"
    else -> "장비 데이터"
}

private fun stateLabel(state: String): String = when (state) {
    "TRASHED" -> "복원 가능"
    "MOVING_TO_TRASH" -> "이동 상태 확인 중"
    "RESTORING" -> "복원 중"
    "RECOVERY_REQUIRED" -> "복구 확인 필요"
    else -> "상태 확인 필요"
}
