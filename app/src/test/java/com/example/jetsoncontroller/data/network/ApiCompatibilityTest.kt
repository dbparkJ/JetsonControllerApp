package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.model.UploadTarget
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiCompatibilityTest {
    @Test
    fun `legacy upload target responses remain readable`() {
        val target = Gson().fromJson(
            """{"id":"external","label":"External"}""",
            UploadTarget::class.java
        )

        assertEquals("external", target.id)
        assertNull(target.baseUrl)
        assertFalse(target.editable)
    }

    @Test
    fun `unsigned status maps only recoverable responses to typed errors`() {
        assertTrue(unsignedResponseException(401) is JetsonSessionExpiredException)
        assertTrue(unsignedResponseException(404) is JetsonEndpointUnavailableException)
        assertTrue(unsignedResponseException(405) is JetsonEndpointUnavailableException)

        val serverError = unsignedResponseException(500)
        assertTrue(serverError is JetsonUnsignedServerErrorException)
        assertTrue(serverError.message.orEmpty().contains("HTTP 500"))
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
