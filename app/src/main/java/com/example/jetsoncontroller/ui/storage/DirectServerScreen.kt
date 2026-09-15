package com.example.jetsoncontroller.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.slateTextFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DirectServerScreen(
    state: DirectServerUiState,
    onBack: () -> Unit,
    onSection: (DirectServerSection) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (ServerEndpointProfile, String) -> Unit,
    onConnect: () -> Unit,
    onRefreshJobs: () -> Unit,
    onLoadMoreJobs: () -> Unit,
    onOpenJob: (ServerJob) -> Unit,
    onOpenDirectory: (ServerFileEntry) -> Unit,
    onOpenFile: (ServerFileEntry) -> Unit,
    onReceipt: (ServerJob) -> Unit,
    onMoveToTrash: (ServerJob) -> Unit,
    onRestore: (String) -> Unit,
    onUndoTrash: () -> Unit,
    onRefreshTrash: () -> Unit,
    onRemoveProfile: () -> Unit,
    onDismissMessage: () -> Unit,
    developerModeEnabled: Boolean = false,
    onDeviceData: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    var trashCandidate by remember(state.selectedProfileId) { mutableStateOf<ServerJob?>(null) }
    var removeProfile by remember(state.selectedProfileId) { mutableStateOf(false) }
    trashCandidate?.let { job ->
        AlertDialog(
            onDismissRequest = { trashCandidate = null },
            title = { Text("서버 작업을 휴지통으로 이동할까요?") },
            text = { Text("${job.sourceName}\n\n휴지통에서 복원할 수 있습니다. 서버가 이동을 확인한 뒤에만 목록에서 제거됩니다.") },
            confirmButton = { Button(onClick = { trashCandidate = null; onMoveToTrash(job) }) { Text("휴지통으로 이동") } },
            dismissButton = { TextButton(onClick = { trashCandidate = null }) { Text("취소") } }
        )
    }
    if (removeProfile) {
        AlertDialog(
            onDismissRequest = { removeProfile = false },
            title = { Text("서버 프로필을 삭제할까요?") },
            text = { Text("이 휴대전화에 저장된 서버 주소와 암호화된 직원 토큰, 해당 범위의 오프라인 캐시를 삭제합니다. 서버 데이터는 삭제하지 않습니다.") },
            confirmButton = { Button(onClick = { removeProfile = false; onRemoveProfile() }) { Text("프로필 삭제") } },
            dismissButton = { TextButton(onClick = { removeProfile = false }) { Text("취소") } }
        )
    }
    state.receipt?.let { receipt -> ReceiptDialog(receipt, onBack, developerModeEnabled) }

    val selectedProfile = state.profiles.firstOrNull { it.profile.profileId == state.selectedProfileId }?.profile
    val canMutate = directServerRoleCanMutate(state.capabilities?.employee?.role)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("서버 데이터")
                        Text(
                            selectedProfile?.let {
                                if (developerModeEnabled) {
                                    "${it.displayName} · ${it.environment.label()} · ${it.projectId}"
                                } else {
                                    it.displayName
                                }
                            }
                                ?: "Jetson 연결 없이 직접 조회",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                } },
                actions = {
                    if (state.section == DirectServerSection.DATA) IconButton(
                        onClick = onRefreshJobs,
                        enabled = !state.isLoading && !state.isConnecting
                    ) { Icon(Icons.Default.Refresh, "서버 데이터 새로고침") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DataLocationTabs(
                selected = DataLocation.SERVER,
                onDeviceClick = onDeviceData,
                onServerClick = {}
            )
            DirectServerTabs(state.section, onSection)
            state.mutationMessage?.let { message ->
                AppBanner(
                    message,
                    StatusTone.SUCCESS,
                    actionLabel = state.undoSessionId?.let { "실행 취소" },
                    onAction = state.undoSessionId?.let { onUndoTrash },
                    onDismiss = onDismissMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            state.message?.let { message ->
                val profileSettingsActions = setOf(
                    "프로필 추가", "프로필 편집", "프로필 인증 확인",
                    "프로젝트 권한 확인", "서버 환경 확인", "서버 인증서 확인"
                )
                val profileSettingsAction = state.errorActionLabel in profileSettingsActions
                AppBanner(
                    if (developerModeEnabled || !state.messageIsError) message
                    else directServerOperatorMessage(message),
                    if (state.messageIsError) StatusTone.WARNING else StatusTone.SUCCESS,
                    actionLabel = when {
                        profileSettingsAction && !developerModeEnabled ->
                            "다시 연결".takeIf { state.profiles.isNotEmpty() }
                        state.errorActionLabel != null -> state.errorActionLabel
                        else -> null
                    },
                    onAction = when {
                        profileSettingsAction && !developerModeEnabled ->
                            onConnect.takeIf { state.profiles.isNotEmpty() }
                        profileSettingsAction ->
                            ({ onSection(DirectServerSection.PROFILES) })
                        state.errorActionLabel != null -> {
                            if (state.section == DirectServerSection.TRASH) onRefreshTrash else onRefreshJobs
                        }
                        else -> null
                    },
                    onDismiss = onDismissMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            if (state.isLoading || state.isConnecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.section) {
                DirectServerSection.DATA -> ServerDataPane(
                    state, onConnect, onRefreshJobs, onOpenJob, onOpenDirectory, onOpenFile,
                    onReceipt, { trashCandidate = it }, onLoadMoreJobs, canMutate,
                    developerModeEnabled
                )
                DirectServerSection.TRASH -> TrashPane(
                    state, onRestore, onRefreshTrash, canMutate, developerModeEnabled
                )
                DirectServerSection.PROFILES -> ProfilesPane(
                    state, onSelectProfile, onSaveProfile, onConnect, { removeProfile = true },
                    developerModeEnabled
                )
            }
        }
    }
}

@Composable
private fun DirectServerTabs(selected: DirectServerSection, onSelect: (DirectServerSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            DirectServerSection.DATA to "수신 결과",
            DirectServerSection.TRASH to "휴지통",
            DirectServerSection.PROFILES to "서버 프로필"
        ).forEach { (section, label) ->
            FilterChip(selected == section, { onSelect(section) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ServerDataPane(
    state: DirectServerUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenJob: (ServerJob) -> Unit,
    onOpenDirectory: (ServerFileEntry) -> Unit,
    onOpenFile: (ServerFileEntry) -> Unit,
    onReceipt: (ServerJob) -> Unit,
    onTrash: (ServerJob) -> Unit,
    onLoadMore: () -> Unit,
    canMutate: Boolean,
    developerModeEnabled: Boolean
) {
    if (state.profiles.isEmpty()) {
        EmptyState("서버 연결 정보가 없습니다", "개발자 모드에서 서버 연결 정보를 등록하거나 관리자에게 요청해 주세요.")
        return
    }
    if (state.capabilities == null && state.jobsSource == null && !state.isConnecting) {
        EmptyState(
            "서버 연결 확인 필요",
            "저장된 직원 인증으로 서버 환경과 프로젝트 권한을 확인합니다.",
            actionLabel = "서버 연결",
            onAction = onConnect
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        ServerFreshness(state)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showDetail = state.selectedJob != null
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    JobsList(state, onRefresh, onOpenJob, onLoadMore, Modifier.weight(0.9f), developerModeEnabled)
                    VerticalDivider()
                    JobDetail(state, onOpenDirectory, onOpenFile, onReceipt, onTrash,
                        Modifier.weight(1.1f), canMutate, developerModeEnabled)
                }
            } else if (showDetail) {
                JobDetail(state, onOpenDirectory, onOpenFile, onReceipt, onTrash,
                    Modifier.fillMaxSize(), canMutate, developerModeEnabled)
            } else {
                JobsList(state, onRefresh, onOpenJob, onLoadMore, Modifier.fillMaxSize(), developerModeEnabled)
            }
        }
    }
}

@Composable
private fun ServerFreshness(state: DirectServerUiState) {
    val current = state.jobsSource == ServerJobsSource.NETWORK
    Surface(
        color = if (current) LocalCobaltColors.current.successBg else LocalCobaltColors.current.warningBg,
        contentColor = if (current) LocalCobaltColors.current.success else LocalCobaltColors.current.warning
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (current) Icons.Default.CloudDone else Icons.Default.CloudOff, null)
            Column(Modifier.weight(1f)) {
                Text(if (current) "서버에서 방금 확인" else "오프라인 보관 결과", style = MaterialTheme.typography.labelLarge)
                Text(
                    state.refreshedAt?.let { "서버 마지막 성공 · ${localDateTimeLabel(it)}" }
                        ?: state.cachedAtEpochMillis?.let { "캐시 저장 · ${formatEpoch(it)}" }
                        ?: "확인 시각 없음",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun JobsList(
    state: DirectServerUiState,
    onRefresh: () -> Unit,
    onOpenJob: (ServerJob) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier,
    developerModeEnabled: Boolean
) {
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.jobs.isEmpty() && !state.isLoading) item {
            EmptyState(
                "수신 결과가 없습니다",
                "프로젝트 범위에서 확인된 업로드 작업이 없습니다.",
                actionLabel = "다시 확인",
                onAction = onRefresh
            )
        }
        items(state.jobs, key = { it.sessionId }) { job ->
            Surface(
                onClick = { onOpenJob(job) },
                color = if (state.selectedJob?.sessionId == job.sessionId) LocalCobaltColors.current.accent
                    else LocalCobaltColors.current.sectionSoft,
                contentColor = if (state.selectedJob?.sessionId == job.sessionId) LocalCobaltColors.current.onAccent
                    else LocalCobaltColors.current.ink,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(job.sourceName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        StatusBadge(job.state.serverStateLabel(), job.state.serverStateTone())
                    }
                    if (developerModeEnabled) {
                        Text("장비 ${job.deviceId}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${job.fileCount}개 파일 · ${formatBytes(job.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("서버 관찰 · ${localDateTimeLabel(job.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.nextOffset != null) item {
            OutlinedButton(onClick = onLoadMore, enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()) { Text("이전 수신 결과 더 불러오기") }
        }
    }
}

@Composable
private fun JobDetail(
    state: DirectServerUiState,
    onOpenDirectory: (ServerFileEntry) -> Unit,
    onOpenFile: (ServerFileEntry) -> Unit,
    onReceipt: (ServerJob) -> Unit,
    onTrash: (ServerJob) -> Unit,
    modifier: Modifier,
    canMutate: Boolean,
    developerModeEnabled: Boolean
) {
    val job = state.selectedJob
    if (job == null) {
        EmptyState("수신 결과를 선택하세요", "파일과 서버 수신 확인 결과를 볼 수 있습니다.", modifier)
        return
    }
    state.preview?.let { preview ->
        PreviewPane(preview, modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(job.sourceName, style = MaterialTheme.typography.titleLarge)
                if (developerModeEnabled) {
                    Text("세션 ${job.sessionId}", style = MaterialTheme.typography.bodySmall)
                    Text("접근 프로젝트 ${job.accessProjectId ?: job.projectId} · 장비 ${job.deviceId}", style = MaterialTheme.typography.bodySmall)
                }
                job.surveyContext?.let { context ->
                    if (developerModeEnabled) {
                        Text("조사 ${context.surveyProjectId} · 구간 ${context.surveySectionId}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Run ${context.runId}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("조사 실행과 연결된 수신 결과", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(if (state.currentPath.isBlank()) "서버 파일" else if (developerModeEnabled) state.currentPath else "현재 폴더 · ${state.currentPath.substringAfterLast('/')}",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        if (job.state == "COMPLETED") item {
            OutlinedButton(onClick = { onReceipt(job) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp)); Text("독립 수신 영수증 확인")
            }
        }
        if (state.filesTruncated) item { AppBanner("파일 목록 일부만 표시됩니다. 하위 폴더에서 다시 확인하세요.", StatusTone.WARNING) }
        items(state.files, key = { it.relativePath }) { entry ->
            val directory = entry.type.equals("directory", true)
            ListItem(
                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(if (directory) "폴더" else entry.sizeBytes?.let(::formatBytes) ?: "파일") },
                leadingContent = { Icon(if (directory) Icons.Default.Folder else Icons.Default.Description, null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { if (directory) onOpenDirectory(entry) else onOpenFile(entry) }
            )
        }
        if (state.files.isEmpty() && !state.isLoading) item {
            EmptyState("표시할 파일이 없습니다", "서버가 이 경로에서 반환한 파일이 없습니다.")
        }
        item {
            OutlinedButton(onClick = { onTrash(job) }, enabled = canMutate && job.state == "COMPLETED",
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("휴지통으로 이동")
            }
        }
    }
}

@Composable
private fun PreviewPane(preview: DirectServerPreview, modifier: Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(preview.fileName, style = MaterialTheme.typography.titleLarge)
        when {
            preview.mediaType?.startsWith("image/") == true -> {
                val bitmap = remember(preview.bytes) { decodePreviewBitmap(preview.bytes) }
                if (bitmap != null) Image(bitmap.asImageBitmap(), preview.fileName,
                    Modifier.fillMaxWidth().weight(1f))
                else InlineMessage("이미지 미리보기를 해석하지 못했습니다.", true)
            }
            preview.mediaType?.startsWith("video/") == true -> VideoPreview(preview)
            preview.mediaType?.startsWith("text/") == true ||
                preview.mediaType?.contains("json") == true -> SelectionContainer {
                Text(preview.bytes.toString(Charsets.UTF_8), Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
            }
            else -> EmptyState("앱에서 표시할 수 없는 파일", "서버에서 미리보기 ${formatBytes(preview.bytes.size.toLong())}를 받았습니다.")
        }
    }
}

@Composable
private fun TrashPane(
    state: DirectServerUiState,
    onRestore: (String) -> Unit,
    onRefresh: () -> Unit,
    canMutate: Boolean,
    developerModeEnabled: Boolean
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DismissibleNoticeBanner(
                noticeKey = "storage.server-trash-retention.v1",
                message = "휴지통 항목은 서버 정책에 따라 보관됩니다. 영구 삭제 기능은 이 앱에서 제공하지 않습니다.",
                tone = StatusTone.INFO
            )
        }
        if (state.trash.isEmpty() && !state.isLoading) item {
            EmptyState(
                "휴지통이 비어 있습니다",
                "복구 가능한 서버 작업이 없습니다.",
                actionLabel = "다시 확인",
                onAction = onRefresh
            )
        }
        items(state.trash, key = { it.sessionId }) { job ->
            SectionSurface(LocalCobaltColors.current.sectionSoft) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(job.sourceName, style = MaterialTheme.typography.titleMedium)
                    job.surveyContext?.let { context ->
                        Text(
                            if (developerModeEnabled) {
                                "조사 ${context.surveyProjectId} · 구간 ${context.surveySectionId}"
                            } else {
                                "조사 실행과 연결된 수신 결과"
                            },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${job.fileCount}개 파일 · ${formatBytes(job.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("이동 · ${localDateTimeLabel(job.trashedAt)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onRestore(job.sessionId) }, enabled = canMutate && !state.isLoading,
                        modifier = Modifier.fillMaxWidth()) { Text("서버 작업 복원") }
                }
            }
        }
    }
}

@Composable
private fun ProfilesPane(
    state: DirectServerUiState,
    onSelect: (String) -> Unit,
    onSave: (ServerEndpointProfile, String) -> Unit,
    onConnect: () -> Unit,
    onRemove: () -> Unit,
    developerModeEnabled: Boolean
) {
    var profileId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var environment by rememberSaveable { mutableStateOf(ServerEnvironment.PRODUCTION) }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var employeeId by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf("") }
    var token by remember(state.selectedProfileId) { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { token = "" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (developerModeEnabled) {
            item {
                DismissibleNoticeBanner(
                    noticeKey = "storage.server-profile-auth.v1",
                    message = "직원 토큰은 이 앱의 보호된 저장소에 보관합니다. 프로필 환경·직원·프로젝트가 서버 응답과 모두 일치해야 데이터를 표시합니다.",
                    tone = StatusTone.INFO
                )
            }
        }
        if (state.profiles.isNotEmpty()) item {
            SectionHeader("저장된 프로필")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { stored ->
                    FilterChip(
                        selected = stored.profile.profileId == state.selectedProfileId,
                        onClick = { onSelect(stored.profile.profileId) },
                        label = { Text("${stored.profile.displayName} · ${stored.profile.environment.label()}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(onClick = onConnect, enabled = state.selectedProfileId != null && !state.isConnecting,
                    modifier = Modifier.fillMaxWidth()) { Text("선택 프로필로 연결") }
                if (developerModeEnabled) {
                    TextButton(onClick = onRemove, enabled = state.selectedProfileId != null,
                        modifier = Modifier.fillMaxWidth()) { Text("선택 프로필 삭제") }
                }
            }
        }
        if (developerModeEnabled) {
            item { SectionHeader(if (state.profiles.isEmpty()) "서버 프로필 추가" else "새 프로필 추가 또는 인증 갱신") }
            item { OutlinedTextField(value = profileId, onValueChange = { profileId = it }, label = { Text("프로필 ID") }, singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("표시 이름") }, singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerEnvironment.entries.forEach { value -> FilterChip(environment == value, { environment = value }, label = { Text(value.label()) }) }
            } }
            item { OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("HTTPS 서버 루트 주소") }, supportingText = { Text("예: https://uploads.example.com/") }, singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = employeeId, onValueChange = { employeeId = it }, label = { Text("직원 ID") }, singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("프로젝트 ID") }, singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("직원 토큰") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                singleLine = true, colors = slateTextFieldColors(), modifier = Modifier.fillMaxWidth()) }
            item {
                Button(
                    onClick = {
                        onSave(
                            ServerEndpointProfile(profileId, displayName, environment, baseUrl, employeeId, projectId),
                            token
                        )
                        token = ""
                    },
                    enabled = listOf(profileId, displayName, baseUrl, employeeId, projectId, token).all { it.isNotBlank() } && !state.isConnecting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("암호화해 저장하고 연결") }
            }
        }
    }
}

@Composable
private fun ReceiptDialog(
    receipt: ServerReceipt,
    onDismiss: () -> Unit,
    developerModeEnabled: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("서버 수신 영수증") },
        text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(if (receipt.matched) "검증 일치" else "검증 불일치",
                if (receipt.matched) StatusTone.SUCCESS else StatusTone.ERROR)
            if (developerModeEnabled) {
                Text("세션 ${receipt.sessionId}")
                Text("접근 프로젝트 ${receipt.accessProjectId ?: receipt.projectId}")
            }
            receipt.surveyContext?.let { context ->
                if (developerModeEnabled) {
                    Text("조사 ${context.surveyProjectId} · 구간 ${context.surveySectionId}")
                    Text("Run ${context.runId} · 장비 ${context.deviceId}")
                } else {
                    Text("조사 실행과 연결됨")
                }
            }
            Text("${receipt.fileCount}개 파일 · ${formatBytes(receipt.totalBytes)}")
            if (developerModeEnabled) Text("SHA-256\n${receipt.contentSha256}")
            Text("완료 · ${localDateTimeLabel(receipt.completedAt)}")
            Text("검증 · ${localDateTimeLabel(receipt.verifiedAt)}")
        } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

private fun ServerEnvironment.label(): String = when (this) {
    ServerEnvironment.DEVELOPMENT -> "개발"
    ServerEnvironment.TEST -> "시험"
    ServerEnvironment.PRODUCTION -> "운영"
}

private fun String.serverStateLabel(): String = when (uppercase(Locale.ROOT)) {
    "COMPLETED" -> "수신 완료"
    "FAILED" -> "실패"
    "TRASHED" -> "휴지통"
    "UPLOADING" -> "전송 중"
    else -> "상태 확인 필요"
}

private fun String.serverStateTone(): StatusTone = when (uppercase(Locale.ROOT)) {
    "COMPLETED" -> StatusTone.SUCCESS
    "FAILED" -> StatusTone.ERROR
    "UPLOADING" -> StatusTone.INFO
    else -> StatusTone.WARNING
}

internal fun directServerOperatorMessage(@Suppress("UNUSED_PARAMETER") message: String): String =
    "서버 인증을 확인하지 못했습니다. 관리자에게 서버 연결 설정 확인을 요청해 주세요."

internal fun directServerRoleCanMutate(role: String?): Boolean =
    role?.uppercase(Locale.ROOT) in setOf("OPERATOR", "ADMIN")

private fun formatEpoch(epochMillis: Long): String = java.text.DateFormat.getDateTimeInstance()
    .format(java.util.Date(epochMillis))

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

internal fun calculatePreviewSampleSize(width: Int, height: Int, maxEdge: Int = 2048): Int {
    var sample = 1
    while (width / sample > maxEdge || height / sample > maxEdge) sample *= 2
    return sample
}

private fun decodePreviewBitmap(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = calculatePreviewSampleSize(bounds.outWidth, bounds.outHeight)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

@Composable
private fun VideoPreview(preview: DirectServerPreview) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val suffix = when {
        preview.mediaType?.contains("webm") == true -> ".webm"
        preview.mediaType?.contains("quicktime") == true -> ".mov"
        else -> ".mp4"
    }
    var retry by remember(preview) { mutableIntStateOf(0) }
    var file by remember(preview) { mutableStateOf<java.io.File?>(null) }
    var failure by remember(preview) { mutableStateOf<String?>(null) }
    LaunchedEffect(preview, retry) {
        failure = null
        file = null
        var candidate: java.io.File? = null
        try {
            withContext(Dispatchers.IO) {
                val target = java.io.File.createTempFile("server-preview-", suffix, context.cacheDir)
                candidate = target
                try {
                    target.writeBytes(preview.bytes)
                } catch (error: Exception) {
                    target.delete()
                    throw error
                }
            }
            currentCoroutineContext().ensureActive()
            file = candidate
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = "영상 미리보기를 준비하지 못했습니다. 다시 시도하세요."
        } finally {
            candidate?.let { abandoned ->
                withContext(NonCancellable + Dispatchers.IO) { abandoned.delete() }
            }
        }
    }
    failure?.let { error ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineMessage("영상 미리보기를 준비하지 못했습니다. $error", true)
            OutlinedButton(onClick = { retry += 1 }, modifier = Modifier.fillMaxWidth()) {
                Text("영상 미리보기 다시 준비")
            }
        }
    } ?: file?.let { readyFile ->
        var videoView by remember(preview, readyFile) { mutableStateOf<android.widget.VideoView?>(null) }
        DisposableEffect(preview, readyFile) {
            onDispose {
                videoView?.stopPlayback()
                videoView = null
                runCatching { readyFile.delete() }
            }
        }
        key(preview, readyFile) {
            AndroidView(
                factory = { viewContext ->
                    android.widget.VideoView(viewContext).apply {
                        videoView = this
                        setVideoPath(readyFile.absolutePath)
                        setMediaController(android.widget.MediaController(viewContext).also { it.setAnchorView(this) })
                        setOnPreparedListener { it.isLooping = false; seekTo(1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp)
            )
        }
    } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
}
