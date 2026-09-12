package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(state: FieldState, onBack: () -> Unit, onExecute: (String) -> Unit,
    onLogs: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("geo_developer", 0) }
    var enabled by remember { mutableStateOf(preferences.getBoolean("enabled", false)) }
    var command by rememberSaveable(state.deviceId) { mutableStateOf("") }
    var confirm by remember(state.deviceId, state.online) { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("원격 명령 실행") },
        text = { Text("대상 장치: ${state.deviceId}\n\n$command\n\n장치의 수집 사용자 권한으로 실행됩니다.") },
        confirmButton = { TextButton(onClick = { confirm = false; onExecute(command) }, enabled = enabled && state.online && !state.terminalBusy) { Text("실행") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
    Scaffold(topBar = { TopAppBar(title = { Text("개발자 옵션") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row { Text("개발자 모드", Modifier.weight(1f)); Switch(enabled, {
                enabled = it; preferences.edit().putBoolean("enabled", it).apply()
            }) }
            if (enabled) {
                OutlinedButton(onClick = onLogs, modifier = Modifier.fillMaxWidth()) { Text("저장된 작업 로그 확인") }
                OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("앱 연결 로그 · 진단 내보내기") }
                HorizontalDivider()
                Text("원격 터미널", style = MaterialTheme.typography.titleLarge)
                Text("장치: ${state.deviceId ?: "선택 안 됨"}\n단일 명령 · 최대 15초 · 출력 64KB", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = command, onValueChange = { if (it.length <= 4096) command = it },
                    label = { Text("명령어") }, placeholder = { Text("pwd") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    enabled = !state.terminalBusy)
                Button(onClick = { confirm = true }, enabled = state.online && !state.terminalBusy && command.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (state.terminalBusy) "명령 실행 중…" else "명령 실행") }
                if (!state.online) Text("장치 LAN 또는 Wi-Fi Direct 연결이 필요합니다.")
                SelectionContainer { Text(state.terminalOutput, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            } else Text("개발자 모드를 껐습니다. 설정의 빌드 번호를 7번 눌러 다시 활성화할 수 있습니다.")
        }
    }
}
