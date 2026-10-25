package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.ui.storage.R7Section as GeoSection
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.localDeviceWord
import com.example.jetsoncontroller.ui.storage.*

internal fun validProfileToken(value: String): Boolean = value.startsWith("emp_") && value.length > 4 && value.none(Char::isWhitespace)

internal data class ServerPurgeTarget(val profileId: String, val sessions: List<String>) {
    fun valid(profileId: String?, eligible: List<String>, supported: Boolean, busy: Boolean): Boolean =
        this.profileId == profileId && supported && !busy && sessions.isNotEmpty() && sessions.size <= 200 &&
            sessions.distinct().size == sessions.size && sessions.all { it in eligible }
}

@Composable
internal fun DevServerProfilesScreen(state: DirectServerUiState, onBack: () -> Unit,
    onEdit: (String?) -> Unit, onConnect: (String) -> Unit) {
    DevPage("서버 프로필", onBack, actions = { GeoIconButton(GeoIcons.add, "프로필 추가", { onEdit(null) }, chrome = true) }) {
        if (state.profiles.isEmpty()) DevEmpty("서버 연결 정보가 없습니다", "관리자에게 서버 연결 정보를 받아 설정하세요.", "프로필 추가", { onEdit(null) })
        else GeoSection("프로필", first = true, trailingColumn = GeoRowTrailing.None) {
            state.profiles.forEach { stored ->
                DevAddressRow(stored.profile.displayName, stored.profile.baseUrl, { onEdit(stored.profile.profileId) })
            }
        }
        DevFeedback(state.message?.let { if (state.messageIsError) "서버 연결 정보를 확인하지 못했습니다. 다시 시도하세요." else it }, state.messageIsError)
    }
}

@Composable
internal fun DevServerProfileEditor(state: DirectServerUiState, profileId: String?, onBack: () -> Unit,
    onSave: (ServerEndpointProfile, String) -> Unit, onConnect: (String) -> Unit, onRemove: (String) -> Unit) {
    val deviceWord = localDeviceWord()
    val profile = state.profiles.firstOrNull { it.profile.profileId == profileId }?.profile
    if (profileId != null && profile == null) {
        DevPage("서버 프로필 수정", onBack) { DevEmpty("프로필을 찾을 수 없습니다", "서버 프로필 목록을 다시 확인하세요.") }
        return
    }
    var id by rememberSaveable(profileId) { mutableStateOf(profile?.profileId ?: "") }
    var name by rememberSaveable(profileId) { mutableStateOf(profile?.displayName ?: "") }
    var environment by rememberSaveable(profileId) { mutableIntStateOf(profile?.environment?.ordinal ?: 0) }
    var url by rememberSaveable(profileId) { mutableStateOf(profile?.baseUrl ?: "") }
    var employee by rememberSaveable(profileId) { mutableStateOf(profile?.employeeId ?: "") }
    var project by rememberSaveable(profileId) { mutableStateOf(profile?.projectId ?: "") }
    val secret = remember(profileId) { DevSecretInput() }
    var token by secret
    var pending by remember(profileId) { mutableStateOf<ServerEndpointProfile?>(null) }
    val candidate = runCatching { ServerEndpointProfile(id, name, ServerEnvironment.entries[environment], url.trim(), employee, project).validated() }.getOrNull()
    val busy = state.isLoading || state.isConnecting
    pending?.let { frozen ->
        GeoConfirmDialog("${devObject(frozen.displayName)} 삭제할까요?",
            "이 ${deviceWord}에 저장된 서버 주소와 직원 인증 정보, 저장된 목록을 지웁니다. 서버의 데이터는 그대로입니다.", "프로필 삭제",
            { pending = null; if (!busy && state.profiles.any { it.profile == frozen }) { token = ""; onRemove(frozen.profileId); onBack() } },
            { pending = null }, kind = GeoConfirmKind.DANGER, confirmEnabled = !busy,
            disabledReason = "서버 요청이 끝난 뒤 삭제할 수 있습니다.")
    }
    DevPage(if (profile == null) "서버 프로필 추가" else "서버 프로필 수정", { token = ""; onBack() }, footer = {
        GeoButtonActions(buildList {
            if (profile != null) add(GeoButtonAction("이 프로필로 연결", { token = ""; onConnect(profile.profileId) },
                style = GeoButtonStyle.Secondary, enabled = !busy))
            add(GeoButtonAction("암호화해 저장", {
                val credential = secret.consume()
                candidate?.let { onSave(it, credential) }
            }, enabled = candidate != null && validProfileToken(token) && !busy,
                busy = busy, busyLabel = "저장 중",
                disabledReason = "프로필 정보와 관리자에게 받은 직원 토큰을 입력하세요."))
        })
    }) {
        if (profile != null) GeoFactRow("프로필 ID", id)
        else GeoTextField(id, { id = it.lowercase().take(64) }, "프로필 ID")
        Spacer(Modifier.height(16.dp))
        GeoTextField(name, { name = it.take(128) }, "표시 이름")
        Spacer(Modifier.height(16.dp))
        DevChoice("환경", listOf("개발", "시험", "운영"), environment, { environment = it })
        Spacer(Modifier.height(16.dp))
        GeoTextField(url, { url = it.take(2048) }, "HTTPS 서버 주소")
        Spacer(Modifier.height(16.dp))
        GeoTextField(employee, { employee = it.lowercase().take(64) }, "직원 ID")
        Spacer(Modifier.height(16.dp))
        GeoTextField(project, { project = it.lowercase().take(64) }, "프로젝트 ID")
        Spacer(Modifier.height(16.dp))
        GeoTextField(token, { token = it.take(4096) }, "직원 토큰", password = true, helper = "저장한 토큰은 다시 보여 주지 않습니다.")
        // Do not render arbitrary exception messages, even if a service echoes a submitted token.
        DevFeedback(state.message?.let { if (state.messageIsError) "서버 연결 정보를 저장하지 못했습니다. 입력과 연결을 확인하세요." else "서버 연결 정보를 저장했습니다." }, state.messageIsError)
        if (profile != null) GeoSection("관리", danger = true) {
            DevText("이 ${deviceWord}에 저장된 서버 주소와 직원 인증 정보를 지웁니다.")
            GeoButton("프로필 삭제", { pending = profile }, Modifier.fillMaxWidth(), style = GeoButtonStyle.DangerOutline, enabled = !busy)
        }
    }
}

@Composable
internal fun DevServerTrashScreen(state: DirectServerUiState, onBack: () -> Unit, onRefresh: () -> Unit,
    onConnect: () -> Unit, onPurge: (String, List<String>) -> Unit) {
    var pending by remember(state.selectedProfileId) { mutableStateOf<ServerPurgeTarget?>(null) }
    val eligible = state.trash.filter(::isServerPurgeEligible).map { it.sessionId }
    val busy = state.isLoading || state.isConnecting || state.isEmptyingTrash
    val profile = state.profiles.firstOrNull { it.profile.profileId == state.selectedProfileId }?.profile
    val content = serverTrashContent(profile != null, busy, state.messageIsError, state.trash.isNotEmpty())
    pending?.let { frozen ->
        val count = frozen.sessions.size
        val body = "표시된 ${count}개 항목을 서버에서 영구 삭제합니다. 되돌릴 수 없습니다." +
            if (state.trashTotal > count) " 전체 ${state.trashTotal}개 중 표시된 ${count}개만 지웁니다." else ""
        GeoConfirmDialog("서버 휴지통을 비울까요?", body, "영구 삭제", {
            pending = null
            if (profile?.profileId == frozen.profileId && frozen.valid(state.selectedProfileId, eligible, state.trashEmptySupported, busy)) onPurge(frozen.profileId, frozen.sessions)
        }, { pending = null }, kind = GeoConfirmKind.DANGER,
            confirmEnabled = profile?.profileId == frozen.profileId && frozen.valid(state.selectedProfileId, eligible, state.trashEmptySupported, busy),
            disabledReason = "서버와 표시된 휴지통 항목을 다시 확인하세요.")
    }
    DevPage("서버 휴지통", onBack, footer = if (profile != null && state.trashEmptySupported && eligible.isNotEmpty()) ({
        GeoButton("표시된 ${eligible.size}개 비우기", {
            state.selectedProfileId?.let { pending = ServerPurgeTarget(it, eligible.toList()) }
        }, Modifier.fillMaxWidth(), style = GeoButtonStyle.DangerOutline, enabled = !busy, actionArea = true)
    }) else null) {
        if (content == ServerTrashContent.NO_PROFILE) {
            DevEmpty("서버 연결 정보가 없습니다", "관리자에게 서버 연결 정보를 받아 설정하세요.")
            return@DevPage
        }
        DevText(listOfNotNull(profile?.displayName, state.refreshedAt?.let { "마지막 동기화 ${devTimestamp(it)}" }).joinToString(" · "))
        if (state.capabilities == null) GeoButton("연결 확인", onConnect, style = GeoButtonStyle.Secondary, enabled = !busy)
        GeoSection("휴지통", first = true, modifier = Modifier.padding(top = 24.dp)) {
            if (content == ServerTrashContent.CONTENT || content == ServerTrashContent.EMPTY)
                DevText("전체 ${state.trashTotal}개 중 ${state.trash.size}개를 표시합니다.")
            state.trash.forEach { job ->
                GeoFactRow(job.sourceName, "${Formats.size(job.totalBytes)} · ${if (job.state == "PURGING") "삭제 처리 중" else if (job.restoreSupported == false) "복원할 수 없음" else "복원 가능"}")
            }
            if (content == ServerTrashContent.EMPTY) DevEmpty("서버 휴지통이 비어 있습니다", "휴지통으로 옮긴 수신 결과가 여기에 표시됩니다.")
            if (content == ServerTrashContent.ERROR) DevEmpty("서버 휴지통을 불러오지 못했습니다", "서버 연결을 확인하고 다시 시도하세요.", "다시 시도", onRefresh)
            if (!state.trashEmptySupported && state.capabilities != null) DevText("이 서버에서는 휴지통 비우기를 지원하지 않습니다.")
            if (busy) DevLoading()
            DevFeedback(state.message, state.messageIsError)
            GeoButton("다시 조회", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = !busy)
        }
    }
}
