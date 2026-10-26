package com.example.jetsoncontroller.data.bluetooth

import com.example.jetsoncontroller.model.bleConnectionProgressText
import com.example.jetsoncontroller.model.bleConnectionFailureText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleConnectionPolicyTest {
    @Test
    fun `only Android 11 and older wait for scan handoff`() {
        listOf(23, 29, 30).forEach { assertEquals(500L, BleConnectionPolicy.initialDelayMillis(it)) }
        listOf(31, 32, 33, 35, 36).forEach { assertEquals(0L, BleConnectionPolicy.initialDelayMillis(it)) }
    }

    @Test
    fun `transient link failures get two fresh attempts after closing GATT`() {
        assertEquals(3, BleConnectionPolicy.MAX_ATTEMPTS)
        listOf(133, 8, 62, 147).forEach { status ->
            assertEquals(500L, BleConnectionPolicy.retryDelayMillis(status, 1, true))
            assertEquals(500L, BleConnectionPolicy.retryDelayMillis(status, 2, true))
            listOf(0, 3, 4).forEach { assertNull(BleConnectionPolicy.retryDelayMillis(status, it, true)) }
        }
    }

    @Test
    fun `success authentication rejection and unknown errors are terminal`() {
        listOf(0, 1, 5, 6, 13, 15, 19, 22, 129, 137, 143, 257).forEach { status ->
            (1..3).forEach { attempt -> assertNull(BleConnectionPolicy.retryDelayMillis(status, attempt, true)) }
        }
    }

    @Test
    fun `ATT errors and failures after connection never retry including status 8`() {
        listOf(133, 8, 62, 147).forEach { status ->
            assertNull(BleConnectionPolicy.retryDelayMillis(status, 1, false))
        }
    }

    @Test
    fun `delays add at most one and a half seconds within twenty second connection budget`() {
        val wait = BleConnectionPolicy.initialDelayMillis(30) +
            (1..2).sumOf { BleConnectionPolicy.retryDelayMillis(133, it, true)!! }
        assertEquals(1_500L, wait)
        assertEquals(20_000L, BleConnectionPolicy.CONNECTION_BUDGET_MILLIS)
    }

    @Test
    fun `reconnect and registration share progress copy`() {
        assertEquals("장치에 연결하는 중입니다.", bleConnectionProgressText(1))
        assertEquals("다시 연결하는 중 (2/3)", bleConnectionProgressText(2))
        assertEquals("다시 연결하는 중 (3/3)", bleConnectionProgressText(3))
    }

    @Test
    fun `reconnect errors use safe Korean copy without platform diagnostics`() {
        assertEquals("장치를 찾았지만 연결하지 못했습니다. 장치 가까이에서 다시 시도하세요.",
            bleConnectionFailureText("Service discovery failed: 133 private-identifier"))
        assertEquals("장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요.",
            bleConnectionFailureText("Authentication failed: private-credential"))
        assertEquals("블루투스 연결 권한이 필요합니다.", bleConnectionFailureText("권한 부족"))
    }
}
