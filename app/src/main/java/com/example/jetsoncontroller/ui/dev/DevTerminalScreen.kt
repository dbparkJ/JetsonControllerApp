package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.ui.dev.DevSection as GeoSection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow

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
    val focus = LocalFocusManager.current
    pending?.let { target ->
        GeoConfirmDialog("${deviceName}에서 명령을 실행할까요?", "장치의 수집 사용자 권한으로 실행합니다.", "명령 실행",
            onConfirm = { pending = null
                if (target.canExecute(state.deviceId, state.online, state.terminalBusy)) onExecute(target.command)
            }, onDismissRequest = { pending = null }, kind = GeoConfirmKind.DANGER,
            confirmEnabled = target.canExecute(state.deviceId, state.online, state.terminalBusy),
            disabledReason = "장치에 연결되고 실행 중인 명령이 없어야 합니다.",
            facts = { SelectionContainer { DevText(target.command, mono = true) } })
    }
    DevFrame("원격 터미널", onBack, primaryAction = GeoButtonAction("명령 실행",
        { state.deviceId?.let { pending = RemoteCommandTarget(it, command) } },
        enabled = canExecute, busy = state.terminalBusy, busyLabel = "명령 실행 중",
        disabledReason = if (!state.online) "장치 연결 후 명령을 실행할 수 있습니다." else null)) {
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val logHeight = maxHeight / 2
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LocalGeoWindow.current.gutterDp.dp).padding(bottom = 24.dp)) {
        DevIdentity(deviceName, "단일 명령 · 최대 15초 · 출력 64KB", status = if (state.online) "연결됨" else "연결 안 됨",
            tone = if (state.online) Tone.SUCCESS else Tone.UNKNOWN)
        Spacer(Modifier.height(24.dp))
        GeoTextField(command, { if (it.length <= 4096) command = it }, "명령어", placeholder = "pwd",
            singleLine = true, enabled = !state.terminalBusy, disabledReason = "명령을 실행하고 있습니다.",
            inputKind = GeoInputKind.IDENTIFIER, imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        DevFeedback(state.error, true)
        GeoSection("출력", first = true, modifier = Modifier.padding(top = 24.dp)) {
            if (state.terminalOutput.isNotBlank()) GeoLogView(
                state.terminalOutput.lines().mapIndexed { index, line -> GeoLogLine(index.toString(), line) },
                live = false, modifier = Modifier.fillMaxWidth().height(logHeight).geoContainScroll())
            else GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "출력 없음", "명령을 실행하면 결과가 여기에 표시됩니다."))
        }
        Spacer(Modifier.height(16.dp))
        DevText("$deviceName · ${if (state.terminalBusy) "명령을 실행하고 있습니다." else if (state.online) "명령을 실행할 수 있습니다." else "장치에 연결하면 명령을 실행할 수 있습니다."}", secondary = true)
        }
      }
    }
}
