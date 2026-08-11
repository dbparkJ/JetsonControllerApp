package com.example.jetsoncontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object UuidCodec {

    fun fromBytes(
        value: ByteArray
    ): UUID {

        require(value.size == 16) {
            "DEVICE_ID must be exactly 16 bytes."
        }

        val buffer =
            ByteBuffer
                .wrap(value)
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        return UUID(
            buffer.long,
            buffer.long
        )
    }


    fun toBytes(
        uuid: UUID
    ): ByteArray {

        return ByteBuffer
            .allocate(16)
            .order(
                ByteOrder.BIG_ENDIAN
            )
            .putLong(
                uuid.mostSignificantBits
            )
            .putLong(
                uuid.leastSignificantBits
            )
            .array()
    }
}
