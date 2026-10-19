package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.ui.connectionNoticeFor
import com.example.jetsoncontroller.ui.devices.formatSeenAt
import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.userFacingConnectionMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryTest {
    @Test
    fun authenticationSyncFailure_offersQrRegistrationInTheNewShell() {
        assertTrue(
            connectionNoticeFor(Routes.DEVICE_DETAIL, false, null, registrationRequired = true)
                ?.message?.contains("QR") == true
        )
    }

    @Test
    fun networkFailure_canRetryDiscovery() {
        assertFalse(connectionNoticeFor(Routes.DEVICE_DETAIL, false, null, registrationRequired = false)
            ?.message?.contains("QR") == true)
    }

    @Test
    fun lastSeen_usesReadableRelativeTime() {
        assertTrue(formatSeenAt(1_000L, 31_000L).contains("방금"))
        assertTrue(formatSeenAt(1_000L, 181_000L).contains("3분 전"))
        assertTrue(formatSeenAt(1_000L, 7_201_000L).contains("2시간 전"))
    }


    @Test
    fun rawNetworkFailure_isNotShownToOperators() {
        val raw = "java.net.ConnectException: failed to connect to /111.111.111.110:44181"
        val message = requireNotNull(userFacingConnectionMessage(raw, developerModeEnabled = false))

        assertFalse(message.contains("111.111.111.110"))
        assertFalse(message.contains("ConnectException"))
        assertTrue(message.contains("다시 시도"))
    }
}
