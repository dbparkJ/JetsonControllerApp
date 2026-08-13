package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardHealthTest {
    @Test
    fun currentNormalStatus_isHealthy() {
        val health = assessDashboardHealth(
            status = JetsonStatus(temperatureC = 45f, storagePercent = 40),
            freshness = StatusFreshness.CURRENT,
            pipelines = emptyList(),
            uploads = emptyList()
        )

        assertEquals(DashboardHealthLevel.HEALTHY, health.level)
    }

    @Test
    fun highTemperatureAndStorage_needAttention() {
        val health = assessDashboardHealth(
            status = JetsonStatus(temperatureC = 82f, storagePercent = 93),
            freshness = StatusFreshness.CURRENT,
            pipelines = emptyList(),
            uploads = emptyList()
        )

        assertEquals(DashboardHealthLevel.ATTENTION, health.level)
        assertEquals(2, health.issues.size)
        assertTrue(health.issues.first().contains("82"))
    }
}
