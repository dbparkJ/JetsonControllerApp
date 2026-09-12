package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.Button
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.*
import com.example.jetsoncontroller.ui.connection.userConnectionStage

@Composable
fun SettingsHubScreen(
    state: DashboardUiState, deviceId: String?, unreadCount: Int,
    onDevices: () -> Unit, onAlerts: () -> Unit, onNetwork: () -> Unit,
    onSensors: () -> Unit, onTargets: () -> Unit, onDiagnostics: () -> Unit,
    onAlertSettings: () -> Unit, onServerStorage: () -> Unit,
    onRefreshFan: () -> Unit, onFanAuto: () -> Unit, onFanManual: (Int) -> Unit,
    onReboot: () -> Unit, onShutdown: () -> Unit,
    onDismissMessage: () -> Unit, onSection: (ControlSection) -> Unit, onDeveloper: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPrefs = remember { context.getSharedPreferences("geo_developer", 0) }
    var developer by remember { mutableStateOf(developerPrefs.getBoolean("enabled", false)) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var developerMessage by remember { mutableStateOf<String?>(null) }
    var pending by remember(deviceId, state.isOnline) { mutableStateOf<String?>(null) }
    val (theme, setTheme) = rememberThemePreference()
    val powerEnabled = deviceId != null && state.isOnline && state.capabilities.powerCommandsEnabled && !state.operationInProgress
    pending?.let { action ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("${state.deviceName} ${if (action == "reboot") "재부팅" else "전원 종료"}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("대상: ${state.deviceName}\n${deviceId.orEmpty()}\n실행 중인 수집 작업과 전송이 중단되고 연결이 끊어집니다." +
                if (action == "shutdown") " 다시 사용하려면 장비 전원을 직접 켜야 합니다." else " 복구 시간은 장비 상태에 따라 다릅니다.") } },
            confirmButton = { Button(shape = MaterialTheme.shapes.small, enabled = powerEnabled, onClick = {
                pending = null
                if (action == "reboot") onReboot() else onShutdown()
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError)) { Text(if (action == "reboot") "장비 재부팅" else "장비 전원 종료") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소") } })
    }
    Scaffold(topBar = { DeviceContextHeader("설정", state.deviceName,
        userConnectionStage(state.isOnline, state.transportType).label, onDevices, unreadCount, onAlerts) },
        bottomBar = { ControlNavigationBar(ControlSection.SETTINGS, onSection) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { GeoDeviceSummary(state, onDevices, showMetrics = false) }
            item { SectionSurface(LocalCobaltColors.current.sectionSoft) { SectionHeader("장비 설정") } }
            item { SettingsLink("네트워크 연결", "연결 경로와 복구 방법", onNetwork) }
            item { SettingsLink("센서 상태", "카메라 · GNSS · IMU · RTK", onSensors) }
            if (state.capabilities.fanControl) {
                item { FanControlCard(state, onRefreshFan, onFanAuto, onFanManual) }
            } else { item { Text("팬 제어 · 미지원 또는 지원 여부 미확인", style = MaterialTheme.typography.bodyMedium) } }
            item { SettingsLink("서버 대상", "업로드할 서버와 인증 설정", onTargets) }
            item { SettingsLink("서버 보관함", "전송된 수집 세션과 파일", onServerStorage) }
            item { SettingsLink("장비 정보 · 진단", "연결 기록·버전·진단 내보내기", onDiagnostics, alert = state.fanError != null || state.operationIsError) }
            item { Text("시스템 지표 · ${if (state.isOnline && state.statusFreshness == StatusFreshness.CURRENT) "최근 응답" else "현재 상태 미확인"}", style = MaterialTheme.typography.titleMedium) }
            item { MetricsGrid(state) }
            item { SectionSurface(LocalCobaltColors.current.sectionRaised) { SectionHeader("앱 설정") } }
            item { SettingsLink("알림", "이력·권한·장비 및 작업 임계값", onAlertSettings, LocalCobaltColors.current.sectionRaised, alert = unreadCount > 0) }
            item {
                SectionSurface(LocalCobaltColors.current.sectionRaised) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("화면 테마", style = MaterialTheme.typography.titleMedium)
                        ThemeMode.entries.forEach { value ->
                            FilterChip(selected = theme == value, onClick = { setTheme(value) },
                                label = { Text(value.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                        }
                        Text("선택·검색·초안은 장비별로 자동 보존합니다. 실행 상태는 연결 후 다시 확인합니다.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                SettingsLink("빌드 번호", "GEO& ${com.example.jetsoncontroller.BuildConfig.VERSION_NAME}", {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastTap > 5000) taps = 0
                    lastTap = now
                    taps++
                    if (taps >= 7) {
                        developer = true
                        developerPrefs.edit().putBoolean("enabled", true).apply()
                        developerMessage = "개발자 모드가 활성화되었습니다."
                    } else if (taps >= 3) developerMessage = "개발자 모드까지 ${7 - taps}번 남았습니다."
                }, LocalCobaltColors.current.sectionRaised)
            }
            developerMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            if (developer) item { SettingsLink("개발자 옵션", "원격 터미널 · 저장 로그", onDeveloper, LocalCobaltColors.current.sectionRaised) }
            item { SectionSurface(LocalCobaltColors.current.sectionDanger) { SectionHeader("위험 동작") } }
            state.operationMessage?.let { message -> item { AppBanner(message,
                if (state.operationIsError) StatusTone.ERROR else StatusTone.INFO, onDismiss = onDismissMessage) } }
            item { SectionSurface(LocalCobaltColors.current.dangerBg) { Button(shape = MaterialTheme.shapes.small, onClick = { pending = "reboot" }, enabled = powerEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, disabledContainerColor = LocalCobaltColors.current.dangerBg, disabledContentColor = LocalCobaltColors.current.danger)) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("장비 재부팅", color = if (powerEnabled) MaterialTheme.colorScheme.onError else LocalCobaltColors.current.danger) } } }
            item { SectionSurface(LocalCobaltColors.current.dangerBg) { Button(shape = MaterialTheme.shapes.small, onClick = { pending = "shutdown" }, enabled = powerEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, disabledContainerColor = LocalCobaltColors.current.dangerBg, disabledContentColor = LocalCobaltColors.current.danger)) { Icon(Icons.Default.PowerSettingsNew, null); Spacer(Modifier.width(8.dp)); Text("장비 전원 종료", color = if (powerEnabled) MaterialTheme.colorScheme.onError else LocalCobaltColors.current.danger) } } }
            item { SettingsLink("장비 등록 관리", "앱의 등록 삭제는 측정 파일 삭제나 장비 초기화가 아닙니다", onDevices, LocalCobaltColors.current.sectionDanger) }
        }
    }
}

@Composable
private fun SettingsLink(title: String, description: String, onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = LocalCobaltColors.current.sectionSoft, alert: Boolean = false) {
    Surface(onClick = onClick, color = color, contentColor = LocalCobaltColors.current.ink, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(when {
                title.contains("네트워크") -> Icons.Default.Wifi
                title.contains("센서") -> Icons.Default.Sensors
                title.contains("서버") -> Icons.Default.Storage
                title.contains("진단") -> Icons.Default.HealthAndSafety
                title.contains("알림") -> Icons.Default.Notifications
                title.contains("개발자") -> Icons.Default.Terminal
                title.contains("빌드") -> Icons.Default.Info
                else -> Icons.Default.Settings
            }, null, tint = LocalCobaltColors.current.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alert) Badge { Text("!") }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
