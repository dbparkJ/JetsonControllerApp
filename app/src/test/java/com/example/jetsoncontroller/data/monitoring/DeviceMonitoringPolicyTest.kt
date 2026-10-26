package com.example.jetsoncontroller.data.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMonitoringPolicyTest {
    @Test fun foregroundConnectionToTheSelectedDeviceCanStartMonitoring() {
        assertTrue(deviceMonitoringCanStart(true, true, "device-A", "DEVICE-a"))
    }

    @Test fun backgroundOrExplicitlyDisconnectedSessionsCannotStartAService() {
        assertFalse(deviceMonitoringCanStart(false, true, "A", "A"))
        assertFalse(deviceMonitoringCanStart(true, false, "A", "A"))
    }

    @Test fun staleConnectionsAndAbsentTargetsCannotStartMonitoring() {
        assertFalse(deviceMonitoringCanStart(true, true, "A", "B"))
        assertFalse(deviceMonitoringCanStart(true, true, "A", null))
        assertFalse(deviceMonitoringCanStart(true, true, null, null))
        assertFalse(deviceMonitoringCanStart(true, true, "", ""))
    }
}
