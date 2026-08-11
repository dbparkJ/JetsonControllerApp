package com.example.jetsoncontroller.data.network

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HttpAuthSigner {

    data class SignedHeaders(
        val deviceId: String,
        val requestNonce: String,
        val signature: String
    )

    fun sign(
        secret: ByteArray,
        deviceId: String,
        bootNonce: String,
        method: String,
        canonicalPath: String,
        body: ByteArray
    ): SignedHeaders {

        val requestNonce = java.util.UUID.randomUUID().toString().take(16)
        
        val bodyHashHex = if (body.isNotEmpty()) {
            bytesToHex(MessageDigest.getInstance("SHA-256").digest(body))
        } else {
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // Empty body SHA256
        }

        val canonicalString = listOf(
            "JETSONHTTP1",
            deviceId,
            bootNonce,
            requestNonce,
            method.uppercase(),
            canonicalPath,
            bodyHashHex
        ).joinToString("\n")

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val signature = bytesToHex(mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8)))

        return SignedHeaders(deviceId, requestNonce, signature)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
