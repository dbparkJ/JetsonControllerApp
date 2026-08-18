package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.model.PairingInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingConnectionPolicyTest {

    @Test
    fun `QR device ID defines the canonical pairing display name`() {
        val info = PairingInfo(
            version = 1,
            deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d9d137",
            bootstrapSecretHex = "00".repeat(32)
        )

        listOf(
            "MMS-9D137",
            "MMS 장비 (7F3A)"
        ).forEach { advertisedName ->
            assertEquals(
                "MMS-D137",
                canonicalPairingDisplayName(
                    pairingInfo = info,
                    advertisedDisplayName = advertisedName
                )
            )
        }
    }
}
