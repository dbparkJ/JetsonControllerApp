package com.example.jetsoncontroller.ui.dev

import org.junit.Assert.*
import org.junit.Test

class RemoteCommandTargetTest {
    @Test fun confirmationUsesTheCapturedCommandAndRevalidatesDeviceAndBusyState() {
        val pending = RemoteCommandTarget("device-a", "pwd")
        assertTrue(pending.canExecute("device-a", true, false))
        assertFalse(pending.canExecute("device-b", true, false))
        assertFalse(pending.canExecute("device-a", false, false))
        assertFalse(pending.canExecute("device-a", true, true))
        assertEquals("pwd", pending.command)
        assertFalse(RemoteCommandTarget("device-a", " ").canExecute("device-a", true, false))
        assertFalse(RemoteCommandTarget("device-a", "x".repeat(4097)).canExecute("device-a", true, false))
    }
}
