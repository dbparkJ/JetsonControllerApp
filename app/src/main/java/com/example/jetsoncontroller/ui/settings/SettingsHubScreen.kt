package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.Appearance
import com.example.jetsoncontroller.ui.theme.rememberAppearance
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.GeoGroupedSection as GeoSection
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.model.Tone

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
    serverStatusTone: Tone = Tone.UNKNOWN,
    selectedPage: String? = null
) {
    val scroll = com.example.jetsoncontroller.ui.components.geo.shell.rememberGeoPaneListState("settings")
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
        else -> GeoLargeTitleScaffold("설정") {
            LazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp, bottom = 24.dp)
            ) {
                item {
                    GeoSection("화면", first = true) {
                        SettingsNavigationRow("테마", { onTheme?.invoke() }, selectedPage == "settings/theme", meta = appearance.summary,
                            leading = { GeoIconTile(GeoIcons.tabSettings) })
                    }
                }
                item {
                    GeoSection("알림", trailingColumn = GeoRowTrailing.None) {
                        SettingsNavigationRow(
                            title = "알림 기준",
                            meta = alertSettingsSummary(alertSettings),
                            leading = { GeoIconTile(GeoIcons.bell) },
                            selected = selectedPage == "alert_settings",
                            rowCount = if (notificationPermissionGranted) 1 else 2,
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
                        SettingsNavigationRow(
                            title = "서버 연결",
                            meta = serverProfileName ?: "연결 정보 없음",
                            leading = { GeoIconTile(GeoIcons.router) },
                            badge = { GeoBadge(serverStatusWord, serverStatusTone) },
                            selected = selectedPage == "settings/server",
                            onClick = { onServerConnection?.invoke() ?: onServerStorage() }
                        )
                    }
                }
                item {
                    GeoSection("정보", trailingColumn = GeoRowTrailing.None) {
                        SettingsNavigationRow(
                            title = "도움말",
                            leading = { GeoIconTile(GeoIcons.info) },
                            onClick = { onHelp?.invoke() ?: run { embeddedPage = "help" } },
                            selected = selectedPage == "settings/help", rowCount = 2,
                            nextSelected = selectedPage == "settings/about"
                        )
                        SettingsNavigationRow(
                            title = "앱 정보",
                            meta = com.example.jetsoncontroller.BuildConfig.VERSION_NAME,
                            leading = { GeoIconTile(GeoIcons.info) },
                            selected = selectedPage == "settings/about", index = 1, rowCount = 2,
                            onClick = { onAbout?.invoke() ?: run { embeddedPage = "about" } }
                        )
                    }
                }
                if (developerModeEnabled) {
                    item {
                        GeoSection("고급", trailingColumn = GeoRowTrailing.None) {
                            SettingsNavigationRow(title = "개발자 도구", onClick = onDeveloper,
                                selected = false, leading = { GeoIconTile(GeoIcons.terminal) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(title: String, onClick: () -> Unit, selected: Boolean,
    index: Int = 0, rowCount: Int = 1, nextSelected: Boolean = false, meta: String? = null,
    leading: (@Composable () -> Unit)? = null, badge: (@Composable () -> Unit)? = null) {
    if (LocalGeoPane.current) GeoPaneListRow(title, onClick, selected, index, rowCount, nextSelected,
        meta = meta, leading = leading, badge = badge)
    else GeoRow(title, onClick, meta = meta, leading = leading, badge = badge,
        divider = index < rowCount - 1)
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
