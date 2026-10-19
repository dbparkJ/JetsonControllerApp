package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.model.PairingInfo
import com.example.jetsoncontroller.model.registeredDeviceDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingConnectionPolicyTest {

    private val info = PairingInfo(
        version = 1,
        deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d9d137",
        bootstrapSecretHex = "00".repeat(32)
    )

    @Test
    fun `QR device ID defines the name of an MMS-style device`() {
        listOf(
            "MMS-9D137",
            "MMS 장비 (7F3A)",
            "MMS 장치 (D137)",
            ""
        ).forEach { advertisedName ->
            assertEquals(
                "MMS-D137",
                pairingDisplayName(
                    pairingInfo = info,
                    advertisedDisplayName = advertisedName
                )
            )
        }
    }

    @Test
    fun `a device the owner renamed keeps its own name`() {
        assertEquals(
            "geo X-1",
            pairingDisplayName(pairingInfo = info, advertisedDisplayName = " geo X-1 ")
        )
        assertEquals("geo X-1", registeredDeviceDisplayName(info.deviceId, "geo X-1"))
        assertEquals("MMS-D137", registeredDeviceDisplayName(info.deviceId, null))
        assertEquals("MMS-D137", registeredDeviceDisplayName(info.deviceId, "mms-d137"))
        assertEquals(64, registeredDeviceDisplayName(info.deviceId, "가".repeat(80)).length)
    }
}
