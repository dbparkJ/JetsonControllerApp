package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    status: JetsonStatus,
    deviceOnline: Boolean = true,
    fullControlAvailable: Boolean = true,
    onCameraClick: () -> Unit,
    onGnssClick: () -> Unit,
    onSectionSelected: (ControlSection) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("센서 상태") }) },
        bottomBar = {
            ControlNavigationBar(
                selected = ControlSection.SENSORS,
                onSelect = onSectionSelected,
                enabledSections = if (fullControlAvailable) {
                    ControlSection.entries.toSet()
                } else {
                    setOf(ControlSection.OVERVIEW, ControlSection.SENSORS)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("연결된 센서") }
            item {
                val camera = status.cameraSensor
                SensorStatusRow(
                    icon = Icons.Default.CameraAlt,
                    name = "카메라",
                    presentation = sensorPresentation(
                        configured = status.cameraConfigured || camera.configured,
                        connected = camera.connected,
                        active = camera.active,
                        telemetryAvailable = status.sensorTelemetryAvailable,
                        telemetryFresh = status.sensorTelemetryFresh,
                        legacyRunning = status.cameraRunning
                    ),
                    detail = if (
                        camera.active && camera.frameWidth != null && camera.frameHeight != null
                    ) {
                        "${camera.frameWidth} × ${camera.frameHeight} · 실시간 프리뷰"
                    } else null,
                    onClick = onCameraClick.takeIf { deviceOnline && fullControlAvailable }
                )
            }
            item {
                val gnss = status.gnssSensor
                val gnssAvailable = effectiveGnssAvailability(
                    deviceOnline = deviceOnline,
                    telemetryAvailable = status.sensorTelemetryAvailable,
                    telemetryFresh = status.sensorTelemetryFresh,
                    sensorConnected = gnss.connected,
                    sensorActive = gnss.active,
                    legacyRunning = status.gnssRunning
                )
                val presentation = if (!deviceOnline) {
                    SensorPresentation(
                        activity = SensorActivity.DISCONNECTED,
                        badge = "오프라인",
                        description = "장치가 오프라인입니다"
                    )
                } else {
                    sensorPresentation(
                        configured = status.gnssConfigured || gnss.configured,
                        connected = gnss.connected,
                        active = gnss.active,
                        telemetryAvailable = status.sensorTelemetryAvailable,
                        telemetryFresh = status.sensorTelemetryFresh,
                        legacyRunning = status.gnssRunning
                    )
                }
                val gnssStateKnown = deviceOnline &&
                    (!status.sensorTelemetryAvailable || status.sensorTelemetryFresh)
                SensorStatusRow(
                    icon = Icons.Default.Explore,
                    name = "GNSS",
                    presentation = presentation,
                    detail = if (gnssStateKnown) {
                        listOfNotNull(
                            gnssReceptionLabel(
                                gnssAvailable = gnssAvailable,
                                fixType = gnss.fixType,
                                rtkStatus = gnss.rtkStatus
                            ),
                            gnss.ntripMountpoint?.takeIf { gnssAvailable && gnss.ntripConnected }
                        ).joinToString(" · ")
                    } else {
                        presentation.description
                    },
                    onClick = onGnssClick
                )
            }
            item {
                val imu = status.imuSensor
                SensorStatusRow(
                    icon = Icons.Default.Sensors,
                    name = "IMU",
                    presentation = sensorPresentation(
                        configured = status.imuConfigured || imu.configured,
                        connected = imu.connected,
                        active = imu.active,
                        telemetryAvailable = status.sensorTelemetryAvailable,
                        telemetryFresh = status.sensorTelemetryFresh,
                        legacyRunning = status.imuRunning
                    ),
                    detail = imu.source?.let { source ->
                        when (source) {
                            "external" -> "외부 IMU"
                            "oak+external" -> "OAK + 외부 IMU"
                            else -> "OAK IMU"
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SensorStatusRow(
    icon: ImageVector,
    name: String,
    presentation: SensorPresentation,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    val active = presentation.activity == SensorActivity.ACTIVE
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(
                    detail ?: presentation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    presentation.badge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
