package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertSettings
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.dev.DevSection as GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoStateBlock
import com.example.jetsoncontroller.ui.components.geo.GeoStateContent
import com.example.jetsoncontroller.ui.components.geo.GeoStateKind
import com.example.jetsoncontroller.ui.components.geo.GeoSwitchRow
import com.example.jetsoncontroller.ui.components.geo.GeoButton

@Suppress("UNUSED_PARAMETER")
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
    onUploadStartedEnabledChange: (Boolean) -> Unit,
    onUploadEndedEnabledChange: (Boolean) -> Unit,
    onSectionSelected: (GeoTab) -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    developerModeEnabled: Boolean = false,
    onBack: () -> Unit = {}
) {
    GeoLargeTitleScaffold("알림 기준", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (!notificationPermissionGranted) {
                item {
                    GeoStateBlock(
                        GeoStateContent(
                            kind = GeoStateKind.PERMISSION,
                            title = "알림 권한이 꺼져 있습니다",
                            explanation = "켜 둔 알림을 받으려면 권한을 허용하세요."
                        )
                    )
                    GeoButton("권한 허용", onRequestNotificationPermission, Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = com.example.jetsoncontroller.ui.components.geo.GeoButtonStyle.Secondary)
                }
            }
            item {
                GeoSection(
                    title = "장치 상태",
                    first = notificationPermissionGranted,
                    note = "기준을 넘을 때마다 한 번만 알립니다."
                ) {
                    AlertThresholdControl(
                        title = "저장 공간",
                        enabled = settings.storageEnabled,
                        threshold = settings.storageThresholdPercent,
                        range = 50..99,
                        valueLabel = { "$it% 이상" },
                        description = { "$it% 이상이면 알립니다." },
                        onEnabledChange = {
                            requestPermissionWhenEnabling(
                                it, notificationPermissionGranted, onRequestNotificationPermission
                            )
                            onStorageEnabledChange(it)
                        },
                        onThresholdChange = onStorageThresholdChange
                    )
                    AlertThresholdControl(
                        title = "장치 온도",
                        enabled = settings.temperatureEnabled,
                        threshold = settings.temperatureThresholdC,
                        range = 40..110,
                        valueLabel = { "$it°C 이상" },
                        description = { "$it°C 이상이면 알립니다." },
                        onEnabledChange = {
                            requestPermissionWhenEnabling(
                                it, notificationPermissionGranted, onRequestNotificationPermission
                            )
                            onTemperatureEnabledChange(it)
                        },
                        onThresholdChange = onTemperatureThresholdChange
                    )
                }
            }
            item {
                GeoSection(title = "작업") {
                    AlertToggle(
                        "작업 시작", settings.pipelineStartedEnabled,
                        onChange = {
                            requestPermissionWhenEnabling(it, notificationPermissionGranted, onRequestNotificationPermission)
                            onPipelineStartedEnabledChange(it)
                        }
                    )
                    AlertToggle(
                        "오류로 끝남", settings.pipelineFailedEnabled,
                        onChange = {
                            requestPermissionWhenEnabling(it, notificationPermissionGranted, onRequestNotificationPermission)
                            onPipelineFailedEnabledChange(it)
                        }
                    )
                }
            }
            item {
                GeoSection(title = "전송") {
                    AlertToggle(
                        "전송 시작", settings.uploadStartedEnabled,
                        onChange = {
                            requestPermissionWhenEnabling(it, notificationPermissionGranted, onRequestNotificationPermission)
                            onUploadStartedEnabledChange(it)
                        }
                    )
                    AlertToggle(
                        "전송 끝남", settings.uploadEndedEnabled,
                        onChange = {
                            requestPermissionWhenEnabling(it, notificationPermissionGranted, onRequestNotificationPermission)
                            onUploadEndedEnabledChange(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertThresholdControl(
    title: String,
    enabled: Boolean,
    threshold: Int,
    range: IntRange,
    valueLabel: (Int) -> String,
    description: (Int) -> String,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    var sliderValue by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }
    val roundedValue = alertThresholdValue(sliderValue, range)
    Column(Modifier.fillMaxWidth()) {
        GeoSwitchRow(
            title = title,
            description = description(roundedValue),
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
        GeoAlertThresholdSlider(
            title = title,
            value = roundedValue,
            valueLabel = valueLabel(roundedValue),
            onValueChange = { sliderValue = it.toFloat() },
            range = range,
            enabled = enabled,
            onStep = { sliderValue = it.toFloat(); onThresholdChange(it) },
            onValueChangeFinished = { onThresholdChange(alertThresholdValue(sliderValue, range)) }
        )
    }
}

@Composable
private fun AlertToggle(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    GeoSwitchRow(title = title, checked = enabled, onCheckedChange = onChange)
}

private fun requestPermissionWhenEnabling(
    enabling: Boolean,
    permissionGranted: Boolean,
    requestPermission: () -> Unit
) {
    if (enabling && !permissionGranted) requestPermission()
}
