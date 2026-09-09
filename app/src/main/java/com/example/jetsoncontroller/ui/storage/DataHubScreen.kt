package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.upload.UploadUiState

internal fun folderSelectionKey(rootId: String, path: String): String = "$rootId\u0000$path"

@Composable
fun DataHubScreen(
    deviceName: String, pipelines: List<ManagedPipeline>, uploads: UploadUiState,
    uploadEnabled: Boolean, unavailableReason: String, unreadCount: Int,
    onDevices: () -> Unit, onAlerts: () -> Unit, onFiles: () -> Unit,
    onHistory: () -> Unit, onTargets: () -> Unit,
    onTransfer: (String, String) -> Unit, onSection: (ControlSection) -> Unit
) {
    var selected by rememberSaveable(uploads.deviceId) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(uploads.deviceId) { mutableStateOf("") }
    val folders = pipelines.filter { it.outputRootId != null && it.outputPath != null }
        .distinctBy { folderSelectionKey(it.outputRootId!!, it.outputPath!!) }
    val chosen = folders.firstOrNull { folderSelectionKey(it.outputRootId!!, it.outputPath!!) == selected }
    val visible = folders.filter { it.label.contains(query, true) || it.outputPath.orEmpty().contains(query, true) }
    Scaffold(topBar = { DeviceContextHeader("데이터", deviceName,
        if (uploadEnabled) "LAN · 파일 전송 가능" else "전송 경로 확인 필요", onDevices, unreadCount, onAlerts) },
        bottomBar = {
            Surface {
                Column {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(if (selected == null) "폴더 선택 안 됨" else if (chosen == null) "선택한 폴더 재확인 필요" else "폴더 1개 선택 · 용량은 다음 단계에서 확인",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(shape = MaterialTheme.shapes.small, onClick = { chosen?.let { onTransfer(it.outputRootId!!, it.outputPath!!) } },
                            enabled = uploadEnabled && chosen != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("선택한 폴더 전송 확인") }
                    }
                    ControlNavigationBar(ControlSection.DATA, onSection)
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("전송 대기") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = false, onClick = onFiles, label = { Text("장비 파일") }, modifier = Modifier.weight(1f))
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("전송 대상 서버", style = MaterialTheme.typography.titleMedium)
                        Text(if (uploads.targets.isEmpty()) "설정된 대상 없음 또는 목록 미확인" else uploads.targets.joinToString { it.label })
                        Text("대상 서버 접근·인증은 전송 시 확인합니다. 모바일 RTK 중계와 별개입니다.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onTargets) { Text("서버 대상 관리") }
                    }
                }
            }
            if (!uploadEnabled) item { InlineMessage(unavailableReason, false) }
            item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("폴더 검색") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item {
                SectionHeader("전송할 폴더", trailing = { TextButton(onClick = { selected = null }, enabled = selected != null) { Text("선택 해제") } })
            }
            if (chosen != null && chosen !in visible) item { Text("검색에 가려진 선택 폴더 1개 · ${chosen.label}", style = MaterialTheme.typography.bodyMedium) }
            if (visible.isEmpty()) item { EmptyState("표시할 결과 폴더 없음", "장비 파일에서 실제 폴더를 찾아 전송할 수 있습니다.", actionLabel = "장비 파일", onAction = onFiles) }
            items(visible, key = { folderSelectionKey(it.outputRootId!!, it.outputPath!!) }) { folder ->
                val key = folderSelectionKey(folder.outputRootId!!, folder.outputPath!!)
                Surface(shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().selectable(selected == key, role = Role.RadioButton,
                        onClick = { selected = key }).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == key, onClick = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(folder.label, style = MaterialTheme.typography.titleMedium)
                            Text("${folder.outputRootId} / ${folder.outputPath}", style = MaterialTheme.typography.bodyMedium)
                            Text("폴더 전체 · 용량 미확인", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Text("한 번에 폴더 하나를 전송합니다. 새 폴더는 자동 선택하지 않으며 원본을 유지합니다.", style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("전송 진행·검증·이력 ${uploads.queue.size}개") } }
        }
    }
}
