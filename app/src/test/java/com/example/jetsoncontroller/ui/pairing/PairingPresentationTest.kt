package com.example.jetsoncontroller.ui.pairing

import com.example.jetsoncontroller.model.BlePairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPresentationTest {
    private val deviceId = "00000000-0000-0000-0000-123456784de0"
    private val secret = "0123456789abcdef".repeat(4)
    private val code = "jetsonctl://pair?v=1&id=$deviceId&key=$secret"

    @Test
    fun `confirmation exposes only the device suffix and never the registration credential`() {
        val info = requireNotNull(parsePairingCode(code))
        val shown = maskedPairingCode(info)
        assertEquals("등록 코드 •••• 4DE0", shown)
        assertFalse(shown.contains(secret))
        assertFalse(shown.contains(deviceId))
        assertFalse(shown.contains(code))
        assertEquals("등록 코드 미확인", maskedPairingCode(null))
    }

    @Test
    fun `malformed codes cannot surface parser exceptions containing the input`() {
        listOf("jetsonctl://pair?key=%zz$secret", "\n$secret", code.replace("v=1", "v=99"),
            code.replace(secret, "not-a-key")).forEach { invalid ->
            assertNull(parsePairingCode(invalid))
            assertFalse(INVALID_PAIRING_CODE.contains(secret))
            assertFalse(pairingFailureText(invalid).contains(secret))
        }
    }

    @Test
    fun `retry causes are mapped instead of showing credentials or technical codes`() {
        listOf(31, 33).forEach { sdk ->
            assertEquals("가까운 장치를 찾으려면 블루투스 권한이 필요합니다.", pairingFailureText("권한 $secret", sdk))
        }
        assertEquals("장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요.", pairingFailureText("인증 실패 $code"))
        assertEquals("장치를 찾았지만 연결하지 못했습니다. 장치 가까이에서 다시 시도하세요.", pairingFailureText("GATT_133 $code"))
        assertFalse(resolvePairingError(null, true, BlePairingState.Error("인증 $code"))!!.contains(secret))
    }

    @Test
    fun `Android 11 explains location permission and the disabled location service separately`() {
        assertEquals("가까운 장치를 찾으려면 위치 권한이 필요합니다. 위치 서비스도 켜세요.",
            pairingFailureText("주변 기기 검색 권한 $secret", 30))
        assertEquals("가까운 장치를 찾으려면 위치 서비스를 켜세요.",
            pairingFailureText("위치 서비스가 꺼져 있습니다. $secret", 30))
    }

    @Test
    fun `authentication and status synchronization remain distinct progress steps`() {
        assertEquals("인증하는 중입니다.", pairingProgressText(PairingPhase.AUTHENTICATING))
        assertEquals("장치 상태를 확인하는 중입니다.", pairingProgressText(PairingPhase.ENABLING_STATUS))
        assertTrue(PairingPhase.SEARCHING.isPairingInProgress())
        assertFalse(PairingPhase.QR_SCANNED.isPairingInProgress())
        assertFalse(PairingPhase.READY.isPairingInProgress())
    }

    @Test
    fun `discovery failure and failure after discovery have distinct safe copy`() {
        val notFound = "장치를 찾지 못했습니다. 장치 전원과 거리를 확인하세요."
        val connectionFailed = "장치를 찾았지만 연결하지 못했습니다. 장치 가까이에서 다시 시도하세요."
        assertEquals(notFound, pairingFailureText("MMS 장치를 찾지 못했습니다. $secret", 30))
        assertEquals(notFound, pairingFailureText(null, 30))
        listOf("Service discovery failed: 133", "Jetson Control Service not found",
            "Device ID characteristic not found", "Auth Challenge characteristic not found", "Read failed: 8", secret).forEach {
            assertEquals(connectionFailed, pairingFailureText(it, 30, deviceFound = true))
            assertEquals(connectionFailed, resolvePairingError(null, true, BlePairingState.Error(it)))
        }
        // The ViewModel and screen both map copy: the second mapping must preserve it.
        assertEquals(connectionFailed, pairingFailureText(connectionFailed, 30))
        assertEquals("장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요.",
            pairingFailureText("Authentication failed $secret", 30, deviceFound = true))
        assertEquals("가까운 장치를 찾으려면 위치 권한이 필요합니다. 위치 서비스도 켜세요.",
            pairingFailureText("권한 $secret", 30, deviceFound = true))
    }

    @Test
    fun `retries show attempt counts and remain an active connecting phase`() {
        assertEquals("장치에 연결하는 중입니다.", pairingProgressText(PairingPhase.CONNECTING))
        listOf(2, 3).forEach { attempt ->
            val retry = BlePairingState.Retrying(attempt, 3)
            assertEquals("다시 연결하는 중 ($attempt/3)", pairingProgressText(PairingPhase.CONNECTING, attempt))
            assertEquals(PairingPhase.CONNECTING, resolvePairingPhase(PairingPhase.SEARCHING, true, retry))
            assertTrue(retry.isActivePairing())
        }
    }
}
