package com.example.jetsoncontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class BleTimeSyncTest {
    @Test
    fun `capability requires successful auth and explicit supported bit`() {
        assertFalse(BleTimeSync.supported(byteArrayOf()))
        assertFalse(BleTimeSync.supported(byteArrayOf(1)))
        assertFalse(BleTimeSync.supported(byteArrayOf(1, 0)))
        assertFalse(BleTimeSync.supported(byteArrayOf(1, 2)))
        assertFalse(BleTimeSync.supported(byteArrayOf(0, 1)))
        assertFalse(BleTimeSync.supported(byteArrayOf(2, 1)))
        assertTrue(BleTimeSync.supported(byteArrayOf(1, 1)))
        assertTrue(BleTimeSync.supported(byteArrayOf(1, 0x81.toByte())))
    }

    @Test
    fun `timestamp frame matches backend unsigned little endian protocol`() {
        val frame = BleTimeSync.command(1_792_728_600_000)
        assertArrayEquals(byteArrayOf(0x5A, 1, 8, 8), frame.take(4).toByteArray())
        val expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(1_792_728_600_000).array()
        assertArrayEquals(expected, frame.copyOfRange(4, 12))
        assertEquals(13, frame.size)
        assertEquals(frame.dropLast(1).sumOf { it.toInt() and 255 } and 255, frame.last().toInt() and 255)
    }

    @Test
    fun `timestamp range matches backend lower inclusive upper exclusive bounds`() {
        for (timestamp in listOf(1_577_836_800_000L, 4_102_444_799_999L)) {
            val frame = BleTimeSync.command(timestamp)
            assertEquals(timestamp, ByteBuffer.wrap(frame, 4, 8).order(ByteOrder.LITTLE_ENDIAN).long)
            assertEquals(frame.dropLast(1).sumOf { it.toInt() and 255 } and 255, frame.last().toInt() and 255)
        }
        for (timestamp in listOf(Long.MIN_VALUE, -1L, 0L, 1_577_836_799_999L, 4_102_444_800_000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { BleTimeSync.command(timestamp) }
        }
    }

    @Test
    fun `shared command encoding preserves unsigned checksum overflow and maximum payload length`() {
        val frame = CommandCodec.encodeId(8, ByteArray(255) { 0xFF.toByte() })
        assertEquals(260, frame.size)
        assertEquals(255, frame[3].toInt() and 255)
        assertEquals((0x5A + 1 + 8 + 255 + 255 * 255) and 255, frame.last().toInt() and 255)
        assertThrows(IllegalArgumentException::class.java) { CommandCodec.encodeId(8, ByteArray(256)) }
        assertArrayEquals(byteArrayOf(0x5A, 1, JetsonCommand.GET_STATUS.id, 0,
            (0x5A + 1 + JetsonCommand.GET_STATUS.id).toByte()), CommandCodec.encode(JetsonCommand.GET_STATUS))
    }
}
