package com.example.jetsoncontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCodecTest {
    private fun basePacket(): ByteArray =
        ByteBuffer.allocate(14)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(1)
            .put(10)
            .put(20)
            .put(30)
            .put(40)
            .put(0x09)
            .putInt(1024)
            .putInt(2048)
            .array()

    @Test
    fun `legacy packet remains compatible and reports unknown wifi as disconnected`() {
        val status = StatusCodec.decode(basePacket())

        assertEquals(10, status.cpuPercent)
        assertTrue(status.cameraRunning)
        assertTrue(status.mmsRunning)
        assertFalse(status.wifiConnected)
        assertNull(status.wifiSsid)
    }

    @Test
    fun `extended packet decodes connected wifi and ssid`() {
        val ssid = "Field Wi-Fi".toByteArray()
        val packet = basePacket() + byteArrayOf(0x01, ssid.size.toByte()) + ssid

        val status = StatusCodec.decode(packet)

        assertTrue(status.wifiConnected)
        assertEquals("Field Wi-Fi", status.wifiSsid)
    }

    @Test
    fun `disconnected extension ignores stale ssid`() {
        val ssid = "stale".toByteArray()
        val packet = basePacket() + byteArrayOf(0x00, ssid.size.toByte()) + ssid

        val status = StatusCodec.decode(packet)

        assertFalse(status.wifiConnected)
        assertNull(status.wifiSsid)
    }

    @Test
    fun `malformed ssid length is rejected`() {
        val packet = basePacket() + byteArrayOf(0x01, 0x04, 0x41)

        assertThrows(IllegalArgumentException::class.java) {
            StatusCodec.decode(packet)
        }
    }
}
