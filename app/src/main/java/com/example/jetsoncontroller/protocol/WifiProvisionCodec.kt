package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.WifiProvisionRequest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WifiProvisionCodec {

    private const val VERSION: Byte = 0x01
    private const val FLAG_HIDDEN = 0x01
    private const val ENCRYPTED_VERSION: Byte = 0x02
    private const val NONCE_SIZE = 12
    private val WIFI_AAD_CONTEXT = "JETSONWIFI2|".toByteArray(Charsets.UTF_8)

    fun encode(request: WifiProvisionRequest): ByteArray {
        val ssid = request.ssid.toByteArray(Charsets.UTF_8)
        val password = request.password.toByteArray(Charsets.UTF_8)

        require(ssid.isNotEmpty()) {
            "SSID를 입력해 주세요."
        }
        require(ssid.size <= 32) {
            "SSID는 UTF-8 기준 32바이트 이하여야 합니다."
        }
        require(password.isEmpty() || password.size in 8..63) {
            "비밀번호는 비워 두거나 UTF-8 기준 8~63바이트여야 합니다."
        }

        val flags = if (request.hidden) FLAG_HIDDEN else 0

        return byteArrayOf(
            VERSION,
            flags.toByte(),
            ssid.size.toByte(),
            password.size.toByte()
        ) + ssid + password
    }

    fun encodeEncrypted(
        request: WifiProvisionRequest,
        sessionKey: ByteArray,
        deviceId: String,
        nonce: ByteArray = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
    ): ByteArray {
        require(sessionKey.size == 32) { "BLE session key must contain 32 bytes." }
        require(nonce.size == NONCE_SIZE) { "BLE Wi-Fi nonce must contain 12 bytes." }
        val plaintext = encode(request)
        val deviceIdBytes = UuidCodec.toBytes(UUID.fromString(deviceId))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(sessionKey, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(WIFI_AAD_CONTEXT + deviceIdBytes)
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(ENCRYPTED_VERSION) + nonce + ciphertext
    }
}
