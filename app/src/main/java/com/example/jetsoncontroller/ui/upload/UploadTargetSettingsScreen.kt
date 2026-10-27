package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.ui.storage.R7Section as GeoSection
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.model.Tone
import java.net.URI

@Composable
fun UploadTargetSettingsScreen(
    targets: List<UploadTarget>, isLoading: Boolean, message: String?, error: String?,
    onSave: (targetId: String, label: String, baseUrl: String, token: String?) -> Unit,
    onDelete: (targetId: String) -> Unit, onRefresh: () -> Unit, onClearFeedback: () -> Unit,
    onBack: () -> Unit, controlAvailable: Boolean = true, deviceId: String? = null,
    onOpenEditor: ((UploadTarget?) -> Unit)? = null, deviceName: String = "선택한 장치"
) {
    var editing by remember(deviceId) { mutableStateOf(false) }
    var selected by remember(deviceId) { mutableStateOf<UploadTarget?>(null) }
    fun open(target: UploadTarget?) {
        onClearFeedback()
        if (onOpenEditor != null) onOpenEditor(target) else { selected = target; editing = true }
    }
    if (editing) {
        UploadTargetEditorScreen(selected, isLoading, controlAvailable, deviceId, deviceName,
            message, error, onSave, onDelete, { editing = false })
        return
    }
    DevPage("업로드 서버", onBack, actions = {
        GeoIconButton(GeoIcons.add, "서버 추가", { open(null) }, enabled = controlAvailable, chrome = true)
    }) {
        DevFeedback(message)
        error?.let {
            GeoInlineNote("업로드 서버를 불러오지 못했습니다. 장치 연결을 확인하세요.",
                tone = if (targets.isEmpty()) Tone.ERROR else Tone.WARNING)
        }
        if (!controlAvailable) DevText("장치에 연결하면 서버를 설정할 수 있습니다.")
        when {
            isLoading -> DevLoading()
            error != null && targets.isEmpty() -> Unit
            targets.isEmpty() -> DevEmpty("등록된 업로드 서버가 없습니다", "서버를 추가하면 여기에 표시됩니다.", "서버 추가", { open(null) })
            else -> GeoSection("서버", first = true, count = "${targets.size}개", note = "${deviceName}에 저장된 서버입니다.",
                trailingColumn = GeoRowTrailing.None) {
                targets.forEachIndexed { index, target ->
                    GeoRow(target.label, { open(target) }, meta = target.baseUrl ?: "주소 미확인",
                        leading = { GeoIconTile(GeoIcons.router) }, divider = index < targets.lastIndex)
                }
            }
        }
        GeoButton("다시 조회", onRefresh, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start, enabled = controlAvailable && !isLoading)
    }
}

@Composable
internal fun UploadTargetEditorScreen(target: UploadTarget?, isSaving: Boolean, controlAvailable: Boolean,
    deviceId: String?, deviceName: String, message: String?, error: String?,
    onSave: (String, String, String, String?) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
    var id by rememberSaveable(deviceId, target?.id) { mutableStateOf(target?.id ?: "") }
    var label by rememberSaveable(deviceId, target?.id) { mutableStateOf(target?.label ?: "") }
    var url by rememberSaveable(deviceId, target?.id) { mutableStateOf(target?.baseUrl ?: "") }
    // Tokens are deliberately absent from saved state, logs, feedback and existing target data.
    val secret = remember(deviceId, target?.id) { DevSecretInput() }
    var token by secret
    var pending by remember(deviceId, controlAvailable) { mutableStateOf<UploadTarget?>(null) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(isSaving, message, submitted) {
        if (submitted && !isSaving && message != null && error == null) { token = ""; onBack() }
    }
    val valid = id.matches(Regex("[a-z0-9][a-z0-9.-]{0,63}")) && label.isNotBlank() &&
        isValidHttpsUrl(url) && (target != null || token.isNotBlank()) && token.none(Char::isWhitespace)
    pending?.let { frozen ->
        GeoConfirmDialog("${devObject(frozen.label)} 삭제할까요?", "${deviceName}에 저장된 서버 주소와 접근 토큰을 지웁니다.", "서버 삭제",
            { pending = null; if (controlAvailable && !isSaving && target?.id == frozen.id) { submitted = true; onDelete(frozen.id) } },
            { pending = null }, kind = GeoConfirmKind.DANGER, confirmEnabled = controlAvailable && !isSaving,
            disabledReason = "장치에 연결하면 삭제할 수 있습니다.")
    }
    DevPage(if (target == null) "업로드 서버 추가" else "업로드 서버 수정", { token = ""; onBack() }, primaryAction =
        GeoButtonAction("저장", {
            val credential = secret.consume().trim().ifEmpty { null }
            submitted = true
            onSave(id.trim(), label.trim(), url.trim().trimEnd('/'), credential)
        }, enabled = valid && controlAvailable && !isSaving,
            busy = isSaving, busyLabel = "저장 중",
            disabledReason = if (!controlAvailable) "장치에 연결하면 저장할 수 있습니다." else "서버 정보를 입력하세요.")) {
        DevFeedback(if (submitted && error != null) "서버 정보를 저장하지 못했습니다. 입력과 연결을 확인하세요." else null, true)
        if (target != null) UploadTargetIdFactRow(id)
        else GeoTextField(id, { id = it.lowercase().take(64) }, "서버 ID",
            inputKind = GeoInputKind.IDENTIFIER, imeAction = ImeAction.Next, keyboardActions = next)
        Spacer(Modifier.height(16.dp))
        GeoTextField(label, { label = it.take(64) }, "표시 이름", imeAction = ImeAction.Next, keyboardActions = next)
        Spacer(Modifier.height(16.dp))
        GeoTextField(url, { url = it.take(2048) }, "HTTPS 주소", error = if (url.isNotBlank() && !isValidHttpsUrl(url)) "HTTPS 주소를 확인하세요." else null,
            inputKind = GeoInputKind.SERVER_ADDRESS, imeAction = ImeAction.Next, keyboardActions = next)
        Spacer(Modifier.height(16.dp))
        GeoTextField(token, { token = it.take(4096) }, "접근 토큰", password = true,
            helper = "저장된 토큰은 다시 표시하지 않습니다. 바꾸려면 새 토큰을 입력하세요.",
            inputKind = GeoInputKind.IDENTIFIER, imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        if (target != null) GeoSection("관리", danger = true) {
            DevText("${deviceName}에 저장된 서버 주소와 접근 토큰을 지웁니다.")
            GeoButton("서버 삭제", { pending = target }, Modifier.fillMaxWidth(), style = GeoButtonStyle.DangerOutline,
                enabled = controlAvailable && !isSaving)
        }
    }
}

internal fun isValidHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
}.getOrDefault(false)

@Composable
private fun UploadTargetIdFactRow(value: String) {
    GeoCopyableValue(value, label = "서버 ID")
}
