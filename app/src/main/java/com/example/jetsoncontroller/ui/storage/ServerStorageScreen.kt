package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontFamily
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.model.Formats

@Composable
fun ServerStorageScreen(state: ServerStorageUiState, onBack: () -> Unit, onDeviceDataClick: () -> Unit,
    onRefresh: () -> Unit, onTargetSelected: (UploadTarget) -> Unit, onSessionClick: (UploadLibrarySession) -> Unit,
    onDeleteSession: (UploadLibrarySession) -> Unit, onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit, onLoadMore: () -> Unit, onSectionSelected: (GeoTab) -> Unit,
    deletionEnabled: Boolean = false, thumbnailLoader: (suspend (RemoteFileEntry) -> Result<RemoteFileContent>)? = null,
    onDismissMessage: (String) -> Unit = {}, deviceName: String = "선택한 장치", onConnection: () -> Unit = {}) {
    var picker by remember(state.deviceId) { mutableStateOf(false) }
    var pending by remember(state.deviceId, state.controlAvailable, state.selectedTarget?.id) { mutableStateOf<UploadLibrarySession?>(null) }
    var category by rememberSaveable(state.deviceId, state.selectedSession?.sessionId, state.currentPath) { mutableStateOf("전체") }
    var filterPicker by remember { mutableStateOf(false) }
    val technical by rememberTechnicalInfo()
    pending?.let { frozen ->
        GeoConfirmDialog("서버 데이터를 삭제할까요?", "선택한 수신 결과를 서버에서 영구 삭제합니다. 되돌릴 수 없습니다.", "영구 삭제",
            { pending = null
                if (deletionEnabled && state.controlAvailable && !state.isDeleting && state.sessions.any { it.sessionId == frozen.sessionId }) onDeleteSession(frozen)
            }, { pending = null }, kind = GeoConfirmKind.DANGER,
            confirmEnabled = deletionEnabled && state.controlAvailable && !state.isDeleting,
            disabledReason = "이 화면에서는 서버 데이터를 지울 수 없습니다.")
    }
    if (picker) GeoSheet("서버 선택", { picker = false }, subtitle = "${deviceName}에 저장된 서버입니다.") {
        val type = MaterialTheme.typography
        MaterialTheme(typography = type.copy(bodySmall = type.bodySmall.copy(fontFamily = FontFamily.Monospace))) {
            state.targets.forEach { target -> GeoRadioRow(target.label, target.id == state.selectedTarget?.id,
                { picker = false; onTargetSelected(target) }, description = target.baseUrl) }
        }
    }
    if (filterPicker) GeoSheet("파일 유형", { filterPicker = false }) {
        listOf("전체", "이미지", "영상", "센서", "로그").forEach { label ->
            GeoRadioRow(label, label == category, { filterPicker = false; category = label })
        }
    }
    if (state.preview != null) {
        DevFrame(state.preview.name, onBack) { FilePreview(state.preview) }
        return
    }
    DevPage("장치 경유 서버 조회", onBack, overlay = {
        com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost(state.message, onDismissMessage)
    }) {
        if (!state.controlAvailable) {
            DevEmpty("$deviceName 기본 연결입니다", "장치를 같은 네트워크나 직접 연결로 연결하면 조회할 수 있습니다.", "연결 방법", onConnection)
        } else {
            GeoPickerField("서버", state.selectedTarget?.label ?: "선택 안 됨", { picker = true })
            Spacer(Modifier.height(12.dp))
            DevText("${deviceName}을 거쳐 조회합니다. 이 화면에서는 지울 수 없습니다.", secondary = true)
            DevFeedback(state.error, true)
            if (state.isLoading || state.isDeleting) DevText("확인 중")
            if (state.selectedSession == null) GeoSection("수신 결과", first = true, modifier = Modifier.padding(top = 24.dp), count = "${state.sessions.size}개") {
                state.sessions.forEach { session ->
                    GeoRow(proxySessionTitle(session.displayFolderName, session.completedAt ?: session.createdAt), { onSessionClick(session) },
                        meta = listOfNotNull(devTimestamp(session.completedAt ?: session.createdAt), Formats.size(session.totalBytes)).joinToString(" · "))
                    if (deletionEnabled) GeoButton("영구 삭제", { pending = session }, style = GeoButtonStyle.DangerOutline, enabled = !state.isDeleting)
                }
                if (state.sessions.isEmpty() && !state.isLoading && state.error == null) DevEmpty("수신 결과가 없습니다", "이 서버에 전송한 자료가 여기에 표시됩니다.")
                if (state.nextOffset != null) GeoButton("이전 수신 결과 더 불러오기", onLoadMore, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !state.isLoading)
            } else {
                DevIdentity(proxySessionTitle(state.selectedSession.displayFolderName, state.selectedSession.completedAt ?: state.selectedSession.createdAt),
                    state.currentPath.ifEmpty { "맨 위" })
                GeoPickerField("파일 유형", category, { filterPicker = true })
                GeoSection("파일", first = true, modifier = Modifier.padding(top = 24.dp)) {
                    val visible = state.entries.filter { it.type == RemoteEntryType.DIRECTORY || category == "전체" || mediaCategory(it) == category }
                    visible.forEach { entry -> GeoRow(breakableFileName(entry.name),
                        { if (entry.type == RemoteEntryType.DIRECTORY) onDirectoryClick(entry) else onFileClick(entry) },
                        meta = if (entry.type == RemoteEntryType.DIRECTORY) "폴더" else Formats.size(entry.sizeBytes),
                        leading = { MediaThumbnail(entry, "${state.deviceId}/${state.selectedTarget?.id}/${state.selectedSession.sessionId}", thumbnailLoader) }) }
                    if (visible.isEmpty() && !state.isLoading && state.error == null) DevEmpty("빈 폴더입니다", "이 위치에는 표시할 파일이 없습니다.")
                    if (state.listingTruncated) DevText("항목이 많아 현재 폴더의 처음 500개만 표시합니다.")
                }
                GeoTechInfo(listOf(GeoTechnicalValue("수신 ID", state.selectedSession.sessionId),
                    GeoTechnicalValue("저장 폴더", state.selectedSession.displayFolderName)), true, technical)
            }
            GeoButton("다시 조회", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !state.isLoading)
        }
    }
}
