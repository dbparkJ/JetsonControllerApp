package com.example.jetsoncontroller.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpAuthSignerTest {

    @Test
    fun matchesBackendCanonicalRequestVector() {
        val signed = HttpAuthSigner.sign(
            secret = ByteArray(32) { it.toByte() },
            deviceId = "00000000-0000-0000-0000-000000000001",
            bootNonce = "boot-test",
            method = "POST",
            canonicalPath = "/v1/fs/list?root=data&path=hello%20world",
            body = "{\"value\":1}".toByteArray(),
            requestNonce = "request-0001",
            requestTimestampSeconds = 1700000000
        )

        assertEquals("00000000-0000-0000-0000-000000000001", signed.deviceId)
        assertEquals("request-0001", signed.requestNonce)
        assertEquals("1700000000", signed.requestTimestamp)
        assertEquals(
            "c86de8bc7bde150b2a13f24d6bada378b392960fcb9976592d42c85b9977d968",
            signed.signature
        )
    }

    @Test
    fun verifiesBackendResponseVector() {
        val valid = HttpAuthSigner.verifyResponse(
            secret = ByteArray(32) { it.toByte() },
            deviceId = "00000000-0000-0000-0000-000000000001",
            bootNonce = "boot-test",
            requestNonce = "request-0001",
            requestTimestamp = "1700000000",
            statusCode = 200,
            body = "{\"ok\":true}".toByteArray(),
            receivedSignature =
                "2e4ce1ab38bbde2508a3b4bd29457202cff60eef8dc9497177bf044dc78a3786"
        )

        assertEquals(true, valid)
    }

    @Test
    fun verifiesBackendTlsHelloProofVector() {
        val valid = HttpAuthSigner.verifyHello(
            secret = ByteArray(32) { it.toByte() },
            apiVersion = 1,
            deviceId = "00000000-0000-0000-0000-000000000001",
            deviceName = "MMS-TEST",
            bootNonce = "boot-test",
            serverTimeEpochSeconds = 1700000000,
            authScheme = "JETSONHTTP2",
            tlsCertificateSha256 = "a".repeat(64),
            receivedProof =
                "d7b17ec56db9ac32a9a21c9dbef011f7f9024a90112731c365606fb4035abc6a"
        )

        assertEquals(true, valid)
    }
}
