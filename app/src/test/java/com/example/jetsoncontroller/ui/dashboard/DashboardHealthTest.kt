package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
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
    fun savedDismissal_hidesTheSameUploadButShowsANewFailureWithTheSameCount() {
        val upload = failedUpload("upload-a")
        val original = uploadHealth(listOf(upload))
        // Preferences restore the same stable keys in a new app process.
        val restored = dismissDashboardHealth(emptySet(), "device-a", original).toList().toSet()

        assertTrue(isDashboardHealthDismissed(restored, "device-a", uploadHealth(listOf(upload.copy()))))
        assertFalse(isDashboardHealthDismissed(restored, "device-b", original))
        assertFalse(isDashboardHealthDismissed(restored, "device-a", uploadHealth(listOf(failedUpload("upload-b")))))
        assertFalse(isDashboardHealthDismissed(restored, "device-a", uploadHealth(listOf(upload.copy(errorMessage = "인증 실패")))))
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
    fun acknowledgedUploadsStayHiddenWhenTheListIsReorderedOrAnOldFailureIsRemoved() {
        val first = failedUpload("upload-a")
        val second = failedUpload("upload-b")
        val dismissals = dismissDashboardHealth(emptySet(), "device-a", uploadHealth(listOf(first, second)))

        assertTrue(isDashboardHealthDismissed(dismissals, "device-a", uploadHealth(listOf(second, first))))
        assertTrue(isDashboardHealthDismissed(dismissals, "device-a", uploadHealth(listOf(second))))
        assertFalse(isDashboardHealthDismissed(dismissals, "device-a", uploadHealth(listOf(second, failedUpload("upload-c")))))
    }

    private fun uploadHealth(uploads: List<UploadJob>): DashboardHealth = assessDashboardHealth(
        JetsonStatus(temperatureC = 45f, storagePercent = 40),
        StatusFreshness.CURRENT,
        emptyList(),
        uploads
    )

    private fun failedUpload(id: String) = UploadJob(
        id = id,
        rootId = "recordings",
        relativePath = "session",
        targetId = "server",
        state = UploadJobState.FAILED,
        bytesTotal = null,
        bytesTransferred = null,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = "연결 시간 초과"
    )

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
