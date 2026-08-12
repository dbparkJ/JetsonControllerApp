package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.InlineMessage
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(
    settings: AlertSettings,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onStorageEnabledChange: (Boolean) -> Unit,
    onStorageThresholdChange: (Int) -> Unit,
    onTemperatureEnabledChange: (Boolean) -> Unit,
    onTemperatureThresholdChange: (Int) -> Unit,
    onSectionSelected: (ControlSection) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("알림 설정") }) },
        bottomBar = {
            ControlNavigationBar(ControlSection.SETTINGS, onSectionSelected)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!notificationPermissionGranted) {
                item {
                    InlineMessage(
                        message = "임계치 경고를 받으려면 알림 권한을 허용해 주세요.",
                        isError = false
                    )
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Text("알림 권한 허용", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                ThresholdSetting(
                    icon = Icons.Default.Storage,
                    title = "저장공간",
                    valueLabel = "${settings.storageThresholdPercent}% 이상",
                    enabled = settings.storageEnabled,
                    value = settings.storageThresholdPercent.toFloat(),
                    range = 50f..99f,
                    steps = 48,
                    onEnabledChange = onStorageEnabledChange,
                    onValueChangeFinished = onStorageThresholdChange
                )
            }
            item {
                ThresholdSetting(
                    icon = Icons.Default.Thermostat,
                    title = "장비 온도",
                    valueLabel = "${settings.temperatureThresholdC} C 이상",
                    enabled = settings.temperatureEnabled,
                    value = settings.temperatureThresholdC.toFloat(),
                    range = 40f..110f,
                    steps = 69,
                    onEnabledChange = onTemperatureEnabledChange,
                    onValueChangeFinished = onTemperatureThresholdChange
                )
            }
            item {
                Text(
                    "경고는 임계치를 넘는 구간마다 최초 1회만 전송됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThresholdSetting(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    enabled: Boolean,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onEnabledChange: (Boolean) -> Unit,
    onValueChangeFinished: (Int) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        valueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onValueChangeFinished(sliderValue.roundToInt())
                },
                valueRange = range,
                steps = steps,
                enabled = enabled
            )
        }
    }
}
