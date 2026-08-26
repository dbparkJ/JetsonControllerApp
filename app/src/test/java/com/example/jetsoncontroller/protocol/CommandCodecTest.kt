package com.example.jetsoncontroller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandCodecTest {
    @Test
    fun `request wifi direct uses authenticated backend command id`() {
        assertEquals(0x08, JetsonCommand.REQUEST_WIFI_DIRECT.id.toInt())
        assertArrayEquals(
            byteArrayOf(0x5A, 0x01, 0x08, 0x00, 0x63),
            CommandCodec.encode(JetsonCommand.REQUEST_WIFI_DIRECT)
        )
    }
}
