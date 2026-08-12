package com.example.jetsoncontroller.protocol

import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PairingAuth {

    private val CONTEXT =
        "JETSONCTRL1|"
            .toByteArray(
                Charsets.UTF_8
            )

    private val SEPARATOR =
        byteArrayOf(
            '|'.code.toByte()
        )

    private val SESSION_KEY_CONTEXT =
        "JETSONBLEENC1|".toByteArray(Charsets.UTF_8)


    fun computeResponse(
        deviceId: String,
        bootstrapSecretHex: String,
        challenge: ByteArray
    ): ByteArray {

        require(
            challenge.size == 16
        ) {
            "Invalid authentication challenge."
        }

        val secret =
            hexToBytes(
                bootstrapSecretHex
            )

        require(
            secret.size == 32
        )

        val deviceIdBytes =
            UuidCodec.toBytes(
                UUID.fromString(
                    deviceId
                )
            )

        val message =
            CONTEXT +
            deviceIdBytes +
            SEPARATOR +
            challenge

        val mac =
            Mac.getInstance(
                "HmacSHA256"
            )

        mac.init(
            SecretKeySpec(
                secret,
                "HmacSHA256"
            )
        )

        return mac
            .doFinal(message)
            .copyOfRange(
                0,
                16
            )
    }

    fun deriveSessionKey(
        deviceId: String,
        bootstrapSecretHex: String,
        challenge: ByteArray
    ): ByteArray {
        require(challenge.size == 16) {
            "Invalid authentication challenge."
        }
        val secret = hexToBytes(bootstrapSecretHex)
        require(secret.size == 32)
        val deviceIdBytes = UuidCodec.toBytes(UUID.fromString(deviceId))
        val message = SESSION_KEY_CONTEXT + deviceIdBytes + SEPARATOR + challenge
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message)
    }


    private fun hexToBytes(
        hex: String
    ): ByteArray {

        require(
            hex.length % 2 == 0
        )

        return ByteArray(
            hex.length / 2
        ) {
            index ->

            hex.substring(
                index * 2,
                index * 2 + 2
            )
                .toInt(16)
                .toByte()
        }
    }
}
