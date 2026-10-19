package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricPresentationTest {
    @Test
    fun unavailableMeasurementsAreNotPresentedAsHealthyZeroValues() {
        val status = JetsonStatus(metricValidity = mapOf("temperatureC" to MetricValidity("unavailable")))
        assertEquals("미확인", status.metricDisplay("temperatureC", "0 C"))
        assertEquals(
            DashboardHealthLevel.ATTENTION,
            assessDashboardHealth(status, StatusFreshness.CURRENT, emptyList(), emptyList()).level
        )
    }

    @Test
    fun invalidMeasurementsNeverDisplayNumbersEvenWhenTheyHaveAnObservedTime() {
        listOf("stale", "unavailable", "invalid", "unknown").forEach { validity ->
            val status = JetsonStatus(metricValidity = mapOf("cpuPercent" to MetricValidity(validity, 1_000L)))
            assertEquals("미확인", status.metricDisplay("cpuPercent", "0%"))
            assertEquals("미확인", status.metricDisplay("cpuPercent", "32%"))
            assertFalse(status.metricIsValid("cpuPercent"))
        }
    }

    @Test
    fun validZeroAndLegacyReadingsRemainCompatible() {
        val status = JetsonStatus(metricValidity = mapOf("cpuPercent" to MetricValidity("valid")))
        assertEquals("0%", status.metricDisplay("cpuPercent", "0%"))
        assertTrue(status.metricIsValid("cpuPercent"))
        assertEquals("32%", JetsonStatus().metricDisplay("cpuPercent", "32%"))
    }

    @Test
    fun memorySummaryDoesNotExposeAnInvalidTotal() {
        val status = JetsonStatus(metricValidity = mapOf(
            "ramUsedMb" to MetricValidity("valid"),
            "ramTotalMb" to MetricValidity("unavailable")
        ))
        assertEquals("미확인", status.metricDisplay("ramUsedMb", "1 GB / 0 GB"))
    }
}
