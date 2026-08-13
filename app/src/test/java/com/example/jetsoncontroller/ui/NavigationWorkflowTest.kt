package com.example.jetsoncontroller.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationWorkflowTest {
    @Test
    fun `explicit connection flows open control home while device hub stays visible`() {
        assertFalse(isConnectionEntryRoute("connection_hub"))
        assertTrue(isConnectionEntryRoute("devices_ble"))
        assertTrue(isConnectionEntryRoute("wifi_direct"))
        assertFalse(isConnectionEntryRoute("dashboard"))
        assertFalse(isConnectionEntryRoute(null))
    }
}
