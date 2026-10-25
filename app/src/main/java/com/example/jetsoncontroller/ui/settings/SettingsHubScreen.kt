package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.Appearance
import com.example.jetsoncontroller.ui.theme.rememberAppearance
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.storage.R7Section as GeoSection
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.geoWorkCardColors
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar

/** Settings root and the local fallback used by isolated previews. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsHubScreen(
    state: DashboardUiState, deviceId: String?, unreadCount: Int,
    onDevices: () -> Unit, onAlerts: () -> Unit, onNetwork: () -> Unit,
    onSensors: () -> Unit, onTargets: () -> Unit, onDiagnostics: () -> Unit,
    onAlertSettings: () -> Unit, onServerStorage: () -> Unit,
    onRefreshFan: () -> Unit, onFanAuto: () -> Unit, onFanManual: (Int) -> Unit,
    onReboot: () -> Unit, onShutdown: () -> Unit,
    onDismissMessage: () -> Unit, onSection: (GeoTab) -> Unit,
    onDeveloper: () -> Unit = {}, onAdminTools: () -> Unit = {},
    developerModeEnabled: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onTheme: (() -> Unit)? = null,
    onServerConnection: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    serverProfileName: String? = null,
    alertSettings: AlertSettings = AlertSettings(),
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    serverStatusWord: String = "확인 전",
    serverStatusTone: Tone = Tone.UNKNOWN
) {
    var embeddedPage by rememberSaveable { mutableStateOf<String?>(null) }
    val appearance = if (LocalInspectionMode.current) Appearance() else rememberAppearance()

    when (embeddedPage) {
        "about" -> SettingsAboutScreen(
            onBack = { embeddedPage = null },
            developerModeEnabled = developerModeEnabled,
            onDeveloperUnlocked = {
                onDeveloperModeChange(true)
                embeddedPage = null
            }
        )
        "help" -> SettingsHelpScreen(onBack = { embeddedPage = null })
        else -> Column(Modifier.fillMaxSize().background(LocalGeoColors.current.canvas)) {
            DeviceTitleBar("설정", root = true)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp, top = 16.dp, bottom = 40.dp)
            ) {
                item {
                    GeoSection("화면", first = true) {
                        ThemeSettingsRow(appearance.summary) { onTheme?.invoke() }
                    }
                }
                item {
                    GeoSection("알림", trailingColumn = GeoRowTrailing.None) {
                        GeoRow(
                            title = "알림 기준",
                            meta = alertSettingsSummary(alertSettings),
                            leading = { GeoIconTile(GeoIcons.bell) },
                            divider = !notificationPermissionGranted,
                            onClick = onAlertSettings
                        )
                        if (!notificationPermissionGranted) {
                            GeoActionRow(
                                title = "알림 권한",
                                actionLabel = "권한 허용",
                                onOpen = onAlertSettings,
                                onAction = onRequestNotificationPermission,
                                divider = false,
                                meta = "알림 권한이 꺼져 있습니다"
                            )
                        }
                    }
                }
                item {
                    GeoSection("서버", trailingColumn = GeoRowTrailing.Status) {
                        GeoRow(
                            title = "서버 연결",
                            meta = serverProfileName ?: "연결 정보 없음",
                            leading = { GeoIconTile(GeoIcons.router) },
                            badge = { GeoBadge(serverStatusWord, serverStatusTone) },
                            divider = false,
                            onClick = { onServerConnection?.invoke() ?: onServerStorage() }
                        )
                    }
                }
                item {
                    GeoSection("정보", trailingColumn = GeoRowTrailing.None) {
                        GeoRow(
                            title = "도움말",
                            leading = { GeoIconTile(GeoIcons.info) },
                            onClick = { onHelp?.invoke() ?: run { embeddedPage = "help" } }
                        )
                        GeoRow(
                            title = "앱 정보",
                            meta = com.example.jetsoncontroller.BuildConfig.VERSION_NAME,
                            leading = { GeoIconTile(GeoIcons.info) },
                            divider = false,
                            onClick = { onAbout?.invoke() ?: run { embeddedPage = "about" } }
                        )
                    }
                }
                if (developerModeEnabled) {
                    item {
                        GeoSection("고급", trailingColumn = GeoRowTrailing.None) {
                            GeoRow(title = "개발자 도구", onClick = onDeveloper,
                                leading = { GeoIconTile(GeoIcons.terminal) }, divider = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsRow(summary: String, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    val work = geoWorkCardColors(LocalGeoPalette.current)
    Row(Modifier.fillMaxWidth().contentTarget(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(76.dp, 104.dp).clip(GeoShape.control).clearAndSetSemantics {}) {
            drawRect(c.canvas)
            drawRoundRect(c.text, Offset(9.dp.toPx(), 9.dp.toPx()), Size(30.dp.toPx(), 8.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRoundRect(work.background, Offset(9.dp.toPx(), 25.dp.toPx()), Size(58.dp.toPx(), 32.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            repeat(3) { index -> drawRoundRect(c.surface, Offset((9 + 20 * index).dp.toPx(), 65.dp.toPx()),
                Size(17.dp.toPx(), 16.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("테마", style = LocalGeoType.current.sectionTitle, color = c.text)
            Text(summary, style = LocalGeoType.current.supporting, color = c.textSecondary)
        }
        Icon(GeoIcons.chevronRight, null, Modifier.size(24.dp), tint = c.textSecondary)
    }
}

internal fun serverConnectionWord(status: ServerConnectionStatus): String = when (status) {
    ServerConnectionStatus.UNKNOWN -> "확인 전"
    ServerConnectionStatus.CHECKING -> "확인 중"
    ServerConnectionStatus.CONNECTED -> "연결됨"
    ServerConnectionStatus.LOGIN_REQUIRED -> "로그인 필요"
    ServerConnectionStatus.FAILED -> "연결 실패"
}

internal fun serverConnectionTone(status: ServerConnectionStatus): Tone = when (status) {
    ServerConnectionStatus.UNKNOWN -> Tone.UNKNOWN
    ServerConnectionStatus.CHECKING -> Tone.PENDING
    ServerConnectionStatus.CONNECTED -> Tone.SUCCESS
    ServerConnectionStatus.LOGIN_REQUIRED -> Tone.WARNING
    ServerConnectionStatus.FAILED -> Tone.ERROR
}
