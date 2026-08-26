package com.example.jetsoncontroller.data.rtk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileRtkRelayManagerTest {
    @Test
    fun `rewrites local relay host while preserving authorization and gga bytes`() {
        val request = (
            "GET /YANJ-RTCM31 HTTP/1.0\r\n" +
                "Host: 192.168.49.71\r\n" +
                "Authorization: Basic c2VjcmV0\r\n" +
                "\r\n" +
                "${'$'}GPGGA,relay-payload\r\n"
            ).toByteArray(Charsets.ISO_8859_1)

        val rewritten = rewriteNtripHostHeader(
            request,
            "www.gnssdata.or.kr",
            2101
        ).toString(Charsets.ISO_8859_1)

        assertTrue(rewritten.contains("Host: www.gnssdata.or.kr:2101\r\n"))
        assertTrue(rewritten.contains("Authorization: Basic c2VjcmV0\r\n"))
        assertTrue(rewritten.endsWith("${'$'}GPGGA,relay-payload\r\n"))
    }

    @Test
    fun `adds a missing host header without changing the request target`() {
        val request = "GET /MOUNT HTTP/1.0\nUser-Agent: test\n\n".toByteArray()

        val rewritten = rewriteNtripHostHeader(request, "caster.example", 80)
            .toString(Charsets.ISO_8859_1)

        assertEquals(
            "GET /MOUNT HTTP/1.0\nHost: caster.example\nUser-Agent: test\n\n",
            rewritten
        )
    }
}
