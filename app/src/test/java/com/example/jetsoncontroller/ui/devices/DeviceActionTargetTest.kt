package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.model.ControlCapabilities
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import org.junit.Assert.*
import org.junit.Test

class DeviceActionTargetTest {
    private val ready = DashboardUiState(isOnline = true, fullControlAvailable = true,
        statusFreshness = StatusFreshness.CURRENT, capabilities = ControlCapabilities(powerCommandsEnabled = true))

    @Test fun `power confirmation cannot follow selection or transport to another device`() {
        assertTrue(devicePowerTargetAllowed("A", "a", "A", ready, false))
        assertFalse(devicePowerTargetAllowed("A", "B", "B", ready, false))
        assertFalse(devicePowerTargetAllowed("A", "A", "B", ready, false))
        assertFalse(devicePowerTargetAllowed("A", "A", null, ready, false))
        assertFalse(devicePowerTargetAllowed("", "", "", ready, false))
    }

    @Test fun `run beginning after dialog opened blocks power without changing its target`() {
        assertFalse(devicePowerTargetAllowed("A", "A", "A", ready, true))
        assertEquals("수집 중에는 쓸 수 없습니다.", powerUnavailableReason(ready, true))
        assertFalse(devicePowerTargetAllowed("A", "A", "A", ready.copy(operationInProgress = true), false))
    }

    @Test fun `power requires current status capability and control rather than a BLE link`() {
        listOf(ready.copy(isOnline = false), ready.copy(fullControlAvailable = false),
            ready.copy(statusFreshness = StatusFreshness.UNKNOWN), ready.copy(statusFreshness = StatusFreshness.STALE),
            ready.copy(capabilities = ControlCapabilities(powerCommandsEnabled = false))).forEach {
            assertFalse(devicePowerTargetAllowed("A", "A", "A", it, false))
            assertTrue(powerUnavailableReason(it, false).isNotBlank())
        }
    }
}
