package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.JetsonStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertThresholdEvaluatorTest {
    @Test
    fun `notifies once while storage remains above threshold`() {
        val settings = AlertSettings(storageThresholdPercent = 85)
        val first = AlertThresholdEvaluator.evaluate(
            JetsonStatus(storagePercent = 90),
            settings,
            notificationsAllowed = true
        )
        assertTrue(first.notifyStorage)
        assertTrue(first.storageLatched)

        val repeated = AlertThresholdEvaluator.evaluate(
            JetsonStatus(storagePercent = 92),
            settings.copy(storageAlertLatched = first.storageLatched),
            notificationsAllowed = true
        )
        assertFalse(repeated.notifyStorage)
        assertTrue(repeated.storageLatched)
    }

    @Test
    fun `rearams after value drops below hysteresis`() {
        val settings = AlertSettings(
            temperatureThresholdC = 80,
            temperatureAlertLatched = true
        )
        val decision = AlertThresholdEvaluator.evaluate(
            JetsonStatus(temperatureC = 76f),
            settings,
            notificationsAllowed = true
        )
        assertFalse(decision.notifyTemperature)
        assertFalse(decision.temperatureLatched)
    }

    @Test
    fun `records and latches in-app alert when notification permission is missing`() {
        val decision = AlertThresholdEvaluator.evaluate(
            JetsonStatus(storagePercent = 95),
            AlertSettings(storageThresholdPercent = 85),
            notificationsAllowed = false
        )
        assertFalse(decision.notifyStorage)
        assertTrue(decision.storageTriggered)
        assertTrue(decision.storageLatched)
    }
}
