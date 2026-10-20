package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import com.example.jetsoncontroller.ui.devices.DeviceNavigationRow

/** Kept for external callers; the old sensor route redirects to device detail. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SensorScreen(
    status: JetsonStatus,
    deviceOnline: Boolean = false,
    fullControlAvailable: Boolean = false,
    onCameraClick: () -> Unit,
    onGnssClick: () -> Unit,
    onSectionSelected: (GeoTab) -> Unit,
    deviceName: String = "선택된 장치 없음",
    onDevices: () -> Unit = {},
    onAlerts: () -> Unit = {},
    unreadCount: Int = 0
) {
    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.surface)) {
        DeviceTitleBar("장치", onDevices)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(LocalGeoWindow.current.gutterDp.dp)) {
            GeoSection("센서", first = true) {
                DeviceSensorRows(status, deviceOnline, fullControlAvailable, onCameraClick, onGnssClick)
            }
        }
    }
}

@Composable
internal fun DeviceSensorRows(status: JetsonStatus, current: Boolean, control: Boolean,
    onCamera: () -> Unit, onGnss: () -> Unit) {
    val sensors = sensorPresentations(status, current, System.currentTimeMillis())
    val camera = sensors.camera
    val gnss = sensors.gnss
    DeviceNavigationRow("카메라", onCamera, meta = cameraResolution(status.cameraSensor).takeIf { camera.activity == SensorActivity.ACTIVE },
        badge = { GeoBadge(camera.badge, camera.activity.deviceSensorTone()) }, enabled = current && control,
        disabledReason = if (!current) "연결되면 쓸 수 있습니다." else "기본 연결에서는 영상을 볼 수 없습니다.")
    val location = deviceGnssState(gnss, status.gnssSensor)
    DeviceNavigationRow("위치(GNSS)", onGnss,
        meta = status.gnssSensor.satellites?.takeIf { gnss.activity == SensorActivity.ACTIVE }?.let { "위성 ${it}개" },
        badge = { GeoBadge(location.word, location.tone) },
        enabled = current, disabledReason = "연결되면 쓸 수 있습니다.")
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 16.dp)) {
        if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("움직임(IMU)", style = LocalGeoType.current.titleSm, color = LocalGeoColors.current.text)
                GeoBadge(sensors.imu.badge, sensors.imu.activity.deviceSensorTone())
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("움직임(IMU)", Modifier.weight(1f), style = LocalGeoType.current.titleSm, color = LocalGeoColors.current.text)
            GeoBadge(sensors.imu.badge, sensors.imu.activity.deviceSensorTone())
        }
    }
}
