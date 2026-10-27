package com.example.jetsoncontroller.ui.dev

import org.junit.Assert.*
import org.junit.Test

class RemoteCommandTargetTest {
    @Test fun emptyCommandExplainsWhyExecutionIsDisabled() {
        assertEquals("명령어를 입력하세요.", terminalDisabledReason("device-a", true, false, ""))
        assertEquals("명령어를 입력하세요.", terminalDisabledReason("device-a", true, false, "  "))
        assertEquals("장치 연결 후 명령을 실행할 수 있습니다.", terminalDisabledReason("device-a", false, false, "pwd"))
        assertEquals("장치 연결 후 명령을 실행할 수 있습니다.", terminalDisabledReason(null, true, false, "pwd"))
        assertEquals("명령을 실행하고 있습니다.", terminalDisabledReason("device-a", true, true, "pwd"))
        assertNull(terminalDisabledReason("device-a", true, false, "pwd"))
    }

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
