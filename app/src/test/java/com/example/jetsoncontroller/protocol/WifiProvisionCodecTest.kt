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
}
