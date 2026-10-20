package com.example.jetsoncontroller.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.*
import java.time.Instant
import java.util.Locale

@Composable
@Suppress("UNUSED_PARAMETER")
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
    onEmptyTrash: (String, List<String>) -> Unit,
    onRemoveProfile: () -> Unit,
    onDismissMessage: () -> Unit,
    developerModeEnabled: Boolean = false,
    onDeviceData: () -> Unit = {},
    onJetsonServerProxy: (() -> Unit)? = null,
    onServerConnection: (() -> Unit)? = null,
    onRelogin: (() -> Unit)? = null,
    onDeveloperTrash: (() -> Unit)? = null,
    showTechnicalInfo: Boolean = false,
    ongoingRun: @Composable () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val profile = state.profiles.firstOrNull { it.profile.profileId == state.selectedProfileId }?.profile
    val settings = onServerConnection ?: onConnect
    val refresh: () -> Unit = when {
        state.section == DirectServerSection.TRASH -> onRefreshTrash
        state.selectedJob != null && !state.mutationResultUnknown -> ({
            onOpenDirectory(ServerFileEntry("", state.currentPath, "directory", null, null))
        })
        else -> onRefreshJobs
    }
    var candidate by remember(state.selectedProfileId, state.section) { mutableStateOf<ServerTrashTarget?>(null) }
    var menu by remember(state.selectedProfileId, state.selectedJob?.sessionId) { mutableStateOf(false) }
    var sectionPicker by remember { mutableStateOf(false) }
    val menuOpener = remember { FocusRequester() }
    val sectionOpener = remember { FocusRequester() }
    val trash = state.section == DirectServerSection.TRASH
    val canMutate = directServerRoleCanMutate(state.capabilities?.employee?.role)
    candidate?.let { target ->
        GeoConfirmDialog("${storageObjectName(target.job.sourceName)} 서버 휴지통으로 옮길까요?",
            "서버 휴지통에서 복원할 수 있습니다.", "휴지통으로 이동", {
                if (target.matches(state)) onMoveToTrash(target.job)
                candidate = null
            }, { candidate = null }, confirmEnabled = target.matches(state),
            disabledReason = "서버와 수신 결과를 다시 확인하세요.", openerFocusRequester = menuOpener)
    }
    state.receipt?.let { receipt -> ReceiptDialog(receipt, profile?.displayName.orEmpty(), onBack,
        developerModeEnabled, showTechnicalInfo) }
    if (sectionPicker) GeoSheet("서버", { sectionPicker = false }, openerFocusRequester = sectionOpener) {
        GeoRadioRow("수신 결과", !trash, { onSection(DirectServerSection.DATA); sectionPicker = false })
        GeoRadioRow("휴지통", trash, { onSection(DirectServerSection.TRASH); sectionPicker = false })
    }
    state.preview?.let { preview ->
        StoragePage(preview.fileName, onBack, media = true) { PreviewPane(preview, Modifier.fillMaxSize()) }
        return
    }
    StoragePage("서버 수신 결과", onBack, actions = {
        state.selectedJob?.takeIf { !trash }?.let { job ->
            Box {
                GeoIconButton(GeoIcons.more, "서버 작업 메뉴", { menu = true }, Modifier.focusRequester(menuOpener), chrome = true)
                GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("서버 휴지통으로 이동", {
                    candidate = serverTrashTarget(state, job)
                }, enabled = serverTrashTarget(state, job) != null, destructive = true,
                    disabledReason = "수신 완료 상태와 서버 권한을 확인하세요.")), openerFocusRequester = menuOpener)
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ongoingRun()
                when {
                    state.requiresLogin -> ServerState("다시 로그인해야 합니다",
                        "직원 인증이 거절되었습니다. 다시 로그인하세요.", "다시 로그인", onRelogin ?: settings)
                    state.profiles.isEmpty() -> ServerState("서버 연결 정보가 없습니다",
                        "관리자에게 서버 연결 정보를 받아 설정하세요.", "서버 연결", settings)
                    else -> {
                        if (state.selectedJob == null || trash) {
                            Column(Modifier.padding(horizontal = storageGutter)) {
                                StorageFreshness(state.refreshedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                                    ?: state.cachedAtEpochMillis, refresh, profile?.displayName ?: "서버")
                                GeoSegmented("서버", listOf("수신 결과", "휴지통"), if (trash) 1 else 0,
                                    { onSection(if (it == 0) DirectServerSection.DATA else DirectServerSection.TRASH) },
                                    { sectionPicker = true }, Modifier.focusRequester(sectionOpener))
                            }
                        }
                        if (state.isConnecting || state.isLoading) GeoProgress()
                        state.message?.let { message ->
                            GeoInlineNote(if (!state.messageIsError || message == UNKNOWN_SERVER_MUTATION) message
                                else if (state.jobsSource == ServerJobsSource.CACHE) "서버에 연결하지 못해 마지막으로 본 수신 결과를 표시합니다."
                                else if (state.errorActionLabel in setOf("프로필 인증 확인", "프로필 편집", "서버 환경 확인",
                                    "서버 인증서 확인", "프로젝트 권한 확인")) directServerOperatorMessage(message) else message,
                                Modifier.padding(horizontal = storageGutter, vertical = 8.dp),
                                tone = if (state.messageIsError) Tone.WARNING else Tone.INFO,
                                kind = GeoNoteKind.LIVE_ERROR, actionLabel = "다시 확인", onAction = refresh)
                        }
                        if (trash) ServerTrashPane(state, onRestore, onRefreshTrash, canMutate,
                            developerModeEnabled, onDeveloperTrash)
                        else if (state.capabilities == null && state.jobsSource == null && !state.isConnecting) {
                            ServerState("서버 연결 확인 필요", "서버 연결을 확인한 뒤 수신 결과를 볼 수 있습니다.", "연결 확인", onConnect)
                        } else BoxWithConstraints(Modifier.fillMaxSize()) {
                            val minimum = (320 + 360) * LocalDensity.current.fontScale.coerceAtLeast(1f)
                            val twoPanes = maxWidth - storageGutter * 2 - 24.dp >= minimum.dp
                            if (twoPanes) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                JobsList(state, onRefreshJobs, onOpenJob, onLoadMoreJobs, Modifier.weight(1f))
                                JobDetail(state, profile?.displayName.orEmpty(), onOpenDirectory, onOpenFile, onReceipt,
                                    Modifier.weight(1f), developerModeEnabled, showTechnicalInfo)
                            } else if (state.selectedJob != null) JobDetail(state, profile?.displayName.orEmpty(),
                                onOpenDirectory, onOpenFile, onReceipt, Modifier.fillMaxSize(), developerModeEnabled, showTechnicalInfo)
                            else JobsList(state, onRefreshJobs, onOpenJob, onLoadMoreJobs, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                GeoOperationMessageHost(state.mutationMessage, { onDismissMessage() },
                    "실행 취소".takeIf { state.undoSessionId != null }, onUndoTrash.takeIf { state.undoSessionId != null })
            }
        }
    }
}

@Composable
private fun ServerState(title: String, explanation: String, action: String? = null, onAction: (() -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(storageGutter, 56.dp)) {
        item { GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, title, explanation, action), onAction = onAction) }
    }
}

@Composable
private fun JobsList(state: DirectServerUiState, onRefresh: () -> Unit, onJob: (ServerJob) -> Unit,
    onLoadMore: () -> Unit, modifier: Modifier) {
    StorageRefresh(state.isLoading, !state.isConnecting, onRefresh, modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(storageGutter, 16.dp)) {
            items(state.jobs, key = { it.sessionId }) { job ->
                val status = serverJobState(job.state)
                GeoRow(job.sourceName, { onJob(job) },
                    meta = if (job.state == "UPLOADING") "${Formats.size(job.receivedBytes)} / ${Formats.size(job.totalBytes)}"
                        else "${browserDay(job.completedAt ?: job.updatedAt)} 수신 · ${Formats.size(job.totalBytes)}",
                    badge = { GeoBadge(status.word, status.tone) })
            }
            if (state.nextOffset != null) item {
                GeoButton("이전 수신 결과 더 불러오기", onLoadMore, Modifier.fillMaxWidth(), style = GeoButtonStyle.Secondary,
                    enabled = !state.isLoading && !state.isConnecting, disabledReason = "수신 결과를 불러오고 있습니다.")
            }
            if (state.jobs.isEmpty() && !state.isLoading && state.message == null) item {
                Spacer(Modifier.height(56.dp))
                GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "수신 결과가 없습니다", "서버가 파일을 받으면 여기에 표시됩니다."))
            }
        }
    }
}

@Composable
private fun JobDetail(state: DirectServerUiState, server: String, onDirectory: (ServerFileEntry) -> Unit,
    onFile: (ServerFileEntry) -> Unit, onReceipt: (ServerJob) -> Unit, modifier: Modifier,
    developer: Boolean, technical: Boolean) {
    val job = state.selectedJob
    if (job == null) {
        Box(modifier.padding(storageGutter)) { GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY,
            "수신 결과를 선택하세요", "파일과 서버 수신 영수증을 볼 수 있습니다.")) }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(storageGutter, 16.dp)) {
        item {
            Text(job.sourceName, style = LocalGeoType.current.boardTitle, color = LocalGeoColors.current.text)
            Spacer(Modifier.height(8.dp))
            val status = serverJobState(job.state)
            GeoBadge(status.word, status.tone)
            Text("${job.deviceId.takeIf { it.startsWith("GEO-X-", true) } ?: "장치"} → $server",
                style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
            GeoSection("정보", first = true, modifier = Modifier.padding(top = 24.dp)) {
                GeoFactRow("수신", Formats.time(job.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }))
                GeoFactRow("용량", Formats.size(job.totalBytes), numeric = true)
                GeoFactRow("파일", Formats.itemCount(job.fileCount.toLong()), numeric = true)
                if (job.state == "COMPLETED") GeoRow("서버 수신 영수증", { onReceipt(job) })
            }
            GeoSection(if (state.currentPath.isBlank()) "파일" else "파일 / ${state.currentPath.substringAfterLast('/')}") {}
            if (state.filesTruncated) GeoInlineNote("파일 목록 일부만 표시됩니다. 하위 폴더에서 다시 확인하세요.")
        }
        items(state.files, key = { it.relativePath }) { file ->
            val directory = file.type.equals("directory", true)
            GeoRow(file.name, { if (directory) onDirectory(file) else onFile(file) },
                meta = if (directory) "폴더" else serverFileCategory(file.name), value = file.sizeBytes?.let(Formats::size))
        }
        if (state.files.isEmpty() && !state.isLoading && state.message == null) item {
            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "이 폴더에 파일이 없습니다", "다른 폴더를 선택하세요."))
        }
        item { GeoTechInfo(listOf(GeoTechnicalValue("세션", job.sessionId), GeoTechnicalValue("장치", job.deviceId),
            GeoTechnicalValue("프로젝트", job.accessProjectId ?: job.projectId)), developer, technical) }
    }
}

private fun serverFileCategory(name: String): String = mediaCategory(com.example.jetsoncontroller.model.RemoteFileEntry(
    name, name, com.example.jetsoncontroller.model.RemoteEntryType.FILE, null, null))

@Composable
private fun ServerTrashPane(state: DirectServerUiState, onRestore: (String) -> Unit, onRefresh: () -> Unit,
    canMutate: Boolean, developer: Boolean, onDeveloperTrash: (() -> Unit)?) {
    var detail by remember(state.selectedProfileId) { mutableStateOf<ServerTrashJob?>(null) }
    detail?.let { job ->
        GeoInfoDialog(job.sourceName, { detail = null }) {
            GeoBadge(serverTrashStatus(job), if (serverTrashCanRestore(job)) Tone.UNKNOWN else Tone.PENDING)
            GeoFactRow("파일", Formats.itemCount(job.fileCount.toLong()), numeric = true)
            GeoFactRow("용량", Formats.size(job.totalBytes), numeric = true)
            GeoFactRow("이동", Formats.time(runCatching { Instant.parse(job.trashedAt) }.getOrNull()))
        }
    }
    StorageRefresh(state.isLoading, !state.isConnecting, onRefresh) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(storageGutter, 16.dp)) {
            item {
                Text(serverTrashCount(state.trash.size, state.trashTotal), style = LocalGeoType.current.body,
                    color = LocalGeoColors.current.textSecondary)
                if (developer && onDeveloperTrash != null) GeoRow("개발자 도구에서 비우기", onDeveloperTrash)
            }
            items(state.trash, key = { it.sessionId }) { job ->
                val restorable = serverTrashCanRestore(job)
                GeoActionRow(job.sourceName, if (restorable) "서버 작업 복원" else serverTrashStatus(job), { detail = job },
                    { onRestore(job.sessionId) }, meta = "${Formats.itemCount(job.fileCount.toLong())} · ${Formats.size(job.totalBytes)}",
                    actionEnabled = canMutate && restorable && !state.isLoading && !state.isConnecting,
                    disabledReason = if (!restorable) serverTrashStatus(job) else "서버 연결과 복원 권한을 확인하세요.")
            }
            if (state.trash.isEmpty() && !state.isLoading && state.message == null) item {
                Spacer(Modifier.height(56.dp))
                GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "휴지통이 비어 있습니다", "서버 휴지통으로 옮긴 수신 결과가 여기에 표시됩니다."))
            }
        }
    }
}

@Composable
private fun ReceiptDialog(receipt: ServerReceipt, server: String, onDismiss: () -> Unit,
    developer: Boolean, technical: Boolean) {
    GeoInfoDialog("서버 수신 영수증", onDismiss) {
        GeoBadge(if (receipt.matched) "내용 일치" else "내용 불일치",
            if (receipt.matched) Tone.SUCCESS else Tone.ERROR)
        Text("${receipt.sourceName} · $server", style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
        GeoFactRow("파일", Formats.itemCount(receipt.fileCount.toLong()), numeric = true)
        GeoFactRow("용량", Formats.size(receipt.totalBytes), numeric = true)
        GeoFactRow("완료", Formats.time(runCatching { Instant.parse(receipt.completedAt) }.getOrNull()))
        GeoFactRow("검증 시각", Formats.time(runCatching { Instant.parse(receipt.verifiedAt) }.getOrNull()))
        GeoTechInfo(listOf(GeoTechnicalValue("세션", receipt.sessionId), GeoTechnicalValue("SHA-256", receipt.contentSha256)), developer, technical)
    }
}

@Composable
private fun PreviewPane(preview: DirectServerPreview, modifier: Modifier) {
    val text = remember(preview.bytes) { previewText(preview.bytes) }
    when {
        preview.mediaType?.startsWith("video/") == true || serverFileCategory(preview.fileName) == "영상" -> VideoPreview(preview, modifier)
        preview.mediaType?.startsWith("image/") == true || serverFileCategory(preview.fileName) == "이미지" -> {
            val bitmap = remember(preview.bytes) { decodePreviewBitmap(preview.bytes) }
            if (bitmap != null) ZoomableImagePreview(preview.fileName, bitmap.asImageBitmap(),
                remember(preview.bytes) { previewImageDimensions(preview.bytes) }) else UnsupportedServerPreview(modifier)
        }
        text != null -> FilePreview(RemoteFileContent(preview.fileName, preview.mediaType.orEmpty(), preview.bytes))
        else -> UnsupportedServerPreview(modifier)
    }
}

@Composable
private fun UnsupportedServerPreview(modifier: Modifier) {
    Box(modifier.background(LocalGeoColors.current.surface).padding(storageGutter)) {
        GeoStateBlock(GeoStateContent(GeoStateKind.UNSUPPORTED, "앱에서 볼 수 없는 파일입니다",
            "이미지, 영상, 텍스트 파일만 앱에서 볼 수 있습니다."), fullScreen = true)
    }
}

internal fun defaultServerEnvironmentChoice(): ServerEnvironment? = null

internal fun directServerOperatorMessage(@Suppress("UNUSED_PARAMETER") message: String): String =
    "서버 인증을 확인하지 못했습니다. 관리자에게 서버 연결 설정 확인을 요청해 주세요."

internal fun directServerRoleCanMutate(role: String?): Boolean =
    role?.uppercase(Locale.ROOT) in setOf("OPERATOR", "ADMIN")

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
private fun VideoPreview(preview: DirectServerPreview, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var retry by remember(preview) { mutableIntStateOf(0) }
    var file by remember(preview) { mutableStateOf<java.io.File?>(null) }
    var failed by remember(preview) { mutableStateOf(false) }
    var prepared by remember(preview, retry) { mutableStateOf(false) }
    var playing by remember(preview, retry) { mutableStateOf(false) }
    var video by remember(preview, retry) { mutableStateOf<android.widget.VideoView?>(null) }
    val suffix = when {
        preview.mediaType?.contains("webm") == true -> ".webm"
        preview.mediaType?.contains("quicktime") == true -> ".mov"
        else -> ".mp4"
    }
    LaunchedEffect(preview, retry) {
        failed = false
        file = null
        var candidate: java.io.File? = null
        try {
            withContext(Dispatchers.IO) {
                val target = java.io.File.createTempFile("server-preview-", suffix, context.cacheDir)
                candidate = target
                target.writeBytes(preview.bytes)
            }
            currentCoroutineContext().ensureActive()
            file = candidate
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { candidate?.delete() }
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, preview, retry) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { video?.pause(); playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); video?.stopPlayback(); video = null }
    }
    Box(modifier.background(Color.Black)) {
        when {
            failed -> Column(Modifier.align(Alignment.Center).padding(storageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("영상 미리 보기를 준비하지 못했습니다. 다시 시도하세요.",
                    style = LocalGeoType.current.body, color = Color.White)
                GeoButton("다시 시도", { retry += 1 }, Modifier.fillMaxWidth())
            }
            file != null -> key(preview, retry, file) {
                AndroidView(factory = { viewContext ->
                    android.widget.VideoView(viewContext).apply {
                        video = this
                        setVideoPath(file!!.absolutePath)
                        setMediaController(android.widget.MediaController(viewContext).also { it.setAnchorView(this) })
                        setOnPreparedListener { prepared = true; it.isLooping = false; seekTo(1) }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
                    }
                }, modifier = Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    GeoButton(if (playing) "일시 정지" else "재생", {
                        if (playing) video?.pause() else video?.start()
                        playing = !playing
                    }, enabled = prepared, disabledReason = "영상을 준비하고 있습니다.")
                }
            }
            else -> Box(Modifier.align(Alignment.Center).padding(storageGutter)) { GeoProgress() }
        }
    }
}
