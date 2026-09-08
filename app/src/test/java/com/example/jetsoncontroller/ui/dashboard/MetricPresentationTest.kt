package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricPresentationTest {
    @Test
    fun unavailableMeasurementsAreNotPresentedAsHealthyZeroValues() {
        val status = JetsonStatus(metricValidity = mapOf("temperatureC" to MetricValidity("unavailable")))
        assertEquals("확인 불가", status.metricDisplay("temperatureC", "0 C"))
        assertEquals(
            DashboardHealthLevel.ATTENTION,
            assessDashboardHealth(status, StatusFreshness.CURRENT, emptyList(), emptyList()).level
        )
    }

    @Test
    fun staleMeasurementsAreMarkedAndLegacyReadingsRemainCompatible() {
        val status = JetsonStatus(metricValidity = mapOf("cpuPercent" to MetricValidity("stale", 1_000L)))
        assertTrue(status.metricDisplay("cpuPercent", "32%").contains("이전 측정"))
        assertEquals("32%", JetsonStatus().metricDisplay("cpuPercent", "32%"))
    }
}
