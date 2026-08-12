package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.JetsonStatus

data class AlertDecision(
    val storageLatched: Boolean,
    val temperatureLatched: Boolean,
    val notifyStorage: Boolean,
    val notifyTemperature: Boolean
)

object AlertThresholdEvaluator {
    fun evaluate(
        status: JetsonStatus,
        settings: AlertSettings,
        notificationsAllowed: Boolean
    ): AlertDecision {
        val notifyStorage = settings.storageEnabled &&
            status.storagePercent >= settings.storageThresholdPercent &&
            !settings.storageAlertLatched && notificationsAllowed
        val storageLatched = when {
            !settings.storageEnabled -> false
            notifyStorage -> true
            status.storagePercent <= settings.storageThresholdPercent - 3 -> false
            else -> settings.storageAlertLatched
        }

        val notifyTemperature = settings.temperatureEnabled &&
            status.temperatureC >= settings.temperatureThresholdC &&
            !settings.temperatureAlertLatched && notificationsAllowed
        val temperatureLatched = when {
            !settings.temperatureEnabled -> false
            notifyTemperature -> true
            status.temperatureC <= settings.temperatureThresholdC - 3 -> false
            else -> settings.temperatureAlertLatched
        }

        return AlertDecision(
            storageLatched = storageLatched,
            temperatureLatched = temperatureLatched,
            notifyStorage = notifyStorage,
            notifyTemperature = notifyTemperature
        )
    }
}
