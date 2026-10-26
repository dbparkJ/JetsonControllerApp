package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.ui.storage.R7InlineNote as GeoInlineNote
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.storage.DeviceStorageUiState
import com.example.jetsoncontroller.ui.storage.breakableFileName
import com.example.jetsoncontroller.ui.storage.R7FileSelectionRow
import com.example.jetsoncontroller.ui.storage.StoragePage
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** Send-mode adapter; the data view model retains its own location independently of route browsing. */
@Composable
internal fun TransferFilesScreen(
    state: DeviceStorageUiState,
    targets: List<UploadTarget>,
    summary: UploadSourceSummary?,
    calculating: Boolean,
    sending: Boolean,
    sendEnabled: Boolean,
    disabledReason: String,
    onBack: () -> Unit,
    onParent: () -> Unit,
    onDirectory: (RemoteFileEntry) -> Unit,
    onFilePreview: (RemoteFileEntry) -> Unit,
    onSelect: (String, String) -> Unit,
    onStart: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onRemedy: () -> Unit,
    error: String? = null,
    ongoingRun: @Composable () -> Unit = {}
) {
    var selectedPath by rememberSaveable(state.deviceId, state.currentRoot?.id, state.currentPath) { mutableStateOf<String?>(null) }
    var targetId by rememberSaveable(state.deviceId) { mutableStateOf<String?>(null) }
    var search by rememberSaveable(state.deviceId) { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    state.preview?.let { preview ->
        StoragePage("파일 미리보기", onBack, media = true) {
            com.example.jetsoncontroller.ui.storage.FilePreview(preview, state.deviceName,
                state.entries.firstOrNull { it.name == preview.name })
        }
        return
    }
    val types = listOf("전체", "이미지", "영상", "센서", "로그")
    val root = state.currentRoot?.id
    val selection = summary?.takeIf { it.rootId == root && it.relativePath == selectedPath }
    val target = targets.firstOrNull { it.id == targetId } ?: targets.firstOrNull()
    TransferPage("파일 보내기", onBack) {
        GeoActionArea(visibilityKey = "${state.deviceId}:$selectedPath", visible = selectedPath != null, action = {
            if (selectedPath != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull(selectedPath?.substringAfterLast('/'), selection?.filesTotal?.toLong()?.let(Formats::itemCount),
                    selection?.bytesTotal?.let(Formats::size)).joinToString(" · "),
                    style = LocalGeoType.current.body, color = LocalGeoColors.current.text)
                when {
                    targets.isEmpty() -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
                        "받을 서버가 없습니다", "관리자에게 받는 서버 설정을 요청하세요."))
                    targets.size == 1 -> GeoFactRow("받는 서버", target?.let(::transferTargetLabel).orEmpty())
                    else -> targets.forEach { row -> GeoRadioRow(transferTargetLabel(row), row.id == target?.id, { targetId = row.id }) }
                }
                Text("장치 원본은 그대로 보관됩니다.", style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
                if (!sendEnabled) GeoButton("장치 Wi-Fi 설정", onRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                GeoButton("전송 시작", {
                    if (root != null && selectedPath != null && target != null) onStart(root, selectedPath!!, target.id)
                }, Modifier.fillMaxWidth(), enabled = sendEnabled && selection != null && target != null && !calculating && !sending,
                    disabledReason = if (!sendEnabled) disabledReason else "보낼 자료와 받는 서버를 확인하세요.",
                    busy = calculating || sending, busyLabel = "확인 중", actionArea = true)
            }
        }) { inlineAction ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = transferGutter, vertical = 16.dp)) {
                item { ongoingRun() }
                item {
                    Text(if (state.currentPath.isBlank()) "수집 자료" else "수집 자료 / ${state.currentPath}",
                        Modifier.fillMaxWidth().contentTarget(onClick = onParent).heightIn(min = 48.dp).padding(vertical = 12.dp),
                        style = LocalGeoType.current.supporting, color = LocalGeoColors.current.textSecondary)
                    GeoTextField(search, { search = it }, "이름으로 찾기", modifier = Modifier.fillMaxWidth(),
                        placeholder = "파일 이름 검색", showLabel = false, leadingIcon = GeoIcons.search)
                    Spacer(Modifier.height(16.dp))
                    GeoFilters(types, type, { type = it })
                    Spacer(Modifier.height(24.dp))
                    if (state.isLoading) GeoSkeleton("파일을 확인하고 있습니다.", kind = GeoSkeletonKind.Rows)
                    if (!state.controlAvailable) GeoStateBlock(GeoStateContent(GeoStateKind.OFFLINE,
                        "장치에 연결해야 합니다", disabledReason, "연결 방법"), onAction = onRemedy)
                    (error ?: state.error)?.let { GeoInlineNote("파일을 확인하지 못했습니다. 다시 시도하세요.",
                        actionLabel = "다시 확인", onAction = onRefresh) }
                }
                val entries = state.entries.filter { it.name.contains(search, true) && transferFileTypeMatches(it, type) }
                    .sortedWith(compareByDescending<RemoteFileEntry> { it.modifiedAt.orEmpty() }.thenBy { it.name.lowercase() })
                entries.groupBy { com.example.jetsoncontroller.ui.upload.transferLogDay(it.modifiedAt) }.entries.forEachIndexed { groupIndex, (day, group) ->
                item(key = "day:$day") { GeoSection(day, first = groupIndex == 0, modifier = Modifier.padding(top = if (groupIndex == 0) 24.dp else 0.dp)) {} }
                items(group, key = { it.relativePath }) { entry ->
                    R7FileSelectionRow(entry, selectedPath == entry.relativePath, {
                            selectedPath = entry.relativePath
                            if (root != null) onSelect(root, entry.relativePath)
                        }, onOpen = {
                            if (entry.type == com.example.jetsoncontroller.model.RemoteEntryType.DIRECTORY) onDirectory(entry)
                            else onFilePreview(entry)
                        }, enabled = state.controlAvailable, disabledReason = disabledReason,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                }
                if (entries.isEmpty() && !state.isLoading && state.controlAvailable) item {
                    GeoStateBlock(fullScreen = true, state = GeoStateContent(GeoStateKind.EMPTY, "파일이 없습니다", "다른 위치나 유형을 선택하세요."))
                }
                item { GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start) }
                item { inlineAction() }
            }
        }
    }
}

internal fun transferFileTypeMatches(entry: RemoteFileEntry, type: Int): Boolean {
    if (entry.type == com.example.jetsoncontroller.model.RemoteEntryType.DIRECTORY || type == 0) return true
    val extension = entry.name.substringAfterLast('.', "").lowercase()
    return when (type) {
        1 -> extension in setOf("jpg", "jpeg", "png", "webp")
        2 -> extension in setOf("mp4", "mkv", "avi", "mov")
        3 -> extension in setOf("csv", "json", "bag", "db", "pcd")
        4 -> extension in setOf("log", "txt")
        else -> true
    }
}
