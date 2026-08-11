package com.example.jetsoncontroller.protocol

import android.net.Uri
import com.example.jetsoncontroller.model.PairingInfo
import java.util.UUID

object PairingQrParser {

    fun parse(
        rawValue: String
    ): PairingInfo {

        val uri =
            Uri.parse(
                rawValue.trim()
            )

        require(
            uri.scheme == "jetsonctl"
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        require(
            uri.host == "pair"
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        val version =
            uri.getQueryParameter("v")
                ?.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "QR 버전 정보가 없습니다."
                )

        require(version == 1) {
            "지원하지 않는 QR 버전입니다."
        }

        val deviceId =
            uri.getQueryParameter("id")
                ?: throw IllegalArgumentException(
                    "장비 ID가 없습니다."
                )

        val normalizedId =
            UUID.fromString(deviceId)
                .toString()
                .lowercase()

        val secret =
            uri.getQueryParameter("key")
                ?: throw IllegalArgumentException(
                    "장비 인증 정보가 없습니다."
                )

        require(
            secret.matches(
                Regex("^[0-9a-fA-F]{64}$")
            )
        ) {
            "장비 인증 정보 형식이 올바르지 않습니다."
        }

        return PairingInfo(
            version = version,
            deviceId = normalizedId,
            bootstrapSecretHex =
                secret.lowercase()
        )
    }
}
