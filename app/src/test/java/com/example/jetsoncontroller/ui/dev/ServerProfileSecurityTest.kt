package com.example.jetsoncontroller.ui.dev

import org.junit.Assert.*
import org.junit.Test

class ServerProfileSecurityTest {
    @Test fun consumingInputClearsItAndObjectDescriptionsNeverRevealIt() {
        val secret = DevSecretInput()
        var input by secret
        input = "emp_example_private"
        assertFalse(secret.toString().contains(input))
        assertEquals("emp_example_private", secret.consume())
        assertEquals("", input)
        assertEquals("", secret.consume())
        var recreatedInput by DevSecretInput()
        assertEquals("", recreatedInput)
    }

    @Test fun invalidTokenInputIsRejectedWithoutReturningItsContent() {
        assertTrue(validProfileToken("emp_example"))
        assertFalse(validProfileToken("emp_"))
        assertFalse(validProfileToken("emp_example\n"))
        assertFalse(validProfileToken("employee token"))
        assertEquals("운영 서버를", devObject("운영 서버"))
        assertEquals("시험 환경을", devObject("시험 환경"))
    }
}
