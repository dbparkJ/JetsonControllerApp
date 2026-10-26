package com.example.jetsoncontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object BleTimeSync {
    fun supported(authState: ByteArray): Boolean =
        authState.size >= 2 && authState[0] == 1.toByte() && (authState[1].toInt() and 0x01) != 0

    fun command(epochMillis: Long): ByteArray {
        require(epochMillis >= 1_577_836_800_000L && epochMillis < 4_102_444_800_000L)
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(epochMillis).array()
        return CommandCodec.encodeId(0x08, payload)
    }
}
