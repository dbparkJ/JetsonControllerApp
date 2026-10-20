package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*

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
    DevPage("개발자 도구", onBack) {
        GeoSwitchRow("개발자 모드", true, { enabled ->
            onDeveloperModeChange(enabled)
            if (!enabled) onDeveloperDisabled()
        }, description = "끄면 설정으로 돌아갑니다.")
        DevDivider()
        GeoSwitchRow("화면에 기술 정보 표시", technical, { technical = it }, description = "상세 화면 끝에 기술 정보를 보여 줍니다.")
        devHubSections.forEachIndexed { index, (section, doors) ->
            GeoSection(section, first = index == 0, modifier = Modifier.padding(top = if (index == 0) 24.dp else 0.dp)) {
                doors.forEachIndexed { doorIndex, door ->
                    GeoRow(door.label, {
                        if (onOpenTool != null) open(door.destination.route)
                        else when (door.destination) {
                            DevDestination.DIAGNOSTICS -> onDiagnostics()
                            DevDestination.PIPELINES, DevDestination.NEW_PIPELINE -> onLogs()
                            DevDestination.TERMINAL, DevDestination.NETWORK, DevDestination.SENSOR_TEST -> open(door.destination.route)
                            else -> onAdminTools()
                        }
                    }, divider = doorIndex < doors.lastIndex || index == devHubSections.lastIndex)
                }
            }
        }
    }
}
