package com.example.jetsoncontroller.ui.sensors

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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onSectionSelected: (ControlSection) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("센서 상태") }) },
        bottomBar = {
            ControlNavigationBar(ControlSection.SENSORS, onSectionSelected)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("연결된 센서") }
            item {
                SensorStatusRow(
                    icon = Icons.Default.CameraAlt,
                    name = "카메라",
                    configured = status.cameraConfigured,
                    running = status.cameraRunning
                )
            }
            item {
                SensorStatusRow(
                    icon = Icons.Default.Explore,
                    name = "GNSS",
                    configured = status.gnssConfigured,
                    running = status.gnssRunning
                )
            }
            item {
                SensorStatusRow(
                    icon = Icons.Default.Sensors,
                    name = "IMU",
                    configured = status.imuConfigured,
                    running = status.imuRunning
                )
            }
        }
    }
}

@Composable
private fun SensorStatusRow(
    icon: ImageVector,
    name: String,
    configured: Boolean,
    running: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                tint = if (running) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !configured -> "아직 서비스가 설정되지 않았습니다"
                        running -> "정상 동작 중"
                        else -> "연결됨 · 대기 또는 중지"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (running) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    if (running) "활성" else if (configured) "대기" else "미설정",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
