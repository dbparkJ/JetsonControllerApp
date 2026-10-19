package com.example.jetsoncontroller.ui.alerts

import com.example.jetsoncontroller.data.alerts.*
import com.example.jetsoncontroller.ui.alertDestinationRoute
import com.example.jetsoncontroller.ui.navigation.Routes
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class AlertTargetNavigationTest {
    private fun record(target: AlertTarget? = null) = AlertRecord("alert", "장치 경고", "내용",
        AlertDestination.SENSORS, AlertSeverity.WARNING, 1, target = target)

    @Test fun oldHistoryKeepsItsGeneralDestination() {
        val old = Gson().fromJson("""{"id":"old","title":"경고","message":"내용","destination":"SENSORS","severity":"WARNING","createdAtEpochMillis":1}""", AlertRecord::class.java)
        assertNull(old.target)
        assertEquals(Routes.SENSORS, alertDestinationRoute(old))
    }

    @Test fun deviceAndRunTargetsSurviveHistoryRoundTrip() {
        val device = record(AlertTarget("device-a", "GEO-X-1"))
        assertEquals(Routes.DEVICE_DETAIL, alertDestinationRoute(device))
        val run = device.copy(destination = AlertDestination.PIPELINES,
            target = AlertTarget("device/a", pipelineId = "capture", runId = "run/older"))
        val restored = Gson().fromJson(Gson().toJson(run), AlertRecord::class.java)
        assertEquals(run, restored)
        assertEquals("run_result/run%2Folder?deviceId=device%2Fa&pipelineId=capture", alertDestinationRoute(restored))
    }
}
