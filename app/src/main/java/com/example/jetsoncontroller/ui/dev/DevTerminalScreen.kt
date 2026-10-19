package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.model.Tone

internal data class RemoteCommandTarget(val deviceId: String, val command: String) {
    fun canExecute(deviceId: String?, online: Boolean, busy: Boolean): Boolean =
        this.deviceId.equals(deviceId, true) && online && !busy && command.isNotBlank() && command.length <= 4096
}

@Composable
internal fun DevTerminalScreen(state: FieldState, onExecute: (String) -> Unit, onBack: () -> Unit,
    deviceName: String = state.deviceId ?: "선택한 장치") {
    var command by rememberSaveable(state.deviceId) { mutableStateOf("") }
    var pending by remember(state.deviceId, state.online) { mutableStateOf<RemoteCommandTarget?>(null) }
    val canExecute = state.deviceId != null && state.online && !state.terminalBusy && command.isNotBlank()
    pending?.let { target ->
        GeoConfirmDialog("${deviceName}에서 명령을 실행할까요?", "장치의 수집 사용자 권한으로 실행합니다.", "실행",
            onConfirm = { pending = null
                if (target.canExecute(state.deviceId, state.online, state.terminalBusy)) onExecute(target.command)
            }, onDismissRequest = { pending = null }, kind = GeoConfirmKind.DANGER,
            confirmEnabled = target.canExecute(state.deviceId, state.online, state.terminalBusy),
            disabledReason = "장치에 연결되고 실행 중인 명령이 없어야 합니다.",
            facts = { SelectionContainer { DevText(target.command, mono = true) } })
    }
    DevPage("원격 터미널", onBack, footer = {
        GeoButton("명령 실행", { state.deviceId?.let { pending = RemoteCommandTarget(it, command) } },
            Modifier.fillMaxWidth(), enabled = canExecute, busy = state.terminalBusy, busyLabel = "명령 실행 중",
            disabledReason = if (!state.online) "장치 연결 후 명령을 실행할 수 있습니다." else null, actionArea = true)
    }) {
        DevIdentity(deviceName, "단일 명령 · 최대 15초 · 출력 64KB", status = if (state.online) "연결됨" else "연결 안 됨",
            tone = if (state.online) Tone.SUCCESS else Tone.UNKNOWN)
        Spacer(Modifier.height(24.dp))
        GeoTextField(command, { if (it.length <= 4096) command = it }, "명령어", placeholder = "pwd",
            singleLine = false, enabled = !state.terminalBusy, disabledReason = "명령을 실행하고 있습니다.")
        DevFeedback(state.error, true)
        if (state.terminalOutput.isNotBlank()) GeoSection("출력") {
            SelectionContainer { DevText(state.terminalOutput, mono = true) }
        }
    }
}
