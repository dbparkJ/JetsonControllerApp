package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.data.location.MobileLocationCallbacks
import com.example.jetsoncontroller.data.location.MobileLocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileLocationConsentTest {
    @Test fun entryAndRefreshNeverStartTrackingUntilRequested() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val tracker = FakeTracker()
        val model = MobileLocationViewModel(tracker, clock = { 1_000L }, elapsedRealtimeClock = { 1_000L })
        try {
            model.setVisible(true)
            model.refresh()
            assertEquals(0, tracker.starts)
            assertFalse(model.uiState.value.trackingEnabled)
            model.setTrackingRequested(true)
            assertEquals(1, tracker.starts)
            assertTrue(model.uiState.value.trackingEnabled)
            model.setVisible(false)
            assertFalse(model.uiState.value.trackingEnabled)
            model.setVisible(true)
            assertEquals(2, tracker.starts)
            model.setTrackingRequested(false)
            model.refresh()
            model.setVisible(false)
            model.setVisible(true)
            assertEquals(2, tracker.starts)
            assertFalse(model.uiState.value.trackingEnabled)
        } finally {
            model.setTrackingRequested(false)
            model.setVisible(false)
            Dispatchers.resetMain()
        }
    }

    private class FakeTracker : MobileLocationTracker {
        var starts = 0
        override fun start(callbacks: MobileLocationCallbacks) { starts++ }
        override fun stop() = Unit
    }
}
