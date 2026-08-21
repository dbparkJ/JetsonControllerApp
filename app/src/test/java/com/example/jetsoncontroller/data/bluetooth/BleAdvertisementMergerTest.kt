package com.example.jetsoncontroller.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementMergerTest {

    @Test
    fun uuidOnlyAdvertisementIsVisibleAndLaterNameIsMerged() {
        val uuidOnly =
            BleAdvertisementMerger.merge(
                previous = null,
                observedName = null,
                observedServiceUuids =
                    listOf(JetsonGattSpec.SERVICE_UUID.toString())
            )

        assertTrue(
            BleAdvertisementMerger.isJetsonDevice(uuidOnly)
        )
        assertEquals(
            "MMS 장비 (7F3A)",
            BleAdvertisementMerger.displayName(
                metadata = uuidOnly,
                address = "AA:BB:CC:DD:7F:3A"
            )
        )

        val withName =
            BleAdvertisementMerger.merge(
                previous = uuidOnly,
                observedName = "  MMS-D137  ",
                observedServiceUuids = emptyList()
            )

        assertEquals("MMS-D137", withName.name)
        assertTrue(
            withName.advertisedServiceUuids.contains(
                JetsonGattSpec.SERVICE_UUID.toString()
            )
        )
        assertEquals(
            "MMS-D137",
            BleAdvertisementMerger.displayName(
                metadata = withName,
                address = "AA:BB:CC:DD:7F:3A"
            )
        )
    }

    @Test
    fun namedAdvertisementKeepsNameWhenUuidArrivesLater() {
        val named =
            BleAdvertisementMerger.merge(
                previous = null,
                observedName = "MMS-D137",
                observedServiceUuids = emptyList()
            )

        val merged =
            BleAdvertisementMerger.merge(
                previous = named,
                observedName = null,
                observedServiceUuids =
                    listOf(
                        JetsonGattSpec.SERVICE_UUID
                            .toString()
                            .uppercase()
                    )
            )

        assertEquals("MMS-D137", merged.name)
        assertTrue(
            BleAdvertisementMerger.isJetsonDevice(merged)
        )
        assertEquals(1, merged.advertisedServiceUuids.size)
    }

    @Test
    fun anonymousNonJetsonAdvertisementStaysHidden() {
        val metadata =
            BleAdvertisementMerger.merge(
                previous = null,
                observedName = "unknown",
                observedServiceUuids =
                    listOf("0000180f-0000-1000-8000-00805f9b34fb")
            )

        assertNull(metadata.name)
        assertNull(
            BleAdvertisementMerger.displayName(
                metadata = metadata,
                address = "AA:BB:CC:DD:EE:FF"
            )
        )
    }

    @Test
    fun scanDebugSummaryCountsUniqueMetadataWithoutIdentifiers() {
        val observations =
            listOf(
                BleAdvertisementMetadata(
                    name = "MMS-D137",
                    advertisedServiceUuids =
                        setOf(JetsonGattSpec.SERVICE_UUID.toString())
                ),
                BleAdvertisementMetadata(
                    name = "Headphones"
                ),
                BleAdvertisementMetadata(
                    advertisedServiceUuids =
                        setOf("0000180f-0000-1000-8000-00805f9b34fb")
                )
            )

        assertEquals(
            BleScanDebugSummary(
                totalResults = 42,
                uniqueDevices = 3,
                namedDevices = 2,
                serviceAdvertisingDevices = 2,
                jetsonCandidates = 1
            ),
            BleScanDebugInfo.summarize(
                totalResults = 42,
                observations = observations
            )
        )
    }

    @Test
    fun scanDebugNameOnlyRevealsSanitizedJetsonNames() {
        assertEquals(
            "MMS-D137",
            BleScanDebugInfo.safeCandidateName("MMS-D137")
        )
        assertEquals(
            "MMS-<redacted>",
            BleScanDebugInfo.safeCandidateName("MMS-D137/private")
        )
        assertEquals(
            "<present>",
            BleScanDebugInfo.safeCandidateName("Personal phone")
        )
        assertEquals(
            "<none>",
            BleScanDebugInfo.safeCandidateName(null)
        )
        assertFalse(
            BleScanDebugInfo
                .safeCandidateName("MMS-D137/private")
                .contains("private")
        )
    }
}
