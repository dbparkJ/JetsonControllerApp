package com.example.jetsoncontroller.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

internal data class FileSendReview(
    val targets: List<UploadTarget>,
    val summary: UploadSourceSummary?,
    val calculating: Boolean,
    val sending: Boolean,
    val enabled: Boolean,
    val disabledReason: String,
    val onSelect: (String, String) -> Unit,
    val onStart: (String, String, String) -> Unit,
    val onRemedy: () -> Unit
)

@Composable
internal fun FileBrowser(
    state: DeviceStorageUiState,
    entryPoint: String,
    onBack: () -> Unit,
    onParent: () -> Unit,
    onRefresh: () -> Unit,
    onDirectory: (RemoteFileEntry) -> Unit,
    onFile: (RemoteFileEntry) -> Unit,
    onDelete: ((RemoteFileEntry) -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onConnection: () -> Unit = onBack,
    onDismissMessage: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    send: FileSendReview? = null,
    error: String? = null,
    ongoingRun: @Composable () -> Unit = {},
    onReconnect: () -> Unit = onConnection
) {
    val location = "$entryPoint:${state.deviceId}:${state.currentRoot?.id}:${state.currentPath}"
    var query by rememberSaveable(location) { mutableStateOf("") }
    var category by rememberSaveable(location) { mutableStateOf("전체") }
    var selecting by rememberSaveable(location) { mutableStateOf(false) }
    var selectedPath by rememberSaveable(location) { mutableStateOf<String?>(null) }
    var targetId by rememberSaveable(entryPoint, state.deviceId) { mutableStateOf<String?>(null) }
    var picker by remember(location) { mutableStateOf(false) }
    var menu by remember(location) { mutableStateOf(false) }
    var deletion by remember(location) { mutableStateOf<DataDeletionSnapshot?>(null) }
    val pickerOpener = remember { FocusRequester() }
    val menuOpener = remember { FocusRequester() }
    val root = state.currentRoot?.id
    val selected = state.entries.firstOrNull { it.relativePath == selectedPath }
    val entries = remember(state.entries, query, category) { browserEntries(state.entries, query, category) }
    val sizeColumns = browserSizeColumns(entries)
    val sendPath = selectedPath ?: state.currentPath.takeIf { it.isNotBlank() && send != null }
    val latestSend by rememberUpdatedState(send)
    LaunchedEffect(state.deviceId, root, sendPath, send != null) {
        if (root != null && sendPath != null) latestSend?.onSelect?.invoke(root, sendPath)
    }
    LaunchedEffect(state.entries) {
        if (selectedPath != null && selected == null && !state.isLoading) selectedPath = null
    }
    BackHandler(enabled = state.preview != null || state.currentPath.isNotBlank() || selecting) {
        if (selecting && state.preview == null) { selecting = false; selectedPath = null } else onBack()
    }
    if (picker) GeoSheet("유형", { picker = false }, openerFocusRequester = pickerOpener) {
        mediaCategories.forEach { name ->
            GeoRadioRow(name, category == name, { category = name; picker = false })
        }
    }
    deletion?.let { snapshot ->
        GeoConfirmDialog("${storageObjectName(snapshot.entry.name)} 휴지통으로 옮길까요?",
            "${state.deviceName} 휴지통에서 복원할 수 있습니다.", "휴지통으로 이동",
            onConfirm = {
                if (snapshot.matches(state)) onDelete?.invoke(snapshot.entry)
                deletion = null
                selectedPath = null
                selecting = false
            }, onDismissRequest = { deletion = null },
            confirmEnabled = snapshot.matches(state), disabledReason = "장치와 파일을 다시 확인하세요.",
            openerFocusRequester = menuOpener)
    }
    state.preview?.let { preview ->
        StoragePage(preview.name, onBack, media = true) { FilePreview(preview) }
        return
    }
    StoragePage(if (send == null) "장치 파일" else "파일 보내기", onBack, actions = {
        if (onDelete != null || onTrash != null) Box {
            GeoIconButton(GeoIcons.more, "파일 메뉴", { menu = true }, Modifier.focusRequester(menuOpener), chrome = true)
            GeoMenu(menu, { menu = false }, buildList {
                add(GeoMenuItem(if (selecting) "선택 해제" else "선택", {
                    selecting = !selecting; selectedPath = null
                }))
                onTrash?.let { add(GeoMenuItem("휴지통", it)) }
                if (selected != null && onDelete != null) add(GeoMenuItem("휴지통으로 이동", {
                    deletion = dataDeletionSnapshot(state, selected)
                }, destructive = true, enabled = state.controlAvailable && !state.isDeleting,
                    disabledReason = "장치에 연결되면 쓸 수 있습니다."))
            }, openerFocusRequester = menuOpener)
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            GeoActionArea(visibilityKey = location to sendPath, action = {
                if (send != null && sendPath != null && root != null) {
                    val summary = send.summary?.takeIf { it.rootId == root && it.relativePath == sendPath }
                    val target = send.targets.firstOrNull { it.id == targetId } ?: send.targets.firstOrNull()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listOfNotNull(sendPath.substringAfterLast('/'), summary?.filesTotal?.toLong()?.let(Formats::itemCount),
                            summary?.bytesTotal?.let(Formats::size)).joinToString(" · "),
                            style = LocalGeoType.current.body, color = LocalGeoColors.current.text)
                        when (send.targets.size) {
                            0 -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "받을 서버가 없습니다",
                                "관리자에게 받는 서버 설정을 요청하세요."))
                            1 -> GeoFactRow("받는 서버", target?.label.orEmpty())
                            else -> send.targets.forEach { row ->
                                GeoRadioRow(row.label, row.id == target?.id, { targetId = row.id })
                            }
                        }
                        if (!send.enabled) GeoButton("장치 Wi-Fi 설정", send.onRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                        GeoButton("전송 시작", { target?.let { send.onStart(root, sendPath, it.id) } },
                            Modifier.fillMaxWidth(), enabled = send.enabled && state.controlAvailable && summary != null && target != null &&
                                !send.calculating && !send.sending,
                            disabledReason = send.disabledReason.ifBlank { "보낼 자료와 받는 서버를 확인하세요." },
                            busy = send.calculating || send.sending, busyLabel = "확인 중", actionArea = true)
                    }
                }
            }) { inlineAction ->
                StorageRefresh(state.isLoading, state.controlAvailable && !state.isDeleting, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = storageGutter, vertical = 16.dp)) {
                        item { ongoingRun() }
                        if (!state.controlAvailable) item {
                            GeoInlineNote("${state.deviceName} 연결이 끊겼습니다. 마지막으로 본 목록입니다.",
                                tone = Tone.WARNING, kind = GeoNoteKind.LIVE_ERROR,
                                actionLabel = "다시 연결", onAction = onReconnect)
                            GeoButton("연결 방법", onConnection, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                        }
                        item {
                            GeoButton(if (state.currentPath.isBlank()) "수집 자료" else "수집 자료 / ${state.currentPath}",
                                onParent, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                            if (state.currentPath.isBlank()) {
                                GeoTextField(query, { query = it }, "검색", Modifier.testTag("file-search"))
                                Spacer(Modifier.height(12.dp))
                            }
                            GeoPickerField("유형", category, { picker = true }, Modifier.focusRequester(pickerOpener))
                            if (state.isDeleting) GeoProgress()
                        }
                        val failure = error ?: state.error
                        if (failure != null) item {
                            Spacer(Modifier.height(24.dp))
                            GeoStateBlock(GeoStateContent(GeoStateKind.ERROR, "파일 목록을 불러오지 못했습니다",
                                failure, "다시 시도"), onAction = onRefresh)
                        }
                        val days = entries.groupBy { browserDay(it.modifiedAt) }
                        days.entries.forEachIndexed { groupIndex, (day, group) ->
                            item(key = "day:$day") { GeoSection(day, first = groupIndex == 0, modifier = Modifier.padding(top = if (groupIndex == 0) 24.dp else 0.dp)) {} }
                            itemsIndexed(group, key = { _, it -> it.relativePath }) { index, entry ->
                                val directory = entry.type == RemoteEntryType.DIRECTORY
                                val divider = index < group.lastIndex || groupIndex == days.size - 1
                                val select = { selectedPath = if (selectedPath == entry.relativePath) null else entry.relativePath }
                                if (selecting) GeoRadioRow(breakableFileName(entry.name), selectedPath == entry.relativePath, select,
                                    description = entry.sizeBytes?.let(Formats::size), enabled = state.controlAvailable,
                                    disabledReason = "장치에 연결되면 쓸 수 있습니다.")
                                else if (send != null && !directory) GeoActionRow(breakableFileName(entry.name), "선택",
                                    { if (state.controlAvailable) onFile(entry) else onConnection() }, select,
                                    meta = mediaCategory(entry), actionEnabled = state.controlAvailable,
                                    disabledReason = "장치에 연결되면 쓸 수 있습니다.", divider = divider)
                                else GeoRow(breakableFileName(entry.name), {
                                    if (!state.controlAvailable) onConnection()
                                    else if (directory) onDirectory(entry) else onFile(entry)
                                },
                                    meta = if (directory) "폴더" else mediaCategory(entry), divider = divider,
                                    badge = if (entry.sizeBytes != null) ({ BrowserSizeValue(entry.sizeBytes, sizeColumns) }) else null,
                                    leading = { Icon(if (directory) GeoIcons.folder else mediaIcon(entry), null,
                                        Modifier.size(24.dp), tint = LocalGeoColors.current.textSecondary) })
                            }
                        }
                        if (entries.isEmpty() && !state.isLoading && failure == null) item {
                            val filtered = query.isNotBlank() || category != "전체"
                            GeoStateBlock(fullScreen = true, state = GeoStateContent(GeoStateKind.EMPTY,
                                if (filtered) "검색 결과가 없습니다" else "파일이 없습니다",
                                if (filtered) "다른 이름이나 유형으로 찾아보세요." else "수집을 마치면 여기에 표시됩니다."))
                        }
                        item {
                            StorageFreshness(state.loadedAtEpochMillis, onRefresh)
                            inlineAction()
                        }
                    }
                }
            }
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                GeoOperationMessageHost(state.message, onDismissMessage, "실행 취소".takeIf { state.undoTrashId != null },
                    onUndo.takeIf { state.undoTrashId != null })
            }
        }
    }
}

@Composable
private fun browserSizeColumns(entries: List<RemoteFileEntry>): Pair<Dp, Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalGeoType.current.body.copy(fontFeatureSettings = "tnum")
    return remember(entries, density, style, measurer) {
        val sizes = entries.mapNotNull { it.sizeBytes?.let(Formats::size) }
        fun width(unit: Boolean): Dp = with(density) {
            (sizes.maxOfOrNull { size -> measurer.measure(AnnotatedString(
                if (unit) size.substringAfterLast(' ', "") else size.substringBeforeLast(' ')), style).size.width } ?: 0).toDp()
        }
        width(false) to width(true)
    }
}

@Composable
private fun BrowserSizeValue(bytes: Long, columns: Pair<Dp, Dp>) {
    val size = Formats.size(bytes)
    val style = LocalGeoType.current.body.copy(fontFeatureSettings = "tnum")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(size.substringBeforeLast(' '), Modifier.width(columns.first), style = style,
            color = LocalGeoColors.current.textSecondary, textAlign = TextAlign.End)
        Text(size.substringAfterLast(' ', ""), Modifier.width(columns.second), style = style,
            color = LocalGeoColors.current.textSecondary)
    }
}

/** Drop-in entry for the transfer graph; browsing and sending share FileBrowser. */
@Composable
internal fun StorageSendBrowser(
    state: DeviceStorageUiState,
    targets: List<UploadTarget>, summary: UploadSourceSummary?, calculating: Boolean, sending: Boolean,
    sendEnabled: Boolean, disabledReason: String,
    onBack: () -> Unit, onParent: () -> Unit, onDirectory: (RemoteFileEntry) -> Unit,
    onFilePreview: (RemoteFileEntry) -> Unit, onSelect: (String, String) -> Unit,
    onStart: (String, String, String) -> Unit, onRefresh: () -> Unit, onRemedy: () -> Unit,
    error: String? = null, ongoingRun: @Composable () -> Unit = {},
    onDeleteClick: ((RemoteFileEntry) -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onDismissMessage: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onConnection: () -> Unit = onRemedy,
    onReconnect: () -> Unit = onConnection
) = FileBrowser(state, "send", onBack, onParent, onRefresh, onDirectory, onFilePreview,
    onDelete = onDeleteClick, onTrash = onTrash, onDismissMessage = onDismissMessage, onUndo = onUndoDelete,
    onConnection = onConnection, onReconnect = onReconnect,
    send = FileSendReview(targets, summary, calculating, sending, sendEnabled, disabledReason, onSelect, onStart, onRemedy),
    error = error, ongoingRun = ongoingRun)

/** Lets long file and folder names wrap after - _ . instead of in the middle of a word (2026-09-02-14-44-32_raw). */
internal fun breakableFileName(name: String): String =
    name.replace(Regex("([-_.])(?=.)"), "$1\u200B")
