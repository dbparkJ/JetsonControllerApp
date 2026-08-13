package com.example.jetsoncontroller.ui.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryTest {
    @Test
    fun authenticationSyncFailure_requiresQrRegistration() {
        assertTrue(
            requiresQrRegistration(
                "Jetson과 인증 정보를 동기화하지 못했습니다. 저장된 장비를 QR로 다시 등록해 주세요."
            )
        )
    }

    @Test
    fun networkFailure_canRetryDiscovery() {
        assertFalse(requiresQrRegistration("Jetson API에 연결하지 못했습니다."))
    }

    @Test
    fun lastSeen_usesReadableRelativeTime() {
        assertTrue(formatLastSeen(1_000L, 31_000L).contains("방금"))
        assertTrue(formatLastSeen(1_000L, 181_000L).contains("3분 전"))
        assertTrue(formatLastSeen(1_000L, 7_201_000L).contains("2시간 전"))
    }
}
