package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.connection.userConnectionStage
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.ThemeMode
import com.example.jetsoncontroller.ui.theme.rememberThemePreference

/** Operator-facing preferences. Administrative device controls live in [AdminToolsScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    state: DashboardUiState, deviceId: String?, unreadCount: Int,
    onDevices: () -> Unit, onAlerts: () -> Unit, onNetwork: () -> Unit,
    onSensors: () -> Unit, onTargets: () -> Unit, onDiagnostics: () -> Unit,
    onAlertSettings: () -> Unit, onServerStorage: () -> Unit,
    onRefreshFan: () -> Unit, onFanAuto: () -> Unit, onFanManual: (Int) -> Unit,
    onReboot: () -> Unit, onShutdown: () -> Unit,
    onDismissMessage: () -> Unit, onSection: (ControlSection) -> Unit,
    onDeveloper: () -> Unit = {}, onAdminTools: () -> Unit = {},
    developerModeEnabled: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {}
) {
    val (theme, setTheme) = rememberThemePreference()
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var developerUnlockTaps by rememberSaveable { mutableIntStateOf(0) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val registerDeveloperUnlockTap = {
        if (!developerModeEnabled) {
            developerUnlockTaps += 1
            if (developerUnlockTaps >= 7) {
                developerUnlockTaps = 0
                aboutOpen = false
                onDeveloperModeChange(true)
            }
        }
    }

    if (themePickerOpen) {
        AlertDialog(
            onDismissRequest = { themePickerOpen = false },
            title = { Text("화면 테마") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                    ThemeMode.entries.forEach { value ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = theme == value,
                                    role = Role.RadioButton,
                                    onValueChange = {
                                        setTheme(value)
                                        themePickerOpen = false
                                    }
                                )
                                .padding(vertical = GeoSpace.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = theme == value, onClick = null)
                            Text(value.label, Modifier.padding(start = GeoSpace.md))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { themePickerOpen = false }) { Text("취소") }
            }
        )
    }
    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = {
                Text(
                    "앱 정보",
                    modifier = Modifier.testTag("developer-unlock").clickable(
                        enabled = !developerModeEnabled,
                        onClick = registerDeveloperUnlockTap
                    )
                )
            },
            text = {
                Text(
                    "GEO& Jetson Controller\n버전 ${com.example.jetsoncontroller.BuildConfig.VERSION_NAME}",
                    modifier = Modifier.clickable(
                        enabled = !developerModeEnabled,
                        onClick = registerDeveloperUnlockTap
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutOpen = false }) { Text("확인") }
            }
        )
    }
    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text("현장 수집 도움말") },
            text = {
                Text(
                    "장치 연결 → 조사 구간 선택 → 장치 점검 → 수집 순서로 진행합니다. " +
                        "수집 후 파일에서 저장 결과와 서버 수신을 확인할 수 있습니다."
                )
            },
            confirmButton = {
                Button(onClick = { helpOpen = false }) { Text("도움말 닫기") }
            }
        )
    }

    Scaffold(
        containerColor = LocalGeoColors.current.canvas,
        topBar = {
            TopAppBar(
                title = { Text("설정", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { onSection(ControlSection.OVERVIEW) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "홈으로 돌아가기")
                    }
                },
                actions = { AlertIconButton(unreadCount, onAlerts) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalGeoColors.current.canvas)
            )
        },
        bottomBar = { ControlNavigationBar(ControlSection.SETTINGS, onSection) }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1240.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = GeoSpace.sm, bottom = GeoSpace.lg),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.section)
            ) {
                item {
                    AdaptiveColumns(
                        modifier = Modifier.fillMaxWidth(),
                        first = {
                            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                                CurrentDevicePreference(
                                    deviceName = state.deviceName,
                                    connected = state.isOnline,
                                    onClick = onDevices
                                )
                                SettingsSection("앱 설정") {
                                    SettingsPreferenceRow(
                                        icon = Icons.Default.Palette,
                                        title = "화면 테마",
                                        value = theme.label,
                                        onClick = { themePickerOpen = true }
                                    )
                                    GeoRowDivider()
                                    SettingsPreferenceRow(
                                        icon = Icons.Default.Notifications,
                                        title = "알림",
                                        value = if (unreadCount > 0) "새 알림 ${unreadCount}개" else "켜짐",
                                        onClick = onAlertSettings,
                                        alert = unreadCount > 0
                                    )
                                    GeoRowDivider()
                                    SettingsPreferenceRow(
                                        icon = Icons.Default.Wifi,
                                        title = "네트워크",
                                        value = "장비 Wi-Fi 선택",
                                        onClick = onNetwork
                                    )
                                    GeoRowDivider()
                                    SettingsPreferenceRow(
                                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                                        title = "도움말",
                                        onClick = { helpOpen = true }
                                    )
                                }
                            }
                        },
                        second = {
                            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                                SettingsPreferenceRow(
                                    icon = null,
                                    title = "앱 정보",
                                    value = "GEO& ${com.example.jetsoncontroller.BuildConfig.VERSION_NAME}",
                                    onClick = {
                                        developerUnlockTaps = if (developerModeEnabled) 0 else 1
                                        aboutOpen = true
                                    },
                                    standalone = true
                                )
                                if (developerModeEnabled) {
                                    SettingsSection("고급") {
                                        DeveloperModeRow(
                                            enabled = true,
                                            onEnabledChange = onDeveloperModeChange
                                        )
                                    }
                                    SettingsSection("개발자 도구") {
                                        SettingsPreferenceRow(
                                            icon = Icons.Default.Terminal,
                                            title = "기술 정보와 관리 도구",
                                            value = "진단 · 서버 · 작업 · 장치 제어",
                                            onClick = onDeveloper
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentDevicePreference(
    deviceName: String,
    connected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
    ) {
        Row(
            Modifier.padding(GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Surface(color = c.brandSoft, shape = MaterialTheme.shapes.small) {
                Icon(
                    Icons.Default.DeveloperBoard,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.padding(GeoSpace.md).size(GeoSize.iconLg)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (connected) "연결된 장치" else "현재 선택한 장치 · 연결 안 됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted
                )
            }
            Icon(Icons.Default.ChevronRight, "장치 변경", tint = c.muted)
        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text(
            label,
            style = com.example.jetsoncontroller.ui.theme.GeoType.eyebrow,
            color = LocalGeoColors.current.muted
        )
        Surface(
            color = LocalGeoColors.current.surface,
            contentColor = LocalGeoColors.current.ink,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsPreferenceRow(
    icon: ImageVector?,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    alert: Boolean = false,
    standalone: Boolean = false
) {
    val c = LocalGeoColors.current
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = GeoSize.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            if (icon != null) Icon(icon, null, tint = c.muted, modifier = Modifier.size(GeoSize.iconMd))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (alert) Badge { Text("!") }
            Icon(Icons.Default.ChevronRight, null, tint = c.muted)
        }
    }
    if (standalone) {
        Surface(color = c.canvas, contentColor = c.ink, shape = MaterialTheme.shapes.small) { row() }
    } else row()
}

@Composable
private fun DeveloperModeRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val c = LocalGeoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GeoSize.minTouchTarget)
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange
            )
            .padding(GeoSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("개발자 모드", style = MaterialTheme.typography.bodyLarge)
            Text(
                "기술 정보와 관리 도구",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
fun AdminToolsScreen(
    state: DashboardUiState, deviceId: String?, onBack: () -> Unit,
    onNetwork: () -> Unit, onTargets: () -> Unit, onDiagnostics: () -> Unit,
    onJetsonServerProxy: () -> Unit,
    onDeviceRegistration: () -> Unit,
    onRefreshFan: () -> Unit, onFanAuto: () -> Unit, onFanManual: (Int) -> Unit,
    onReboot: () -> Unit, onShutdown: () -> Unit, onDismissMessage: () -> Unit
) {
    var pending by remember(deviceId, state.isOnline) { mutableStateOf<String?>(null) }
    val powerEnabled = deviceId != null && state.isOnline &&
        state.capabilities.powerCommandsEnabled && !state.operationInProgress
    pending?.let { action ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("${state.deviceName} ${if (action == "reboot") "재부팅" else "전원 종료"}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("대상: ${state.deviceName}\n${deviceId.orEmpty()}\n실행 중인 수집과 전송이 중단되고 연결이 끊어질 수 있습니다." +
                    if (action == "shutdown") " 다시 사용하려면 장비 전원을 직접 켜야 합니다." else "")
            } },
            confirmButton = { Button(onClick = { pending = null; if (action == "reboot") onReboot() else onShutdown() },
                enabled = powerEnabled, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                Text(if (action == "reboot") "장비 재부팅" else "장비 전원 종료")
            } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소") } })
    }

    Scaffold(
        containerColor = LocalGeoColors.current.canvas,
        topBar = {
            val stage = userConnectionStage(state.isOnline, state.transportType)
            DeviceContextHeader("관리자 도구", state.deviceName, stage.label,
                onBack, 0, {}, connectionTone = stage.tone)
        }) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = GeoSpace.lg),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
            item { DismissibleNoticeBanner(
                noticeKey = "settings.admin-access-explainer.v1",
                message = "이 화면은 관리 기능을 모아 보여 줍니다. 로컬 화면 접근은 관리자 인증이나 서버 권한 확인을 대신하지 않습니다.",
                tone = StatusTone.WARNING) }
            item { SectionHeader("연결 · 운영 정책") }
            item { SettingsLink("네트워크 연결", "장비의 연결 경로와 복구 방법", onNetwork) }
            item { SettingsLink("업로드 서버 대상", "Jetson이 전송할 서버 주소와 장비 인증", onTargets) }
            item { SettingsLink("Jetson 경유 서버 조회", "선택 장비가 서버를 대신 조회하는 기존 기능", onJetsonServerProxy) }
            item { SettingsLink("장비 정보 · 진단", "연결 기록·버전·진단 내보내기", onDiagnostics,
                alert = state.fanError != null || state.operationIsError) }
            if (state.capabilities.fanControl) item { FanControlCard(state, onRefreshFan, onFanAuto, onFanManual) }
            item { Text("시스템 지표 · ${if (state.isOnline && state.statusFreshness == StatusFreshness.CURRENT) "최근 응답" else "현재 상태 미확인"}",
                style = MaterialTheme.typography.titleMedium) }
            item { MetricsGrid(state) }
            state.operationMessage?.let { message -> item { AppBanner(message,
                if (state.operationIsError) StatusTone.ERROR else StatusTone.INFO, onDismiss = onDismissMessage) } }
            // 위험 동작: the target is stated next to the button, not only inside the
            // confirmation dialog, so the operator reads which device and which work is
            // affected before they commit to the gesture.
            item { SectionHeader("위험 동작") }
            item {
                GeoSection(tone = StatusTone.ERROR) {
                    Text(
                        "아래 동작은 실행 중인 수집과 전송을 중단시키고 연결을 끊을 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalGeoColors.current.muted
                    )
                    GeoDangerAction(
                        label = "장비 재부팅",
                        target = "대상: ${state.deviceName}${deviceId?.let { " · $it" }.orEmpty()}",
                        onClick = { pending = "reboot" },
                        enabled = powerEnabled
                    )
                    GeoDangerAction(
                        label = "장비 전원 종료",
                        target = "대상: ${state.deviceName} · 다시 사용하려면 현장에서 직접 전원을 켜야 합니다",
                        onClick = { pending = "shutdown" },
                        enabled = powerEnabled
                    )
                    if (!powerEnabled) {
                        Text(
                            "현재 연결 상태나 장비 권한에서는 전원 명령을 보낼 수 없습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalGeoColors.current.muted
                        )
                    }
                }
            }
            item { SettingsLink("장비 등록 관리", "등록 삭제는 측정 파일 삭제나 장비 초기화가 아닙니다",
                onDeviceRegistration, LocalGeoColors.current.sectionDanger) }
        }
        }
    }
}

/**
 * One settings destination.
 *
 * The `color` parameter is kept for source compatibility but ignored: callers used it to
 * give individual rows different tinted backgrounds, which made a settings list look like
 * a status display. Rows now share one surface and are separated by their borders, and
 * colour is spent only on the leading icon and on a genuine alert.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SettingsLink(title: String, description: String, onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = LocalGeoColors.current.sectionSoft,
    alert: Boolean = false) {
    val c = LocalGeoColors.current
    Surface(onClick = onClick, color = c.surface, contentColor = c.ink,
        border = androidx.compose.foundation.BorderStroke(GeoSize.hairline, c.border),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget)) {
        Row(Modifier.padding(GeoSpace.lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
            Icon(when {
                title.contains("네트워크") -> Icons.Default.Wifi
                title.contains("센서") -> Icons.Default.Sensors
                title.contains("서버") -> Icons.Default.Storage
                title.contains("진단") -> Icons.Default.HealthAndSafety
                title.contains("알림") -> Icons.Default.Notifications
                title.contains("개발자") -> Icons.Default.Terminal
                title.contains("관리자") -> Icons.Default.AdminPanelSettings
                title.contains("빌드") -> Icons.Default.Info
                else -> Icons.Default.Settings
            }, null, tint = c.primary, modifier = Modifier.size(GeoSize.iconLg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            if (alert) Badge { Text("!") }
            Icon(Icons.Default.ChevronRight, null, tint = c.muted)
        }
    }
}
