package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.PairingInfo
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object PairingQrParser {

    fun parse(
        rawValue: String
    ): PairingInfo {

        val uri = try {
            URI(rawValue.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "QR 코드 형식이 올바르지 않습니다."
            )
        }

        require(
            uri.scheme.equals(
                "jetsonctl",
                ignoreCase = true
            )
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        require(
            uri.host.equals(
                "pair",
                ignoreCase = true
            )
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        val parameters =
            parseQuery(uri.rawQuery)

        val version =
            parameters["v"]
                ?.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "QR 버전 정보가 없습니다."
                )

        require(version == 1) {
            "지원하지 않는 QR 버전입니다."
        }

        val deviceId =
            parameters["id"]
                ?: throw IllegalArgumentException(
                    "장비 ID가 없습니다."
                )

        val normalizedId = try {
            UUID.fromString(deviceId)
                .toString()
                .lowercase()
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(
                "장비 ID 형식이 올바르지 않습니다."
            )
        }

        val secret =
            parameters["key"]
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

    private fun parseQuery(
        rawQuery: String?
    ): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }

        return rawQuery
            .split("&")
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) {
                    return@mapNotNull null
                }

                val key = decode(entry.substring(0, separator))
                val value = decode(entry.substring(separator + 1))
                key to value
            }
            .toMap()
    }

    private fun decode(
        value: String
    ): String {
        return try {
            URLDecoder.decode(
                value,
                StandardCharsets.UTF_8.name()
            )
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(
                "QR 코드 형식이 올바르지 않습니다."
            )
        }
    }
}
