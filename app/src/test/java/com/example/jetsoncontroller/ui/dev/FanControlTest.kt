package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.model.ControlCapabilities
import com.example.jetsoncontroller.model.FanStatus
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import org.junit.Assert.*
import org.junit.Test

class FanControlTest {
    private val ready = DashboardUiState(fullControlAvailable = true,
        capabilities = ControlCapabilities(fanControl = true),
        fanStatus = FanStatus(true, "AUTO", rpm = 3200, autoAvailable = true))

    @Test fun retainsCheckingErrorUnavailableAndOfflineStates() {
        assertEquals(FanPageState.READY, fanPageState(ready))
        assertEquals(FanPageState.CHECKING, fanPageState(ready.copy(fanLoading = true)))
        assertEquals(FanPageState.CHECKING, fanPageState(ready.copy(fanStatus = null)))
        assertEquals(FanPageState.ERROR, fanPageState(ready.copy(fanError = "failed")))
        assertEquals(FanPageState.UNAVAILABLE, fanPageState(ready.copy(fanStatus = ready.fanStatus!!.copy(available = false))))
        assertEquals(FanPageState.UNAVAILABLE, fanPageState(ready.copy(capabilities = ControlCapabilities())))
        assertEquals(FanPageState.OFFLINE, fanPageState(ready.copy(fullControlAvailable = false)))
    }

    @Test fun manualValuesPreserveSupportedMinimumAndPercentage() {
        assertEquals((20..100 step 10).toList(), fanManualValues(20))
        assertEquals(40, fanManualPercent(40, 20))
        assertEquals(60, fanManualPercent(20, 60))
        assertEquals(100, fanManualPercent(120, 20))
        assertEquals(25, fanManualPercent(0, 25))
        assertEquals(listOf(100), fanManualValues(100))
        assertTrue(fanManualValues(65).all { it in 65..100 })
    }
}
