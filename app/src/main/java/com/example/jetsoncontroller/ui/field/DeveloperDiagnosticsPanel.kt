package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DeveloperDiagnosticsPanel(
    state: FieldState,
    onRefreshNetwork: () -> Unit,
    onTestSensor: (String) -> Unit,
    onCameraPreview: () -> Unit,
    onGpsMap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        DiagnosticCard {
            Text("Jetson 네트워크 IP", style = MaterialTheme.typography.titleMedium)
            Text("Wi-Fi · 유선 · Wi-Fi Direct · Tailscale 주소", style = MaterialTheme.typography.bodySmall)
            state.network?.let { network ->
                if (network.interfaces.isEmpty()) Text("활성 네트워크 주소가 없습니다.")
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                        network.interfaces.forEach { address ->
                            Column {
                                Text("${address.kind} · ${address.interfaceName} · ${address.family}")
                                Text(address.address, style = MaterialTheme.typography.titleMedium)
                                Text(address.apiUrl, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Text("조회: ${diagnosticTime(network.checkedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
            }
            state.networkError?.let { Text(it, color = LocalGeoColors.current.danger) }
            TextButton(onClick = onRefreshNetwork, enabled = state.online && !state.networkBusy) {
                Text(if (state.networkBusy) "IP 주소 조회 중…" else "IP 주소 새로고침")
            }
            if (!state.online) Text("장치에 LAN 또는 Wi-Fi Direct로 연결하면 조회할 수 있습니다.")
        }
        DiagnosticCard {
            Text("카메라·GPS 테스트", style = MaterialTheme.typography.titleMedium)
            Text("5초 동안 Jetson 센서 모니터에서 새 프레임과 GPS 데이터 수신을 확인합니다.", style = MaterialTheme.typography.bodySmall)
            for ((sensor, label) in listOf("camera" to "카메라", "gps" to "GPS")) {
                Button(
                    onClick = { onTestSensor(sensor) },
                    enabled = state.online && state.testingSensor == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.testingSensor == sensor) "$label 테스트 중…" else "$label 테스트") }
                state.sensorTests[sensor]?.let { result ->
                    val passed = result.state == "PASSED"
                    Text("$label: ${if (passed) "수신 확인" else "미확인"}",
                        color = if (passed) LocalGeoColors.current.ink else LocalGeoColors.current.danger)
                    Text(result.message)
                    SelectionContainer {
                        Text(result.details.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("테스트: ${diagnosticTime(result.checkedAtEpochMillis)} · ${result.durationSeconds}초",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            state.sensorTestError?.let { Text(it, color = LocalGeoColors.current.danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                TextButton(onClick = onCameraPreview, enabled = state.online) { Text("카메라 프리뷰") }
                TextButton(onClick = onGpsMap, enabled = state.online) { Text("GPS 지도") }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(content: @Composable () -> Unit) {
    Surface(color = LocalGeoColors.current.surface, contentColor = LocalGeoColors.current.ink,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(GeoSpace.lg), verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
            content()
        }
    }
}

private fun diagnosticTime(timestamp: Long): String =
    SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
