package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.data.alerts.AlertSettings

internal const val ALERT_SETTING_COUNT = 6

internal fun alertSettingsSummary(settings: AlertSettings): String {
    val enabled = listOf(
        settings.storageEnabled,
        settings.temperatureEnabled,
        settings.pipelineStartedEnabled,
        settings.pipelineFailedEnabled,
        settings.uploadStartedEnabled,
        settings.uploadEndedEnabled
    ).count { it }
    return ALERT_SETTING_COUNT.toString() + "개 중 " + enabled + "개"
}
