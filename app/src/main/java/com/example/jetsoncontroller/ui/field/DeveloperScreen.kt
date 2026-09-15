package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.AdaptiveContent
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.slateTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    state: FieldState,
    onBack: () -> Unit,
    onExecute: (String) -> Unit,
    onLogs: () -> Unit,
    onDiagnostics: () -> Unit,
    developerModeEnabled: Boolean = true,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onDeveloperDisabled: () -> Unit = {},
    onAdminTools: () -> Unit = {}
) {
    if (!developerModeEnabled) {
        LaunchedEffect(Unit) { onDeveloperDisabled() }
        return
    }
    var command by rememberSaveable(state.deviceId) { mutableStateOf("") }
    var confirm by remember(state.deviceId, state.online) { mutableStateOf(false) }
    var terminalExpanded by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("원격 명령 실행") },
            text = {
                Text(
                    "대상 장치: ${state.deviceId}\n\n$command\n\n" +
                        "장치의 수집 사용자 권한으로 실행됩니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onExecute(command)
                    },
                    enabled = developerModeEnabled && state.online && !state.terminalBusy
                ) { Text("실행") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        containerColor = LocalGeoColors.current.canvas,
        topBar = {
            TopAppBar(
                title = { Text("개발자 도구") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "개발자 도구 메뉴")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (terminalExpanded) "원격 터미널 닫기" else "원격 터미널 열기") },
                            onClick = {
                                menuOpen = false
                                terminalExpanded = !terminalExpanded
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = GeoSpace.sm),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
            ) {
                item {
                    Surface(
                        color = LocalGeoColors.current.surface,
                        contentColor = LocalGeoColors.current.ink,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = GeoSize.primaryAction)
                                .toggleable(
                                    value = true,
                                    role = Role.Switch,
                                    onValueChange = { enabled ->
                                        if (!enabled) {
                                            onDeveloperModeChange(false)
                                            onDeveloperDisabled()
                                        }
                                    }
                                )
                                .padding(GeoSpace.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
                        ) {
                            Text("개발자 모드 켜짐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = true, onCheckedChange = null)
                        }
                    }
                }
                item {
                    Surface(
                        color = LocalGeoColors.current.surface,
                        contentColor = LocalGeoColors.current.ink,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(horizontal = GeoSpace.xl)) {
                            DeveloperToolRow(Icons.Default.Wifi, "연결 진단", "주소 · 인증 · 원시 오류", onDiagnostics)
                            GeoRowDivider()
                            DeveloperToolRow(Icons.Default.ListAlt, "수집 작업 관리", "설정 · 실행 로그 · 식별자", onLogs)
                            GeoRowDivider()
                            DeveloperToolRow(Icons.Default.CloudQueue, "서버·전송 설정", "서버 주소 · 프로필 · 수신 진단", onAdminTools)
                            GeoRowDivider()
                            DeveloperToolRow(Icons.Default.Settings, "장치 관리", "팬 · 전원 · 등록 관리", onAdminTools)
                        }
                    }
                }
                if (terminalExpanded) item {
                    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                        Text("원격 터미널", style = MaterialTheme.typography.titleLarge)
                Text(
                    "장치: ${state.deviceId ?: "선택 안 됨"}\n" +
                        "단일 명령 · 최대 15초 · 출력 64KB",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { if (it.length <= 4096) command = it },
                    label = { Text("명령어") },
                    placeholder = { Text("pwd") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    enabled = !state.terminalBusy,
                    colors = slateTextFieldColors()
                )
                Button(
                    onClick = { confirm = true },
                    enabled = state.online && !state.terminalBusy && command.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction)
                ) {
                    Text(if (state.terminalBusy) "명령 실행 중…" else "명령 실행")
                }
                if (!state.online) {
                    Text("장치 연결 후 명령을 실행할 수 있습니다.")
                }
                if (state.terminalOutput.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            state.terminalOutput,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                    }
                }
                item {
                    TextButton(onClick = onBack) { Text("설정으로 돌아가기") }
                }
            }
        }
    }
}

@Composable
private fun DeveloperToolRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = LocalGeoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick)
            .padding(vertical = GeoSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
    ) {
        Icon(icon, null, tint = colors.muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}
