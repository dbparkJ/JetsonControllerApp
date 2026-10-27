package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
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
    GeoLargeTitleScaffold("센서", subtitle = deviceName, onBack = onDevices) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp)
            .padding(bottom = 24.dp)) {
            GeoGroupedSection("센서", first = true, trailingColumn = GeoRowTrailing.Status) {
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
    GeoRowList(GeoRowTrailing.Status) {
        DeviceNavigationRow("카메라", onCamera,
            meta = if (camera.activity == SensorActivity.ACTIVE) cameraResolution(status.cameraSensor) else camera.description,
            badge = { GeoBadge(camera.badge, camera.activity.deviceSensorTone()) }, enabled = current && control,
            disabledReason = listOf(camera.badge, camera.description,
                if (!current) "연결되면 쓸 수 있습니다." else "기본 연결에서는 영상을 볼 수 없습니다.")
                .distinct().joinToString(" · "),
            leading = { GeoIconTile(GeoIcons.camera) })
        val location = deviceGnssState(gnss, status.gnssSensor)
        DeviceNavigationRow("위치(GNSS)", onGnss,
            meta = if (gnss.activity == SensorActivity.ACTIVE) status.gnssSensor.satellites?.let { "위성 ${it}개" }
                else gnss.description,
            badge = { GeoBadge(location.word, location.tone) },
            enabled = current, disabledReason = listOf(location.word, gnss.description, "연결되면 쓸 수 있습니다.")
                .distinct().joinToString(" · "),
            leading = { GeoIconTile(GeoIcons.myLocation) })
        GeoRow("움직임(IMU)", {}, navigable = false, meta = sensors.imu.description, divider = false,
            badge = { GeoBadge(sensors.imu.badge, sensors.imu.activity.deviceSensorTone()) },
            leading = { GeoIconTile(GeoIcons.tabDevice) })
    }
}
