package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun changingMetricValue_keepsStableDismissalKey() {
        val first = health(JetsonStatus(temperatureC = 82f))
        val updated = health(JetsonStatus(temperatureC = 87f))

        assertEquals(dashboardHealthKey(first), dashboardHealthKey(updated))

        val dismissals = dismissDashboardHealth(emptySet(), "device-a", first)
        assertTrue(isDashboardHealthDismissed(dismissals, "device-a", updated))
    }

    @Test
    fun reconnect_keepsDismissal_butHealthyRecoveryRearmsIt() {
        val attention = health(JetsonStatus(temperatureC = 85f))
        val dismissals = dismissDashboardHealth(emptySet(), "device-a", attention)

        val whileOffline = reconcileDashboardHealthDismissals(
            dismissals,
            deviceId = "device-a",
            health = attention,
            online = false
        )
        assertEquals(dismissals, whileOffline)
        assertTrue(isDashboardHealthDismissed(whileOffline, "device-a", attention))

        val afterRecovery = reconcileDashboardHealthDismissals(
            whileOffline,
            deviceId = "device-a",
            health = health(JetsonStatus(temperatureC = 45f, storagePercent = 40)),
            online = true
        )
        assertFalse(isDashboardHealthDismissed(afterRecovery, "device-a", attention))
    }

    @Test
    fun dismissingCombinedIssues_keepsRemainingIssueHidden_butNewKindIsVisible() {
        val combined = health(JetsonStatus(temperatureC = 85f, storagePercent = 95))
        val dismissals = dismissDashboardHealth(emptySet(), "device-a", combined)

        val storageOnly = health(JetsonStatus(temperatureC = 45f, storagePercent = 95))
        assertTrue(isDashboardHealthDismissed(dismissals, "device-a", storageOnly))

        val storageAndNewStaleIssue = health(
            status = JetsonStatus(temperatureC = 45f, storagePercent = 95),
            freshness = StatusFreshness.STALE
        )
        assertFalse(
            isDashboardHealthDismissed(
                dismissals,
                "device-a",
                storageAndNewStaleIssue
            )
        )
    }

    @Test
    fun dismissals_areDeviceAndIssueScoped_untilHealthy() {
        val temperature = health(JetsonStatus(temperatureC = 85f))
        val storage = health(JetsonStatus(storagePercent = 95))
        val firstDevice = dismissDashboardHealth(emptySet(), "device-a", temperature)
        val bothDevices = dismissDashboardHealth(firstDevice, "device-b", temperature)

        val changed = reconcileDashboardHealthDismissals(
            bothDevices,
            deviceId = "device-a",
            health = storage,
            online = true
        )

        assertTrue(isDashboardHealthDismissed(changed, "device-a", temperature))
        assertFalse(isDashboardHealthDismissed(changed, "device-a", storage))
        assertTrue(isDashboardHealthDismissed(changed, "device-b", temperature))

        val bothIssues = dismissDashboardHealth(changed, "device-a", storage)
        assertTrue(isDashboardHealthDismissed(bothIssues, "device-a", temperature))
        assertTrue(isDashboardHealthDismissed(bothIssues, "device-a", storage))

        val recovered = reconcileDashboardHealthDismissals(
            bothIssues,
            deviceId = "device-a",
            health = health(JetsonStatus(temperatureC = 45f, storagePercent = 40)),
            online = true
        )
        assertFalse(isDashboardHealthDismissed(recovered, "device-a", temperature))
        assertFalse(isDashboardHealthDismissed(recovered, "device-a", storage))
        assertTrue(isDashboardHealthDismissed(recovered, "device-b", temperature))
    }

    private fun health(
        status: JetsonStatus,
        freshness: StatusFreshness = StatusFreshness.CURRENT
    ): DashboardHealth = assessDashboardHealth(
        status = status,
        freshness = freshness,
        pipelines = emptyList(),
        uploads = emptyList()
    )
}
