package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.FanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FanLoadingStateTest {
    @Test
    fun cancelledRequestClearsLoadingGateWithoutDiscardingLastStatus() {
        val status = FanStatus(
            available = true,
            mode = "AUTO",
            percent = 38,
            rpm = 2_762
        )

        val finished = FanControlSnapshot(
            status = status,
            loading = true
        ).finishedLoading()

        assertFalse(finished.loading)
        assertEquals(status, finished.status)
    }
}
