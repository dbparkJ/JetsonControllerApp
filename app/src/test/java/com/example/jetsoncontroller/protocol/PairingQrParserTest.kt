package com.example.jetsoncontroller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingQrParserTest {

    @Test
    fun parse_validQr_returnsNormalizedPairingInfo() {
        val result = PairingQrParser.parse(
            "  JETSONCTL://PAIR?v=1&id=550E8400-E29B-41D4-A716-446655440000&" +
                "key=ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789  "
        )

        assertEquals(1, result.version)
        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            result.deviceId
        )
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            result.bootstrapSecretHex
        )
    }

    @Test
    fun parse_wrongScheme_isRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PairingQrParser.parse(
                "https://pair?v=1&id=550e8400-e29b-41d4-a716-446655440000&" +
                    "key=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
            )
        }

        assertEquals("지원하지 않는 QR 코드입니다.", error.message)
    }

    @Test
    fun parse_invalidDeviceId_returnsActionableError() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PairingQrParser.parse(
                "jetsonctl://pair?v=1&id=not-a-uuid&" +
                    "key=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
            )
        }

        assertEquals("장비 ID 형식이 올바르지 않습니다.", error.message)
    }

    @Test
    fun parse_shortSecret_isRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PairingQrParser.parse(
                "jetsonctl://pair?v=1&id=550e8400-e29b-41d4-a716-446655440000&key=abcd"
            )
        }

        assertEquals("장비 인증 정보 형식이 올바르지 않습니다.", error.message)
    }
}
