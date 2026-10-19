package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.navigation.primaryNavigationVisible
import com.example.jetsoncontroller.ui.runResultRoute
import org.junit.Assert.*
import org.junit.Test

/** F5 stop path. Device QA still exercises the composed navigation graph. */
class TapCountTest {
    @Test fun `F5 stop and confirm are two taps and terminal observation opens the exact result`() {
        val run = runStopFixture()
        var flow = RunStopFlow()
        var taps = 0
        var commands = 0
        var destination = Routes.ACTIVE_RUN
        listOf(RunStopEvent.REQUEST, RunStopEvent.CONFIRM).forEach { tap ->
            taps++
            val next = reduceRunStop(flow, tap, run, true, true)
            flow = next.flow
            if (next.command != null) commands++
            assertEquals(Routes.ACTIVE_RUN, destination)
        }
        val terminal = reduceRunStop(flow, RunStopEvent.OBSERVE, run.copy(active = false, state = "STOPPED"), true, false)
        terminal.openResult?.let { destination = runResultRoute(it.runId, it.deviceId, it.pipelineId) }
        assertEquals(2, taps)
        assertEquals(1, commands)
        assertEquals("run_result/run-1?deviceId=device-a&pipelineId=capture", destination)
        assertFalse(primaryNavigationVisible(Routes.ACTIVE_RUN))
        assertTrue(primaryNavigationVisible(Routes.RUN_RESULT))
    }
}
