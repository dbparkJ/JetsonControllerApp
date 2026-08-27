package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.data.transport.TransportType
import org.junit.Assert.assertEquals
import org.junit.Test

class UserConnectionStageTest {
    @Test
    fun directPhoneTransports_usePlainPhoneLabel() {
        listOf(TransportType.BLE, TransportType.WIFI_DIRECT).forEach { transport ->
            assertEquals(
                UserConnectionStage.PHONE_CONNECTED,
                userConnectionStage(online = true, transportType = transport)
            )
        }
    }

    @Test
    fun localNetworkTransport_isShownAsWifi() {
        assertEquals(
            UserConnectionStage.WIFI_CONNECTED,
            userConnectionStage(online = true, transportType = TransportType.LAN)
        )
    }

    @Test
    fun offlineAlwaysWinsOverStaleTransport() {
        assertEquals(
            UserConnectionStage.OFFLINE,
            userConnectionStage(online = false, transportType = TransportType.LAN)
        )
    }
}
