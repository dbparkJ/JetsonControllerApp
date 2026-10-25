package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import com.example.jetsoncontroller.ui.devices.DeviceNavigationRow
import com.example.jetsoncontroller.ui.devices.DeviceSurface

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
    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.canvas)) {
        DeviceTitleBar("장치", onDevices)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(LocalGeoWindow.current.gutterDp.dp)) {
            GeoSection("센서", first = true) {
                DeviceSurface {
                    DeviceSensorRows(status, deviceOnline, fullControlAvailable, onCameraClick, onGnssClick)
                }
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
        DeviceNavigationRow("카메라", onCamera, meta = cameraResolution(status.cameraSensor).takeIf { camera.activity == SensorActivity.ACTIVE },
            badge = { GeoBadge(camera.badge, camera.activity.deviceSensorTone()) }, enabled = current && control,
            disabledReason = if (!current) "연결되면 쓸 수 있습니다." else "기본 연결에서는 영상을 볼 수 없습니다.",
            leading = { GeoIconTile(GeoIcons.camera) })
        val location = deviceGnssState(gnss, status.gnssSensor)
        DeviceNavigationRow("위치(GNSS)", onGnss,
            meta = status.gnssSensor.satellites?.takeIf { gnss.activity == SensorActivity.ACTIVE }?.let { "위성 ${it}개" },
            badge = { GeoBadge(location.word, location.tone) },
            enabled = current, disabledReason = "연결되면 쓸 수 있습니다.",
            leading = { GeoIconTile(GeoIcons.myLocation) })
        GeoDeviceStatusRow("움직임(IMU)", sensors.imu.badge, sensors.imu.activity.deviceSensorTone(),
            leading = { GeoIconTile(GeoIcons.tabDevice) })
    }
}
