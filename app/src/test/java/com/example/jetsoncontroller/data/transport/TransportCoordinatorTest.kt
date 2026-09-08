package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand
import org.junit.Assert.*
import org.junit.Test

class TransportCoordinatorTest {
    @Test
    fun `late status and command responses cannot affect another device or reconnected session`() {
        val coordinator = TransportCoordinator()
        val first = FakeTransport(TransportType.LAN)
        coordinator.setActiveTransport(first, deviceId = "A")
        val status = coordinator.beginRequest("status")!!
        val command = coordinator.beginRequest("command")!!
        coordinator.disconnect()
        coordinator.setActiveTransport(first, deviceId = "A")
        var applied = false
        assertFalse(coordinator.applyResponse(status) { applied = true })
        coordinator.setActiveTransport(FakeTransport(TransportType.LAN), deviceId = "B")
        assertFalse(coordinator.applyResponse(command) { applied = true })
        assertFalse(applied)
        assertTrue(coordinator.applyResponse(coordinator.beginRequest("status")!!) { applied = true })
        assertTrue(applied)
    }

    @Test
    fun `older success or failure cannot replace a newer heartbeat result`() {
        val coordinator = TransportCoordinator()
        coordinator.setActiveTransport(FakeTransport(TransportType.LAN), deviceId = "A")
        val old = coordinator.beginRequest("status")!!
        val new = coordinator.beginRequest("status")!!
        var failures = 2
        assertTrue(coordinator.applyResponse(new) { failures = 0 })
        assertFalse(coordinator.applyResponse(old) { failures += 1 })
        assertEquals(0, failures)
    }

    @Test
    fun `BLE ready during Direct verification leaves the IP attempt alive`() {
        val coordinator = TransportCoordinator()
        val attempt = coordinator.nextConnectionAttempt()
        coordinator.setActiveTransport(FakeTransport(TransportType.BLE), deviceId = "A")
        assertTrue(coordinator.connectionAttemptIsCurrent(attempt))
        val bleRequest = coordinator.beginRequest("status")!!
        coordinator.setActiveTransport(FakeTransport(TransportType.WIFI_DIRECT), deviceId = "A")
        assertFalse(coordinator.isCurrent(bleRequest))
        coordinator.nextConnectionAttempt()
        assertFalse(coordinator.connectionAttemptIsCurrent(attempt))
    }

    private class FakeTransport(override val type: TransportType) : ControlTransport {
        override val capabilities = TransportCapabilities(true, true, true, true, true)
        override suspend fun ping() = true
        override suspend fun getStatus() = Result.success(JetsonStatus())
        override suspend fun sendCommand(command: JetsonCommand, payload: ByteArray) = Result.success(Unit)
        override suspend fun disconnect() = Unit
    }
}
