package com.example.jetsoncontroller.protocol

object CommandCodec {

    private const val MAGIC: Byte =
        0x5A

    private const val VERSION: Byte =
        0x01


    fun encode(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {

        require(
            payload.size <= 255
        ) {
            "Payload too large for BLE command frame"
        }

        val length =
            payload.size.toByte()

        val body =
            byteArrayOf(
                MAGIC,
                VERSION,
                command.id,
                length
            ) + payload

        val checksum =
            body.fold(0) { acc, byte ->
                (acc + (byte.toInt() and 0xFF)) and 0xFF
            }.toByte()

        return body +
            byteArrayOf(checksum)
    }
}
