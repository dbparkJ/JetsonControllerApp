package com.example.jetsoncontroller.data.diagnostics

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DiagnosticPrivacyTest {
    @After fun clearSink() { ConnectionDiagnostics.setSinkForTests(null) }

    @Test fun `unknown fields secret shaped strings and payloads do not enter the sink`() {
        var captured = emptyMap<String, Any?>()
        ConnectionDiagnostics.setSinkForTests { _, fields, _ -> captured = fields }
        ConnectionDiagnostics.record("api_failure", mapOf(
            "token" to "secret", "body" to "sensitive-payload", "ip" to "192.0.2.5",
            "reasonCode" to "a-valid-looking-secret", "exceptionClass" to "IOException: secret",
            "route" to "/files/private-name?token=secret", "requestId" to "unsafe-id",
            "durationMs" to Double.NaN, "httpStatus" to 503, "authenticated" to false
        ))
        assertEquals(mapOf("httpStatus" to 503, "authenticated" to false), captured)
    }

    @Test fun `random request correlation and salted address references survive privacy filtering`() {
        val id = ConnectionDiagnostics.newId()
        val reference = ConnectionDiagnostics.privateRef("192.0.2.5")!!
        assertEquals(reference, ConnectionDiagnostics.privateRef("192.0.2.5"))
        assertNotEquals(reference, ConnectionDiagnostics.privateRef("192.0.2.6"))
        assertTrue(reference.matches(Regex("[0-9a-f]{16}")))
        assertEquals(mapOf("requestId" to id, "remoteAddressRef" to reference),
            DiagnosticPrivacy.fields(mapOf("requestId" to id, "remoteAddressRef" to reference)))
    }

    @Test fun `uninitialized recorder and a broken diagnostic sink do not throw`() {
        ConnectionDiagnostics.record("api_response", mapOf("httpStatus" to 200))
        ConnectionDiagnostics.setSinkForTests { _, _, _ -> throw IOException("must not reach caller") }
        ConnectionDiagnostics.record("api_failure", mapOf("httpStatus" to 503), incident = true)
    }

    @Test fun `unrecognized event cannot leak user text`() {
        var calls = 0
        ConnectionDiagnostics.setSinkForTests { _, _, _ -> calls++ }
        ConnectionDiagnostics.record("arbitrary-user-secret", mapOf("httpStatus" to 200))
        assertEquals(0, calls)
    }
}
