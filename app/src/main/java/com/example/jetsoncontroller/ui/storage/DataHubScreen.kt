package com.example.jetsoncontroller.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.AdaptiveContent
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.components.OperationMessageHost
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.slateTextFieldColors

/** File workspace backed by the selected device's real storage entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHubScreen(
    state: DeviceStorageUiState,
    serverUploadEnabled: Boolean,
    unavailableReason: String,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onDeleteClick: (RemoteFileEntry) -> Unit,
    onHistory: () -> Unit,
    onTransfer: (String, String) -> Unit,
    onSection: (ControlSection) -> Unit,
    onServerData: () -> Unit = {},
    onTrash: (() -> Unit)? = null,
    onDismissMessage: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {}
) {
    val colors = LocalGeoColors.current
    val locationKey = state.currentRoot?.id to state.currentPath
    var query by rememberSaveable(state.deviceId, locationKey) { mutableStateOf("") }
    var category by rememberSaveable(state.deviceId, locationKey) { mutableStateOf("전체") }
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    // Selection survives recreation but is scoped to one device, root, and directory.
    // A target from another workspace must never be uploaded by mistake.
    var selectionMode by rememberSaveable(state.deviceId, locationKey) { mutableStateOf(false) }
    var selectedPath by rememberSaveable(state.deviceId, locationKey) { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeletion by rememberSaveable(state.deviceId, locationKey, selectedPath) { mutableStateOf(false) }

    val entries = state.entries
        .filter { entry ->
            entry.name.contains(query, ignoreCase = true) &&
                (category == "전체" || entry.type == RemoteEntryType.DIRECTORY || mediaCategory(entry) == category)
        }
        .sortedWith(
            compareByDescending<RemoteFileEntry> { it.modifiedAt.orEmpty() }
                .thenBy { it.name.lowercase() }
        )
    val selected = state.entries.firstOrNull { it.relativePath == selectedPath }
    val groups = entries.groupBy { dateGroup(it.modifiedAt) }.entries.toList()
    val inDirectory = state.currentPath.isNotBlank()

    LaunchedEffect(state.entries, selectedPath) {
        if (selectedPath != null && selected == null) selectedPath = null
    }
    LaunchedEffect(state.preview) {
        if (state.preview != null) {
            selectionMode = false
            selectedPath = null
        }
    }
    BackHandler(enabled = state.preview != null || inDirectory, onBack = onNavigateBack)

    if (pendingDeletion && selected != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletion = false },
            title = { Text("장치 데이터를 휴지통으로 옮길까요?") },
            text = { Text("${selected.name} 항목은 휴지통에서 복원할 수 있습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletion = false
                        selectionMode = false
                        selectedPath = null
                        onDeleteClick(selected)
                    },
                    enabled = state.controlAvailable
                ) { Text("휴지통으로 이동") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = false }) { Text("취소") }
            }
        )
    }

    BoxWithConstraints {
    val tabletWorkspace = maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.3f &&
        !inDirectory && state.preview == null
    val expandedWorkspace = maxWidth >= 1000.dp && tabletWorkspace
    Scaffold(
        containerColor = colors.canvas,
        snackbarHost = {
            OperationMessageHost(
                message = state.message,
                onDismissMessage = onDismissMessage,
                actionLabel = "실행 취소".takeIf { state.undoTrashId != null },
                onAction = onUndoDelete.takeIf { state.undoTrashId != null }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    if (inDirectory || state.preview != null) {
                        Text(
                            state.preview?.name ?: "수집 파일",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else Text("파일", style = MaterialTheme.typography.headlineMedium)
                },
                navigationIcon = {
                    if (state.preview != null || state.currentPath.isNotBlank()) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "이전 폴더")
                        }
                    }
                },
                actions = {
                    if (state.preview == null && state.entries.isNotEmpty()) {
                        TextButton(onClick = {
                            selectionMode = !selectionMode
                            selectedPath = null
                        }) { Text(if (selectionMode) "취소" else "선택") }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "파일 메뉴")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("업로드 목록") },
                            leadingIcon = { Icon(Icons.Default.Upload, null) },
                            onClick = { menuOpen = false; onHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text("새로고침") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; onRefresh() },
                            enabled = state.controlAvailable && !state.isLoading
                        )
                        if (onTrash != null) {
                            DropdownMenuItem(
                                text = { Text("휴지통") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = { menuOpen = false; onTrash() }
                            )
                        }
                        if (unreadCount > 0) {
                            DropdownMenuItem(
                                text = { Text("알림 ${unreadCount}개") },
                                onClick = { menuOpen = false; onAlerts() }
                            )
                        }
                        if (selected != null) {
                            DropdownMenuItem(
                                text = { Text("선택 항목을 휴지통으로 이동") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = { menuOpen = false; pendingDeletion = true },
                                enabled = state.controlAvailable && !state.isDeleting
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (selected != null && !expandedWorkspace) {
                    val transferPath = selected.relativePath
                    Surface(
                        color = colors.surface,
                        contentColor = colors.ink,
                        border = BorderStroke(GeoSize.hairline, colors.border)
                    ) {
                        AdaptiveContent(maxWidth = 1100.dp) {
                            if (tabletWorkspace && selected != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = GeoSpace.xxl),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(GeoSpace.xxl)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${selected.name} 선택",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            listOfNotNull(
                                                if (selected.type == RemoteEntryType.DIRECTORY) "폴더" else mediaCategory(selected),
                                                selected.sizeBytes?.let(::formatSize)
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            state.currentRoot?.let { root -> onTransfer(root.id, transferPath) }
                                        },
                                        enabled = state.controlAvailable && serverUploadEnabled &&
                                            state.currentRoot != null && !state.isLoading,
                                        modifier = Modifier.weight(1f).heightIn(min = GeoSize.primaryAction),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text("서버로 전송")
                                        Spacer(Modifier.padding(horizontal = GeoSpace.xs))
                                        Icon(Icons.Default.Upload, contentDescription = null)
                                    }
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = GeoSpace.md),
                                    verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${selected.name} 선택",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (selected.type == RemoteEntryType.DIRECTORY) "폴더" else mediaCategory(selected),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.muted
                                            )
                                        }
                                        selected.sizeBytes?.let {
                                            Text(formatSize(it), style = MaterialTheme.typography.titleMedium, color = colors.muted)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            state.currentRoot?.let { root -> onTransfer(root.id, transferPath) }
                                        },
                                        enabled = state.controlAvailable && serverUploadEnabled &&
                                            state.currentRoot != null && !state.isLoading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text("서버로 전송")
                                        Spacer(Modifier.padding(horizontal = GeoSpace.xs))
                                        Icon(Icons.Default.Upload, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!inDirectory && state.preview == null) {
                    ControlNavigationBar(ControlSection.DATA, onSection)
                }
            }
        }
    ) { padding ->
        if (state.preview != null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                FilePreview(state.preview)
            }
            return@Scaffold
        }
        val explorer: @Composable (Modifier, Boolean) -> Unit = { modifier, showLocationTabs ->
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(bottom = GeoSpace.lg),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
            ) {
                if (!inDirectory) {
                    if (showLocationTabs) item {
                        DataLocationTabs(
                            selected = DataLocation.DEVICE,
                            onDeviceClick = {},
                            onServerClick = onServerData
                        )
                    }
                    if (showLocationTabs) item {
                        Surface(
                            onClick = onHistory,
                            color = colors.surface,
                            contentColor = colors.ink,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().testTag("upload-queue-entry")
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget).padding(GeoSpace.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, tint = colors.primary)
                                Text("업로드 목록", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted)
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                IconButton(onClick = { filtersVisible = !filtersVisible }) {
                                    Icon(Icons.Default.FilterList, "파일 유형 필터")
                                }
                            },
                            placeholder = { Text("파일명 검색") },
                            singleLine = true,
                            colors = slateTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "파일명 검색" }
                                .testTag("file-search")
                        )
                    }
                    if (filtersVisible) {
                        item { MediaFilters(category) { category = it } }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.currentRoot?.label
                                    ?.takeUnless { it.equals("Collected data", ignoreCase = true) }
                                    ?: "수집 자료",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                modifier = Modifier.weight(1f)
                            )
                            Text("최근순 ↓", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                        }
                    }
                } else {
                    item {
                        Text(
                            "장치  ›  ${state.currentPath.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                    item {
                        val knownSize = state.entries.mapNotNull { it.sizeBytes }
                            .takeIf { it.size == state.entries.size && it.isNotEmpty() }
                            ?.sum()
                        Text(
                            buildString {
                                append("${state.entries.size}개 항목")
                                if (knownSize != null) append(" · ${formatSize(knownSize)}")
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                if (!serverUploadEnabled && selected != null) {
                    item { AppBanner(unavailableReason, StatusTone.WARNING) }
                }
                if (!state.controlAvailable) {
                    item {
                        AppBanner(
                            "연결 끊김 · 마지막으로 확인한 파일 목록입니다.",
                            StatusTone.WARNING,
                            actionLabel = "다시 연결",
                            onAction = onDevices
                        )
                    }
                }
                state.error?.let { error ->
                    item { AppBanner(error, StatusTone.ERROR, actionLabel = "다시 불러오기", onAction = onRefresh) }
                }
                if (state.isLoading || state.isDeleting) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (entries.isEmpty() && !state.isLoading) {
                    item {
                        EmptyState(
                            title = if (query.isBlank()) "표시할 파일이 없습니다" else "검색 결과가 없습니다",
                            message = if (query.isBlank()) "수집한 파일과 폴더가 여기에 표시됩니다."
                                else "다른 파일명이나 유형을 선택해 주세요.",
                            actionLabel = "새로고침".takeIf { query.isBlank() },
                            onAction = onRefresh
                        )
                    }
                } else {
                    item {
                        val fileColumn: @Composable (List<Map.Entry<String, List<RemoteFileEntry>>>) -> Unit = { columnGroups ->
                            FileGroupColumn(
                                groups = columnGroups,
                                selectionMode = selectionMode,
                                selectedPath = selectedPath,
                                onSelect = { selectedPath = if (selectedPath == it.relativePath) null else it.relativePath },
                                onOpenDirectory = { entry ->
                                    if (state.controlAvailable) onDirectoryClick(entry) else onDevices()
                                },
                                onOpenFile = { entry ->
                                    if (state.controlAvailable) onFileClick(entry) else onDevices()
                                }
                            )
                        }
                        if (inDirectory) {
                            FileGroupColumn(
                                groups = mapOf("" to entries).entries.toList(),
                                selectionMode = selectionMode,
                                selectedPath = selectedPath,
                                onSelect = { selectedPath = if (selectedPath == it.relativePath) null else it.relativePath },
                                onOpenDirectory = { entry ->
                                    if (state.controlAvailable) onDirectoryClick(entry) else onDevices()
                                },
                                onOpenFile = { entry ->
                                    if (state.controlAvailable) onFileClick(entry) else onDevices()
                                },
                                showGroupLabel = false,
                                showModified = false
                            )
                        } else {
                            fileColumn(groups)
                        }
                    }
                }
            }
        }
        if (tabletWorkspace) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = GeoSpace.xxl),
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.xxl)
            ) {
                DataLocationSidebar(
                    modifier = Modifier.width(if (expandedWorkspace) 180.dp else 156.dp),
                    onServerData = onServerData,
                    onHistory = onHistory,
                    onTrash = onTrash
                )
                explorer(Modifier.weight(1f).fillMaxSize(), false)
                if (expandedWorkspace && selected != null) {
                    SelectedTargetPanel(
                        entry = selected,
                        canTransfer = state.controlAvailable && serverUploadEnabled &&
                            state.currentRoot != null && !state.isLoading,
                        onTransfer = {
                            state.currentRoot?.let { root -> onTransfer(root.id, selected.relativePath) }
                        },
                        onClear = {
                            selectedPath = null
                            selectionMode = false
                        },
                        modifier = Modifier.width(352.dp)
                    )
                }
            }
        } else {
            AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1100.dp) {
                explorer(Modifier.fillMaxSize(), true)
            }
        }
    }
    }
}

@Composable
private fun DataLocationSidebar(
    modifier: Modifier,
    onServerData: () -> Unit,
    onHistory: () -> Unit,
    onTrash: (() -> Unit)?
) {
    val colors = LocalGeoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        Text("위치", style = com.example.jetsoncontroller.ui.theme.GeoType.eyebrow, color = colors.muted)
        Surface(color = colors.brandSoft, contentColor = colors.onAccent, shape = MaterialTheme.shapes.small) {
            Row(
                Modifier.fillMaxWidth().padding(GeoSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
            ) {
                Icon(Icons.Default.Storage, null)
                Text("장치", style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onServerData).padding(GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
        ) {
            Icon(Icons.Default.Cloud, null, tint = colors.muted)
            Text("서버", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        }
        GeoRowDivider()
        Text(
            "업로드 목록",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onHistory).padding(vertical = GeoSpace.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted
        )
        if (onTrash != null) {
            Text(
                "휴지통",
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTrash).padding(vertical = GeoSpace.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun SelectedTargetPanel(
    entry: RemoteFileEntry,
    canTransfer: Boolean,
    onTransfer: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGeoColors.current
    Surface(
        modifier = modifier,
        color = colors.surface,
        contentColor = colors.ink,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(GeoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
        ) {
            Text("선택한 자료", style = com.example.jetsoncontroller.ui.theme.GeoType.eyebrow, color = colors.muted)
            Icon(
                if (entry.type == RemoteEntryType.DIRECTORY) Icons.Default.Folder else Icons.Default.Description,
                null,
                tint = colors.primary
            )
            Text(entry.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.type == RemoteEntryType.DIRECTORY) "폴더" else mediaCategory(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
            GeoRowDivider()
            entry.sizeBytes?.let {
                Text(formatSize(it), style = MaterialTheme.typography.headlineMedium)
            }
            entry.modifiedAt?.let {
                Text(
                    listOf(dateGroup(it), localTimeOrDateLabel(it)).distinct().joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            Button(
                onClick = onTransfer,
                enabled = canTransfer,
                modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction),
                shape = MaterialTheme.shapes.small
            ) {
                Text("서버로 전송")
                Spacer(Modifier.padding(horizontal = GeoSpace.xs))
                Icon(Icons.Default.Upload, null)
            }
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("선택 해제") }
        }
    }
}

@Composable
private fun FileGroupColumn(
    groups: List<Map.Entry<String, List<RemoteFileEntry>>>,
    selectionMode: Boolean,
    selectedPath: String?,
    onSelect: (RemoteFileEntry) -> Unit,
    onOpenDirectory: (RemoteFileEntry) -> Unit,
    onOpenFile: (RemoteFileEntry) -> Unit,
    showGroupLabel: Boolean = true,
    showModified: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        groups.forEach { (day, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                if (showGroupLabel) {
                    Text(day, style = MaterialTheme.typography.labelMedium, color = LocalGeoColors.current.muted)
                }
                Surface(shape = MaterialTheme.shapes.small, color = LocalGeoColors.current.surface) {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            FileEntryRow(
                                entry = entry,
                                selectionMode = selectionMode,
                                selected = selectedPath == entry.relativePath,
                                showModified = showModified,
                                onSelect = { onSelect(entry) },
                                onOpen = {
                                    if (entry.type == RemoteEntryType.DIRECTORY) onOpenDirectory(entry)
                                    else onOpenFile(entry)
                                }
                            )
                            if (index != entries.lastIndex) GeoRowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: RemoteFileEntry,
    selectionMode: Boolean,
    selected: Boolean,
    showModified: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    val colors = LocalGeoColors.current
    Surface(
        color = if (selected) colors.brandSoft else colors.surface,
        contentColor = colors.ink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .then(
                    if (selectionMode) Modifier.clickable(role = Role.RadioButton, onClick = onSelect)
                    else Modifier.clickable(onClick = onOpen)
                )
                .padding(horizontal = GeoSpace.md, vertical = GeoSpace.md),
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    entry.type == RemoteEntryType.DIRECTORY -> Icons.Default.Folder
                    mediaCategory(entry) == "이미지" -> Icons.Default.Image
                    mediaCategory(entry) == "영상" -> Icons.Default.Videocam
                    mediaCategory(entry) == "센서" -> Icons.Default.Sensors
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = colors.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    fileMetadata(entry, showModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectionMode) {
                RadioButton(selected = selected, onClick = null)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = "${entry.name} 열기")
            }
        }
    }
}

private fun fileMetadata(entry: RemoteFileEntry, showModified: Boolean): String = listOfNotNull(
    if (entry.type == RemoteEntryType.DIRECTORY) "폴더" else mediaCategory(entry),
    entry.modifiedAt?.takeIf { showModified }?.let(::localTimeOrDateLabel),
    entry.sizeBytes?.let(::formatSize)
).joinToString(" · ")
