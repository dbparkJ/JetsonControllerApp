package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
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
    onPipelineStartedEnabledChange: (Boolean) -> Unit,
    onPipelineFailedEnabledChange: (Boolean) -> Unit,
    onSectionSelected: (ControlSection) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("알림 설정") }) },
        bottomBar = {
            ControlNavigationBar(ControlSection.SETTINGS, onSectionSelected)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!notificationPermissionGranted) {
                NotificationPermissionBanner(onRequestNotificationPermission)
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("장비 상태") },
                    icon = { Icon(Icons.Default.Thermostat, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("작업") },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> HealthAlertSettings(
                    settings = settings,
                    onStorageEnabledChange = onStorageEnabledChange,
                    onStorageThresholdChange = onStorageThresholdChange,
                    onTemperatureEnabledChange = onTemperatureEnabledChange,
                    onTemperatureThresholdChange = onTemperatureThresholdChange,
                    modifier = Modifier.weight(1f)
                )
                else -> PipelineAlertSettings(
                    settings = settings,
                    onPipelineStartedEnabledChange = onPipelineStartedEnabledChange,
                    onPipelineFailedEnabledChange = onPipelineFailedEnabledChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text(
                    "알림 권한이 꺼져 있습니다",
                    modifier = Modifier.padding(start = 10.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "설정한 장비 및 작업 알림을 받으려면 권한을 허용해 주세요.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text("권한 허용")
            }
        }
    }
}

@Composable
private fun HealthAlertSettings(
    settings: AlertSettings,
    onStorageEnabledChange: (Boolean) -> Unit,
    onStorageThresholdChange: (Int) -> Unit,
    onTemperatureEnabledChange: (Boolean) -> Unit,
    onTemperatureThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ThresholdSetting(
                icon = Icons.Default.Storage,
                title = "저장공간",
                enabled = settings.storageEnabled,
                value = settings.storageThresholdPercent.toFloat(),
                valueLabel = { "$it% 이상" },
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
                enabled = settings.temperatureEnabled,
                value = settings.temperatureThresholdC.toFloat(),
                valueLabel = { "$it C 이상" },
                range = 40f..110f,
                steps = 69,
                onEnabledChange = onTemperatureEnabledChange,
                onValueChangeFinished = onTemperatureThresholdChange
            )
        }
        item {
            Text(
                "임계치를 넘는 구간마다 최초 1회만 알립니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PipelineAlertSettings(
    settings: AlertSettings,
    onPipelineStartedEnabledChange: (Boolean) -> Unit,
    onPipelineFailedEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NotificationSetting(
                icon = Icons.Default.PlayArrow,
                title = "작업 시작",
                description = "작업 상태가 실행 중으로 바뀌면 알립니다.",
                enabled = settings.pipelineStartedEnabled,
                onEnabledChange = onPipelineStartedEnabledChange
            )
        }
        item {
            NotificationSetting(
                icon = Icons.Default.ErrorOutline,
                iconTint = MaterialTheme.colorScheme.error,
                title = "오류 종료",
                description = "작업이 실패 상태로 종료되면 로그 확인 알림을 보냅니다.",
                enabled = settings.pipelineFailedEnabled,
                onEnabledChange = onPipelineFailedEnabledChange
            )
        }
    }
}

@Composable
private fun NotificationSetting(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun ThresholdSetting(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    value: Float,
    valueLabel: (Int) -> String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onEnabledChange: (Boolean) -> Unit,
    onValueChangeFinished: (Int) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val roundedValue = sliderValue.roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        valueLabel(roundedValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChangeFinished(roundedValue) },
                valueRange = range,
                steps = steps,
                enabled = enabled
            )
        }
    }
}
