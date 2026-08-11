package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.WifiProvisionRequest

object WifiProvisionCodec {

    private const val VERSION: Byte = 0x01
    private const val FLAG_HIDDEN = 0x01

    fun encode(request: WifiProvisionRequest): ByteArray {
        val ssid = request.ssid.trim().toByteArray(Charsets.UTF_8)
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
}
