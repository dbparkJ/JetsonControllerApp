package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane

@Composable
fun DeveloperScreen(
    state: FieldState, onBack: () -> Unit, onExecute: (String) -> Unit,
    onLogs: () -> Unit, onDiagnostics: () -> Unit,
    developerModeEnabled: Boolean = false, onDeveloperModeChange: (Boolean) -> Unit = {},
    onDeveloperDisabled: () -> Unit = {}, onAdminTools: () -> Unit = {},
    onRefreshNetwork: () -> Unit = {}, onTestSensor: (String) -> Unit = {},
    onCameraPreview: () -> Unit = {}, onGpsMap: () -> Unit = {},
    onOpenTool: ((String) -> Unit)? = null
) {
    if (!developerModeEnabled) {
        LaunchedEffect(Unit) { onDeveloperDisabled() }
        return
    }
    var technical by rememberTechnicalInfo()
    var localTool by remember { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = onOpenTool ?: { localTool = it }
    when (localTool) {
        "dev/terminal" -> { DevTerminalScreen(state, onExecute, { localTool = null }); return }
        "dev/network" -> { DevNetworkScreen(state, onRefreshNetwork, { localTool = null }); return }
        "dev/sensor-test" -> { DevSensorTestScreen(state, onTestSensor, onCameraPreview, onGpsMap, { localTool = null }); return }
    }
    FieldDetailFrame("개발자 도구", onBack) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp).padding(bottom = 24.dp)) {
        GeoGroupedSection("개발자 설정", first = true) {
        GeoSwitchRow("개발자 모드", true, { enabled ->
            onDeveloperModeChange(enabled)
            if (!enabled) onDeveloperDisabled()
        }, description = "끄면 설정으로 돌아갑니다.")
        GeoSwitchRow("화면에 기술 정보 표시", technical, { technical = it }, description = "상세 화면 끝에 기술 정보를 보여 줍니다.")
        }
        devHubSections.forEach { (section, doors) ->
            GeoGroupedSection(section) {
                doors.forEachIndexed { doorIndex, door ->
                    GeoRow(door.label, {
                        if (onOpenTool != null) open(door.destination.route)
                        else when (door.destination) {
                            DevDestination.DIAGNOSTICS -> onDiagnostics()
                            DevDestination.PIPELINES, DevDestination.NEW_PIPELINE -> onLogs()
                            DevDestination.TERMINAL, DevDestination.NETWORK, DevDestination.SENSOR_TEST -> open(door.destination.route)
                            else -> onAdminTools()
                        }
                    }, leading = { GeoIconTile(GeoIcons.terminal) }, divider = doorIndex < doors.lastIndex)
                }
            }
        }
      }
    }
}
