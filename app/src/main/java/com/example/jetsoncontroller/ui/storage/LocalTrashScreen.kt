package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
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
    onEmptyTrash: (String, List<String>) -> Unit,
    onDismissMessage: (String) -> Unit
) {
    val dialogDensity = LocalDensity.current
    var emptySnapshot by remember { mutableStateOf<LocalTrashEmptySnapshot?>(null) }
    LaunchedEffect(state.deviceId) { emptySnapshot = null }
    emptySnapshot?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { if (!state.emptying) emptySnapshot = null },
            title = { CompositionLocalProvider(LocalDensity provides dialogDensity) {
                val slotFontScale = LocalDensity.current.fontScale
                Text(
                    "휴지통을 완전히 비울까요?",
                    Modifier.testTag("local-trash-empty-dialog-title").semantics {
                        this[TrashDialogFontScaleKey] = slotFontScale
                    }
                )
            } },
            text = {
                CompositionLocalProvider(LocalDensity provides dialogDensity) {
                    Text("표시된 ${snapshot.trashIds.size}개 항목을 영구 삭제합니다. 삭제한 항목은 복원할 수 없습니다.")
                }
            },
            confirmButton = {
                CompositionLocalProvider(LocalDensity provides dialogDensity) {
                    Button(
                        onClick = {
                            emptySnapshot = null
                            onEmptyTrash(snapshot.deviceId, snapshot.trashIds)
                        },
                        enabled = !state.emptying && state.deviceId == snapshot.deviceId
                    ) { Text("영구 삭제") }
                }
            },
            dismissButton = {
                CompositionLocalProvider(LocalDensity provides dialogDensity) {
                    TextButton(onClick = { emptySnapshot = null }, enabled = !state.emptying) { Text("취소") }
                }
            }
        )
    }
    val busy = state.loading || state.restoringId != null || state.emptying
    Scaffold(
        snackbarHost = { OperationMessageHost(state.message, onDismissMessage) },
        topBar = { TopAppBar(
            title = { Text("장비 휴지통") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
            } },
            actions = { IconButton(onClick = onRefresh, enabled = state.online && !busy) {
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
                    noticeKey = "storage.local-trash-retention.v2",
                    message = when {
                        !state.online -> "장비에 연결하면 복원 가능한 항목과 영구 삭제 지원 여부를 확인할 수 있습니다."
                        !state.hasLoaded -> "장비에서 휴지통과 영구 삭제 지원 여부를 확인하고 있습니다."
                        state.emptySupported -> "복원 가능한 항목은 원래 위치로 되돌릴 수 있습니다. 휴지통을 비우면 표시된 항목이 영구 삭제됩니다."
                        else -> "이 장비 소프트웨어는 휴지통 비우기를 지원하지 않습니다. 장비 소프트웨어를 업데이트하면 영구 삭제할 수 있습니다."
                    },
                    tone = if (state.emptySupported) StatusTone.WARNING else StatusTone.INFO
                )
            }
            val purgeSnapshot = localTrashEmptySnapshot(state)
            item {
                Button(
                    onClick = { emptySnapshot = purgeSnapshot },
                    enabled = purgeSnapshot != null && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("휴지통 비우기${purgeSnapshot?.trashIds?.size?.let { " (${it}개)" }.orEmpty()}")
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
                            enabled = state.online && entry.restoreSupported && entry.state == "TRASHED" && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (entry.restoreSupported) "원래 위치로 복원" else "복원 지원 안 함")
                        }
                    }
                }
            }
            if (state.entries.isEmpty() && !state.loading && state.online && state.hasLoaded) item {
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
    "PURGING" -> "삭제 처리 중"
    "RECOVERY_REQUIRED" -> "복구 확인 필요"
    else -> "상태 확인 필요"
}

internal data class LocalTrashEmptySnapshot(val deviceId: String, val trashIds: List<String>)

internal val TrashDialogFontScaleKey = SemanticsPropertyKey<Float>("TrashDialogFontScale")

internal fun localTrashEmptySnapshot(state: LocalTrashUiState): LocalTrashEmptySnapshot? {
    val deviceId = state.deviceId ?: return null
    if (!state.online || !state.emptySupported) return null
    val ids = state.entries.filter(::isPurgeEligible).map(TrashEntry::trashId).distinct()
    return ids.takeIf { it.isNotEmpty() }?.let { LocalTrashEmptySnapshot(deviceId, it) }
}
