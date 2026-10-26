package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import org.junit.Assert.*
import org.junit.Test

class CameraPreviewReceivePolicyTest {
    private val safe = CameraPreviewEnvironment(unmetered = true, powerSave = false, localLanRoute = true)

    @Test fun onlyNearbyUnmeteredAwakeConnectionsOverlap() {
        for (type in TransportType.entries) {
            val connected = TransportState.Connected(type)
            assertEquals(type != TransportType.BLE, cameraPreviewCanOverlap(connected, safe))
            assertFalse(cameraPreviewCanOverlap(connected, safe.copy(unmetered = false)))
            assertFalse(cameraPreviewCanOverlap(connected, safe.copy(powerSave = true)))
            assertFalse(cameraPreviewCanOverlap(connected, safe.copy(thermalLimited = true)))
            assertFalse(cameraPreviewCanOverlap(connected, CameraPreviewEnvironment()))
        }
        assertFalse(cameraPreviewCanOverlap(TransportState.Disconnected, safe))
        assertFalse(cameraPreviewCanOverlap(TransportState.Connecting(TransportType.LAN), safe))
        assertFalse(cameraPreviewCanOverlap(TransportState.Error(TransportType.LAN, "offline"), safe))
    }

    @Test fun lanLabelAloneCannotEnableARemoteOrRelayedEndpoint() {
        assertFalse(cameraPreviewCanOverlap(TransportState.Connected(TransportType.LAN), safe.copy(localLanRoute = false)))
        assertTrue(cameraPreviewCanOverlap(TransportState.Connected(TransportType.WIFI_DIRECT), safe.copy(localLanRoute = false)))
    }
}
