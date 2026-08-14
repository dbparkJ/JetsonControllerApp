package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.JetsonStatus

data class AlertDecision(
    val storageLatched: Boolean,
    val temperatureLatched: Boolean,
    val storageTriggered: Boolean,
    val temperatureTriggered: Boolean,
    val notifyStorage: Boolean,
    val notifyTemperature: Boolean
)

object AlertThresholdEvaluator {
    fun evaluate(
        status: JetsonStatus,
        settings: AlertSettings,
        notificationsAllowed: Boolean
    ): AlertDecision {
        val storageTriggered = settings.storageEnabled &&
            status.storagePercent >= settings.storageThresholdPercent &&
            !settings.storageAlertLatched
        val notifyStorage = storageTriggered && notificationsAllowed
        val storageLatched = when {
            !settings.storageEnabled -> false
            storageTriggered -> true
            status.storagePercent <= settings.storageThresholdPercent - 3 -> false
            else -> settings.storageAlertLatched
        }

        val temperatureTriggered = settings.temperatureEnabled &&
            status.temperatureC >= settings.temperatureThresholdC &&
            !settings.temperatureAlertLatched
        val notifyTemperature = temperatureTriggered && notificationsAllowed
        val temperatureLatched = when {
            !settings.temperatureEnabled -> false
            temperatureTriggered -> true
            status.temperatureC <= settings.temperatureThresholdC - 3 -> false
            else -> settings.temperatureAlertLatched
        }

        return AlertDecision(
            storageLatched = storageLatched,
            temperatureLatched = temperatureLatched,
            storageTriggered = storageTriggered,
            temperatureTriggered = temperatureTriggered,
            notifyStorage = notifyStorage,
            notifyTemperature = notifyTemperature
        )
    }
}
