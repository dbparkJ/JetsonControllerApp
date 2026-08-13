package com.example.jetsoncontroller.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiCompatibilityTest {
    @Test
    fun `unsigned status maps only recoverable responses to typed errors`() {
        assertTrue(unsignedResponseException(401) is JetsonSessionExpiredException)
        assertTrue(unsignedResponseException(404) is JetsonEndpointUnavailableException)

        val integrityError = unsignedResponseException(500)
        assertEquals(IOException::class.java, integrityError::class.java)
        assertEquals("Jetson 응답 인증에 실패했습니다.", integrityError.message)
    }

    @Test
    fun `legacy fallback runs only when endpoint is unavailable`() = runBlocking {
        var fallbackCalls = 0
        val value = withLegacyEndpointFallback(
            call = { throw JetsonEndpointUnavailableException() },
            fallback = {
                fallbackCalls += 1
                "legacy"
            }
        )

        assertEquals("legacy", value)
        assertEquals(1, fallbackCalls)
    }

    @Test(expected = IOException::class)
    fun `legacy fallback does not hide integrity failures`() {
        runBlocking {
            withLegacyEndpointFallback(
                call = { throw IOException("invalid signature") },
                fallback = { "legacy" }
            )
        }
    }
}
