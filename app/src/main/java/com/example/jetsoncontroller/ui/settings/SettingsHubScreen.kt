package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.connection.userConnectionStage
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.ThemeMode
import com.example.jetsoncontroller.ui.theme.rememberThemePreference

/** Operator-facing preferences. Administrative device controls live in [AdminToolsScreen]. */
@Composable
fun SettingsHubScreen(
    state: DashboardUiState, deviceId: String?, unreadCount: Int,
    onDevices: () -> Unit, onAlerts: () -> Unit, onNetwork: () -> Unit,
    onSensors: () -> Unit, onTargets: () -> Unit, onDiagnostics: () -> Unit,
    onAlertSettings: () -> Unit, onServerStorage: () -> Unit,
    onRefreshFan: () -> Unit, onFanAuto: () -> Unit, onFanManual: (Int) -> Unit,
    onReboot: () -> Unit, onShutdown: () -> Unit,
    onDismissMessage: () -> Unit, onSection: (ControlSection) -> Unit,
    onDeveloper: () -> Unit = {}, onAdminTools: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPrefs = remember { context.getSharedPreferences("geo_developer", 0) }
    var developer by remember { mutableStateOf(developerPrefs.getBoolean("enabled", false)) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var developerMessage by remember { mutableStateOf<String?>(null) }
    val (theme, setTheme) = rememberThemePreference()

    Scaffold(
        containerColor = LocalGeoColors.current.canvas,
        topBar = {
            val stage = userConnectionStage(state.isOnline, state.transportType)
            DeviceContextHeader("설정", state.deviceName, stage.label,
                onDevices, unreadCount, onAlerts, connectionTone = stage.tone)
        },
        bottomBar = { ControlNavigationBar(ControlSection.SETTINGS, onSection) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { GeoDeviceSummary(state, onDevices, showMetrics = false) }
            item { SectionHeader("현장 작업 설정") }
            item { SettingsLink("센서 상태", "카메라 · GNSS · IMU · RTK 관찰값", onSensors) }
            item { SettingsLink("서버 데이터", "휴대전화 인터넷으로 서버 결과 확인", onServerStorage) }
            item { SettingsLink("알림", "이력·권한·장비 및 작업 임계값", onAlertSettings,
                LocalGeoColors.current.sectionRaised, alert = unreadCount > 0) }
            item {
                SectionSurface(LocalGeoColors.current.sectionRaised) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("화면 테마", style = MaterialTheme.typography.titleMedium)
                        ThemeMode.entries.forEach { value ->
                            FilterChip(selected = theme == value, onClick = { setTheme(value) },
                                label = { Text(value.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                        }
                        Text("선택·검색·초안은 장비별로 보존합니다. 실행 상태는 연결 후 다시 확인합니다.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionHeader("관리 영역") }
            item { AppBanner(
                "관리 영역 구분은 화면을 정리하기 위한 것입니다. 서버나 장비 권한을 부여하지 않으며 실제 작업은 각 대상의 인증으로 확인합니다.",
                StatusTone.INFO) }
            item { SettingsLink("관리자 도구", "네트워크·업로드 대상·진단·전원 관리", onAdminTools) }
            item {
                SettingsLink("빌드 번호", "GEO& ${com.example.jetsoncontroller.BuildConfig.VERSION_NAME}", {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastTap > 5_000) taps = 0
                    lastTap = now
                    taps += 1
                    if (taps >= 7) {
                        developer = true
                        developerPrefs.edit().putBoolean("enabled", true).apply()
                        developerMessage = "개발자 도구가 표시됩니다. 이 설정은 서버 권한과 무관합니다."
                    } else if (taps >= 3) {
                        developerMessage = "개발자 도구 표시까지 ${7 - taps}번 남았습니다."
                    }
                }, LocalGeoColors.current.sectionRaised)
            }
            developerMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            if (developer) item { SettingsLink("개발자 도구", "원격 터미널 · 저장 로그", onDeveloper) }
        }
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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { AppBanner(
                "이 화면은 관리 기능을 모아 보여 줍니다. 로컬 화면 접근은 관리자 인증이나 서버 권한 확인을 대신하지 않습니다.",
                StatusTone.WARNING) }
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
