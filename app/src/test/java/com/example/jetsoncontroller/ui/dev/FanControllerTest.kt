package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.FanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class FanControllerTest {
    @Test fun duplicateRefreshDoesNotReplayAndManualControlUsesTheSupportedMinimum() = runTest {
        val repo = mock(JetsonRepository::class.java)
        val device = MutableStateFlow<String?>("a")
        val transport = MutableStateFlow<TransportState>(TransportState.Connected(type = TransportType.LAN, deviceId = "a", deviceName = "A"))
        `when`(repo.selectedDeviceId).thenReturn(device)
        `when`(repo.transportState).thenReturn(transport)
        val fan = FanStatus(true, "AUTO", minimumManualPercent = 60, autoAvailable = true)
        `when`(repo.getFanStatus()).thenReturn(Result.success(fan))
        `when`(repo.setFan("MANUAL", 60)).thenReturn(Result.success(fan.copy(mode = "MANUAL", percent = 60)))
        val controller = DevFanController(repo, backgroundScope)
        runCurrent()
        controller.refresh()
        controller.refresh()
        runCurrent()
        verify(repo, times(1)).getFanStatus()
        controller.manual(20)
        runCurrent()
        verify(repo).setFan("MANUAL", 60)
        assertEquals(60, controller.state.value.status?.percent)
        assertFalse(controller.state.value.loading)
    }

    @Test fun switchingDeviceClearsPreviousFanStateAndBlocksManualControlUntilRefreshed() = runTest {
        val repo = mock(JetsonRepository::class.java)
        val device = MutableStateFlow<String?>("a")
        val transport = MutableStateFlow<TransportState>(TransportState.Connected(type = TransportType.LAN, deviceId = "a", deviceName = "A"))
        `when`(repo.selectedDeviceId).thenReturn(device)
        `when`(repo.transportState).thenReturn(transport)
        `when`(repo.getFanStatus()).thenReturn(Result.success(FanStatus(true, "AUTO")))
        val controller = DevFanController(repo, backgroundScope)
        runCurrent()
        controller.refresh()
        runCurrent()
        assertNotNull(controller.state.value.status)
        device.value = "b"
        transport.value = TransportState.Connected(type = TransportType.LAN, deviceId = "b", deviceName = "B")
        runCurrent()
        assertNull(controller.state.value.status)
        controller.manual(90)
        runCurrent()
        verify(repo, never()).setFan("MANUAL", 90)
    }

    @Test fun basicConnectionDoesNotRequestFanControl() = runTest {
        val repo = mock(JetsonRepository::class.java)
        `when`(repo.selectedDeviceId).thenReturn(MutableStateFlow<String?>("a"))
        `when`(repo.transportState).thenReturn(MutableStateFlow<TransportState>(TransportState.Connected(type = TransportType.BLE, deviceId = "a", deviceName = "A")))
        val controller = DevFanController(repo, backgroundScope)
        controller.refresh()
        runCurrent()
        verify(repo, never()).getFanStatus()
        assertFalse(controller.state.value.loading)
    }
}
