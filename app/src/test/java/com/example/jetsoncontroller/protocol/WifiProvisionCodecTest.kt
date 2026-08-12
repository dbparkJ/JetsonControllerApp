package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.WifiProvisionRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WifiProvisionCodecTest {

    @Test
    fun `encodes version flags lengths and utf8 values`() {
        val encoded = WifiProvisionCodec.encode(
            WifiProvisionRequest(
                ssid = "JetsonNet",
                password = "password123",
                hidden = true
            )
        )

        assertArrayEquals(
            byteArrayOf(1, 1, 9, 11),
            encoded.copyOfRange(0, 4)
        )
        assertEquals(
            "JetsonNetpassword123",
            String(encoded.copyOfRange(4, encoded.size))
        )
    }

    @Test
    fun `allows an open network`() {
        val encoded = WifiProvisionCodec.encode(
            WifiProvisionRequest("OpenNet", "")
        )

        assertEquals(0, encoded[3].toInt())
    }

    @Test
    fun `rejects invalid password length`() {
        assertThrows(IllegalArgumentException::class.java) {
            WifiProvisionCodec.encode(
                WifiProvisionRequest("JetsonNet", "short")
            )
        }
    }

    @Test
    fun `preserves significant ssid whitespace`() {
        val encoded = WifiProvisionCodec.encode(
            WifiProvisionRequest(" Studio ", "password123")
        )

        assertEquals(" Studio ", String(encoded.copyOfRange(4, 12)))
    }

    @Test
    fun `matches backend encrypted wifi vector`() {
        val sessionKey = PairingAuth.deriveSessionKey(
            deviceId = "00000000-0000-0000-0000-000000000001",
            bootstrapSecretHex = ByteArray(32) { it.toByte() }
                .joinToString("") { "%02x".format(it) },
            challenge = ByteArray(16) { it.toByte() }
        )
        assertArrayEquals(
            hexToBytes(
                "00c03ba2e92cd466dc132d90f0bc2698b280244043b60aee352fde5f04d5baca"
            ),
            sessionKey
        )
        val encoded = WifiProvisionCodec.encodeEncrypted(
            request = WifiProvisionRequest("JetsonNet", "password123", hidden = true),
            sessionKey = sessionKey,
            deviceId = "00000000-0000-0000-0000-000000000001",
            nonce = ByteArray(12) { it.toByte() }
        )

        assertArrayEquals(
            hexToBytes(
                "02000102030405060708090a0baa2b754289fe7603d1e122753af38aa834521d" +
                    "616ffc1dca2a0185d950f1dbab74938da5f9fc513c"
            ),
            encoded
        )
    }

    private fun hexToBytes(value: String): ByteArray = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
