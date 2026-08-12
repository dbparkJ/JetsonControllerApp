package com.example.jetsoncontroller.data.network

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HttpAuthSigner {

    data class SignedHeaders(
        val deviceId: String,
        val requestNonce: String,
        val requestTimestamp: String,
        val signature: String
    )

    fun sign(
        secret: ByteArray,
        deviceId: String,
        bootNonce: String,
        method: String,
        canonicalPath: String,
        body: ByteArray,
        requestNonce: String = UUID.randomUUID().toString(),
        requestTimestampSeconds: Long = System.currentTimeMillis() / 1000
    ): SignedHeaders {

        val normalizedDeviceId = deviceId.lowercase()
        val requestTimestamp = requestTimestampSeconds.toString()

        val bodyHashHex = bytesToHex(
            MessageDigest.getInstance("SHA-256").digest(body)
        )

        val canonicalString = listOf(
            "JETSONHTTP2",
            normalizedDeviceId,
            bootNonce,
            requestNonce,
            requestTimestamp,
            method.uppercase(),
            canonicalPath,
            bodyHashHex
        ).joinToString("\n")

        val signature = hmacHex(secret, canonicalString)

        return SignedHeaders(normalizedDeviceId, requestNonce, requestTimestamp, signature)
    }

    fun verifyResponse(
        secret: ByteArray,
        deviceId: String,
        bootNonce: String,
        requestNonce: String,
        requestTimestamp: String,
        statusCode: Int,
        body: ByteArray,
        receivedSignature: String
    ): Boolean {
        if (receivedSignature.length != 64 ||
            receivedSignature.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }
        ) {
            return false
        }
        val canonicalString = listOf(
            "JETSONHTTPRESP1",
            deviceId.lowercase(),
            bootNonce,
            requestNonce,
            requestTimestamp,
            statusCode.toString(),
            bytesToHex(MessageDigest.getInstance("SHA-256").digest(body))
        ).joinToString("\n")
        val expected = hmacHex(secret, canonicalString)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            receivedSignature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }

    fun verifyHello(
        secret: ByteArray,
        apiVersion: Int,
        deviceId: String,
        deviceName: String,
        bootNonce: String,
        serverTimeEpochSeconds: Long,
        authScheme: String,
        tlsCertificateSha256: String,
        receivedProof: String
    ): Boolean {
        if (receivedProof.length != 64 ||
            receivedProof.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }
        ) {
            return false
        }
        val canonicalString = listOf(
            "JETSONHELLO1",
            apiVersion.toString(),
            deviceId.lowercase(),
            deviceName,
            bootNonce,
            serverTimeEpochSeconds.toString(),
            authScheme,
            tlsCertificateSha256.lowercase()
        ).joinToString("\n")
        val expected = hmacHex(secret, canonicalString)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            receivedProof.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }

    private fun hmacHex(secret: ByteArray, canonicalString: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return bytesToHex(mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8)))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
