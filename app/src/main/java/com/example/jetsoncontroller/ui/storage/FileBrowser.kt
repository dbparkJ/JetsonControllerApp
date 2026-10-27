package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.ui.storage.R7InlineNote as GeoInlineNote
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
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
    onReconnect: () -> Unit = onConnection,
    onSend: ((String, String) -> Unit)? = null
) {
    val wideSend = send != null && LocalGeoWindow.current.twoPane
    val location = "$entryPoint:${state.deviceId}:${state.currentRoot?.id}:${state.currentPath}"
    val listScroll = rememberSaveable(location, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val detailScroll = rememberSaveable(location, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    var query by rememberSaveable(location) { mutableStateOf("") }
    var category by rememberSaveable(location) { mutableStateOf("전체") }
    var selecting by rememberSaveable(location) { mutableStateOf(false) }
    var selectedPath by rememberSaveable(location) { mutableStateOf<String?>(null) }
    var targetId by rememberSaveable(entryPoint, state.deviceId) { mutableStateOf<String?>(null) }
    var targetPicker by rememberSaveable(entryPoint, state.deviceId) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    var menu by rememberSaveable(location) { mutableStateOf(false) }
    var folderInformation by rememberSaveable(location) { mutableStateOf(false) }
    var deletion by rememberSaveable(location, stateSaver = geoSnapshotSaver(DataDeletionSnapshot::class.java)) {
        mutableStateOf<DataDeletionSnapshot?>(null)
    }
    val menuOpener = remember { FocusRequester() }
    val root = state.currentRoot?.id
    val selected = state.entries.firstOrNull { it.relativePath == selectedPath }
    val entries = remember(state.entries, query, category) { browserEntries(state.entries, query, category) }
    val sendPath = selectedPath ?: state.currentPath.takeIf { it.isNotBlank() && (send != null || onSend != null) }
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
            openerFocusRequester = menuOpener, kind = GeoConfirmKind.DANGER,
            facts = { GeoCopyableValue(snapshot.entry.name, label = "원본 파일명") })
    }
    val sendTarget = send?.targets?.firstOrNull { it.id == targetId } ?: send?.targets?.firstOrNull()
    if (folderInformation) GeoInfoDialog("폴더 정보", { folderInformation = false }) {
        GeoCopyableValue(state.currentPath, label = "폴더 경로")
        GeoPropertyRow("장치", state.deviceName)
    }
    val sendSummary = send?.summary?.takeIf { it.rootId == root && it.relativePath == sendPath }
    if (targetPicker && send != null) GeoSheet("받는 서버", { targetPicker = false }, kind = GeoSheetKind.CHOICE) {
        send.targets.forEach { target -> GeoRadioRow(target.label, target.id == sendTarget?.id,
            { targetId = target.id; targetPicker = false }, description = target.baseUrl) }
    }
    val sendAction = if (send != null) GeoButtonAction("전송 시작", {
        if (root != null && sendPath != null && sendTarget != null) send.onStart(root, sendPath, sendTarget.id)
    }, enabled = send.enabled && state.controlAvailable && sendSummary != null && sendTarget != null &&
        !send.calculating && !send.sending,
        disabledReason = send.disabledReason.ifBlank { "보낼 자료와 받는 서버를 확인하세요." },
        busy = send.calculating || send.sending, busyLabel = "확인 중") else null
    val browseAction = if (send == null && onSend != null) GeoButtonAction(
        if (sendPath != null) "선택한 파일 보내기" else "보낼 파일 선택", {
            if (root != null && sendPath != null) onSend(root, sendPath) else selecting = true
        }, enabled = browserCanSelectFiles(state, error),
        disabledReason = when {
            !state.controlAvailable -> "장치에 연결되면 파일을 선택할 수 있습니다."
            state.isLoading -> "파일 목록을 확인하고 있습니다."
            state.isDeleting -> "파일을 정리하고 있습니다."
            state.error != null || error != null -> "파일 목록을 다시 불러오세요."
            root == null -> "수집 자료 위치를 확인하세요."
            else -> "보낼 파일이 없습니다."
        }) else null
    val previewContent by rememberUpdatedState<@Composable () -> Unit>({
        state.preview?.let { preview ->
            FilePreview(preview, state.deviceName, state.entries.firstOrNull { it.name == preview.name })
        }
    })
    val preview = remember { movableContentOf { previewContent() } }
    if (state.preview != null && !wideSend) {
        val content = state.preview
        StoragePage("파일 미리보기", onBack, media = content?.let(::usesImageMediaFrame) == true) { preview() }
        return
    }
    val browserActions: @Composable RowScope.() -> Unit = {
        if (onDelete != null || onTrash != null || state.currentPath.isNotBlank()) {
            GeoIconButton(GeoIcons.more, "파일 메뉴", { menu = true }, Modifier.focusRequester(menuOpener), chrome = true)
        }
    }
    StorageMenuSheet("파일 메뉴", menu, { menu = false }, buildList {
                if (state.currentPath.isNotBlank()) add(GeoMenuItem("폴더 정보", { folderInformation = true }))
                add(GeoMenuItem(if (selecting) "선택 해제" else "선택", {
                    selecting = !selecting; selectedPath = null
                }))
                onTrash?.let { add(GeoMenuItem("휴지통", it)) }
                if (selected != null && onDelete != null) add(GeoMenuItem("휴지통으로 이동", {
                    deletion = dataDeletionSnapshot(state, selected)
                }, destructive = true, enabled = state.controlAvailable && !state.isDeleting,
                    disabledReason = "장치에 연결되면 쓸 수 있습니다."))
    }, openerFocusRequester = menuOpener)
    StoragePage(if (send == null) "장치 파일" else "파일 보내기", onBack, actions = browserActions,
        showHeader = !wideSend, primaryAction = browseAction, maxContentWidth = 720.dp) {
        Box(Modifier.fillMaxSize()) {
            val review: @Composable () -> Unit = {
                if (send != null && sendPath != null && root != null) {
                    val summary = send.summary?.takeIf { it.rootId == root && it.relativePath == sendPath }
                    val target = send.targets.firstOrNull { it.id == targetId } ?: send.targets.firstOrNull()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeoCopyableValue(sendPath.substringAfterLast('/'))
                        Text(listOfNotNull(summary?.filesTotal?.toLong()?.let(Formats::itemCount),
                            summary?.bytesTotal?.let(Formats::size)).joinToString(" · "),
                            style = LocalGeoType.current.body, color = LocalGeoColors.current.text)
                        when (send.targets.size) {
                            0 -> GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "받을 서버가 없습니다",
                                "관리자에게 받는 서버 설정을 요청하세요."))
                            1 -> GeoFactRow("받는 서버", target?.label.orEmpty())
                            else -> GeoPickerField("받는 서버", target?.label.orEmpty(), { targetPicker = true })
                        }
                        if (!send.enabled) GeoButton("장치 Wi-Fi 설정", send.onRemedy, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                    }
                }
            }
            val listing: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = storageGutter)) {
                            GeoButton(if (state.currentPath.isBlank()) "수집 자료" else "수집 자료 / ${state.currentPath}",
                                onParent, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start,
                                contentColor = LocalGeoColors.current.textSecondary)
                            if (state.currentPath.isBlank()) {
                                GeoTextField(query, { query = it }, "검색", Modifier.testTag("file-search"),
                                    placeholder = "파일 이름 검색", showLabel = false, leadingIcon = GeoIcons.search,
                                    inputKind = GeoInputKind.SEARCH, imeAction = ImeAction.Search,
                                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                                Spacer(Modifier.height(12.dp))
                            }
                    }
                    GeoFilterChipRow(mediaCategories, mediaCategories.indexOf(category).coerceAtLeast(0),
                        { category = mediaCategories[it] }, contentPadding = PaddingValues(horizontal = storageGutter))
                StorageRefresh(state.isLoading, state.controlAvailable && !state.isDeleting, onRefresh, Modifier.weight(1f)) {
                    LazyColumn(state = listScroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = storageGutter, end = storageGutter,
                        top = if (com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f) 24.dp else 16.dp,
                        bottom = if (com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f) 32.dp else 16.dp)) {
                        item { ongoingRun() }
                        if (!state.controlAvailable) item {
                            GeoInlineNote("${state.deviceName} 연결이 끊겼습니다. 마지막으로 본 목록입니다.",
                                tone = Tone.WARNING, kind = GeoNoteKind.LIVE_ERROR,
                                actionLabel = "다시 연결", onAction = onReconnect)
                            GeoButton("연결 방법", onConnection, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                        }
                        item {
                            if (state.isLoading && state.entries.isEmpty()) GeoSkeleton("파일을 확인하고 있습니다.", kind = GeoSkeletonKind.Rows)
                            if (state.isDeleting) GeoProgress()
                        }
                        val failure = error ?: state.error
                        if (failure != null) item {
                            Spacer(Modifier.height(24.dp))
                            val title = "파일 목록을 불러오지 못했습니다"
                            GeoInlineNote("$title. ${storageFailureDetail(failure, title)}",
                                tone = if (state.entries.isEmpty()) Tone.ERROR else Tone.WARNING,
                                kind = GeoNoteKind.LIVE_ERROR)
                        }
                        val days = entries.groupBy { browserDay(it.modifiedAt) }
                        days.entries.forEachIndexed { groupIndex, (day, group) ->
                            item(key = "day:$day") {
                            R7Section(day, first = groupIndex == 0,
                                modifier = Modifier.padding(top = if (groupIndex == 0) 24.dp else 0.dp), trailingColumn = GeoRowTrailing.Status) {
                            group.forEachIndexed { index, entry ->
                                val directory = entry.type == RemoteEntryType.DIRECTORY
                                val divider = index < group.lastIndex
                                val select = {
                                    selectedPath = if (selectedPath == entry.relativePath) null else entry.relativePath
                                    if (wideSend && selectedPath != null && !directory) {
                                        if (state.preview != null) onBack()
                                        onFile(entry)
                                    }
                                    else if (wideSend && selectedPath == null && state.preview != null) onBack()
                                }
                                if (selecting || (send != null && !directory)) R7FileSelectionRow(entry,
                                    selectedPath == entry.relativePath, select,
                                    onOpen = {
                                        if (directory) onDirectory(entry) else {
                                            if (wideSend) {
                                                selectedPath = entry.relativePath
                                                if (state.preview != null) onBack()
                                            }
                                            onFile(entry)
                                        }
                                    },
                                    enabled = state.controlAvailable, disabledReason = "장치에 연결되면 쓸 수 있습니다.",
                                    divider = divider)
                                else {
                                GeoRow(breakableFileName(entry.name), {
                                    if (!state.controlAvailable) onConnection()
                                    else if (directory) onDirectory(entry) else onFile(entry)
                                },
                                    meta = fileEntryMeta(entry), divider = divider,
                                    leading = { GeoIconTile(if (directory) GeoIcons.folder else mediaIcon(entry)) })
                                }
                                }
                            }
                            }
                        }
                        if (entries.isEmpty() && !state.isLoading && failure == null) item {
                            val filtered = query.isNotBlank() || category != "전체"
                            GeoStateBlock(fullScreen = true, state = GeoStateContent(GeoStateKind.EMPTY,
                                if (filtered) "검색 결과가 없습니다" else "파일이 없습니다",
                                if (filtered) "다른 이름이나 유형으로 찾아보세요." else "수집을 마치면 여기에 표시됩니다."))
                        }
                        item {
                            StorageFreshness(state.loadedAtEpochMillis, onRefresh, loading = state.isLoading, forceVisible = failure != null)
                        }
                    }
                }
            }
            }
            if (wideSend) {
                GeoPaneLayout(singlePane = GeoPaneSelection.LIST,
                    list = {
                        GeoLargeTitleScaffold("파일 보내기", onBack = onBack, actions = browserActions) { listing() }
                    }, detail = {
                        GeoLargeTitleScaffold("파일 미리보기") {
                        StorageActionLayout(visible = sendPath != null && root != null,
                            action = review, primaryAction = sendAction) {
                            LazyColumn(state = detailScroll, modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)) {
                                if (state.preview != null) item(key = "preview") {
                                    GeoSection("파일 미리보기", first = true) {
                                        Box(Modifier.fillMaxWidth().height(400.dp)) { preview() }
                                    }
                                }
                                if (state.isLoading) item { GeoSkeleton("파일을 확인하고 있습니다.", kind = GeoSkeletonKind.Rows) }
                                if (state.preview == null && !state.isLoading) item {
                                    GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "파일을 선택하세요",
                                        "왼쪽에서 파일을 고르면 미리보기와 전송 정보를 볼 수 있습니다."))
                                }
                            }
                        }
                        }
                    })
            } else StorageActionLayout(visible = send != null && sendPath != null && root != null,
                action = review, primaryAction = sendAction, body = listing)
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                GeoOperationMessageHost(state.message, onDismissMessage, "실행 취소".takeIf { state.undoTrashId != null },
                    onUndo.takeIf { state.undoTrashId != null })
            }
        }
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
