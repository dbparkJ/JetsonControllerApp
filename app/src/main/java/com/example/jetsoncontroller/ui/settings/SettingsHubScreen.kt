package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.Appearance
import com.example.jetsoncontroller.ui.theme.rememberAppearance
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.geo.GeoRow
import com.example.jetsoncontroller.ui.components.geo.GeoActionRow
import com.example.jetsoncontroller.ui.components.geo.GeoSection

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
    onDismissMessage: () -> Unit, onSection: (ControlSection) -> Unit,
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
    onRequestNotificationPermission: () -> Unit = {}
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
        else -> Column(Modifier.fillMaxSize()) {
            Text(
                "설정",
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 8.dp),
                color = LocalGeoColors.current.ink,
                style = LocalGeoType.current.heading
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    GeoSection("화면", first = true) {
                        GeoRow(
                            title = "테마",
                            value = appearance.summary,
                            onClick = { onTheme?.invoke() }
                        )
                    }
                }
                item {
                    GeoSection("알림") {
                        GeoRow(
                            title = "알림 기준",
                            value = alertSettingsSummary(alertSettings),
                            onClick = onAlertSettings
                        )
                        if (!notificationPermissionGranted) {
                            GeoActionRow(
                                title = "알림 권한",
                                actionLabel = "권한 허용",
                                onOpen = onAlertSettings,
                                onAction = onRequestNotificationPermission,
                                meta = "알림 권한이 꺼져 있습니다"
                            )
                        }
                    }
                }
                item {
                    GeoSection("서버") {
                        GeoRow(
                            title = "서버 연결",
                            value = serverProfileName ?: "연결 정보 없음",
                            onClick = { onServerConnection?.invoke() ?: onServerStorage() }
                        )
                    }
                }
                item {
                    GeoSection("정보") {
                        GeoRow(
                            title = "도움말",
                            onClick = { onHelp?.invoke() ?: run { embeddedPage = "help" } }
                        )
                        GeoRow(
                            title = "앱 정보",
                            value = com.example.jetsoncontroller.BuildConfig.VERSION_NAME,
                            onClick = { onAbout?.invoke() ?: run { embeddedPage = "about" } }
                        )
                    }
                }
                if (developerModeEnabled) {
                    item {
                        GeoSection("고급") {
                            GeoRow(title = "개발자 도구", onClick = onDeveloper)
                        }
                    }
                }
            }
        }
    }
}
