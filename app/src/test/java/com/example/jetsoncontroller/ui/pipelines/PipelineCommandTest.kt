package com.example.jetsoncontroller.ui.pipelines

import androidx.lifecycle.ViewModelStore
import com.example.jetsoncontroller.data.network.JetsonApiException
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class PipelineCommandTest {
    @Test fun repeatedClickAndRefreshDoNotReplayCommandAndDraftSurvivesReentry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repo = mock(JetsonRepository::class.java)
            val device = MutableStateFlow<String?>("a")
            val transport = MutableStateFlow<TransportState>(TransportState.Connected(type = TransportType.LAN, deviceId = "a", deviceName = "Device A"))
            `when`(repo.selectedDeviceId).thenReturn(device)
            `when`(repo.transportState).thenReturn(transport)
            `when`(repo.mobileRtkRelayState).thenReturn(MutableStateFlow(MobileRtkRelayState()))
            val task = ManagedPipeline("task", "Task", entrypoint = "main.py", config = "config.yaml", virtualenv = "venv", state = PipelineState.STOPPED)
            `when`(repo.getPipelines()).thenReturn(Result.success(listOf(task)))
            `when`(repo.getWorkspaceRoots()).thenReturn(Result.success(emptyList()))
            `when`(repo.synchronizeSystemTime(anyLong())).thenReturn(Result.success(SystemTimeStatus(true, 1000)))
            `when`(repo.controlPipeline("task", "start")).thenReturn(Result.success(task.copy(state = PipelineState.STARTING)))
            val vm = PipelineViewModel(repo)
            store.put("pipeline", vm)
            runCurrent()
            vm.control(task, "start")
            vm.control(task, "start")
            vm.refresh()
            runCurrent()
            verify(repo, times(1)).controlPipeline("task", "start")
            assertEquals("start", vm.uiState.value.pendingActions["task"])
            assertEquals(PipelineState.STARTING, vm.uiState.value.pipelines.single().state)
            vm.setLabel("preserved draft")
            vm.beginCreate()
            assertEquals("preserved draft", vm.uiState.value.draft.label)
            device.value = "b"
            transport.value = TransportState.Connected(type = TransportType.LAN, deviceId = "b", deviceName = "Device B")
            runCurrent()
            assertEquals("", vm.uiState.value.draft.label)
            vm.setLabel("B draft")
            device.value = "a"
            transport.value = TransportState.Connected(type = TransportType.LAN, deviceId = "a", deviceName = "Device A")
            runCurrent()
            assertEquals("preserved draft", vm.uiState.value.draft.label)
            verify(repo, times(1)).controlPipeline("task", "start")
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun exactStopPassesRunIdentityAndReportsOldBackendWithoutRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repo = mock(JetsonRepository::class.java)
            val device = MutableStateFlow<String?>("a")
            val transport = MutableStateFlow<TransportState>(
                TransportState.Connected(
                    type = TransportType.LAN,
                    deviceId = "a",
                    deviceName = "Device A"
                )
            )
            `when`(repo.selectedDeviceId).thenReturn(device)
            `when`(repo.transportState).thenReturn(transport)
            `when`(repo.mobileRtkRelayState).thenReturn(MutableStateFlow(MobileRtkRelayState()))
            val task = ManagedPipeline(
                "task",
                "Task",
                entrypoint = "main.py",
                config = "config.yaml",
                virtualenv = "venv",
                state = PipelineState.RUNNING
            )
            `when`(repo.getPipelines()).thenReturn(Result.success(listOf(task)))
            `when`(repo.getWorkspaceRoots()).thenReturn(Result.success(emptyList()))
            `when`(repo.controlPipeline("task", "stop", "capture/run-0001")).thenReturn(
                Result.failure(
                    JetsonApiException(
                        statusCode = 404,
                        message = "Unknown pipeline action"
                    )
                )
            )
            val vm = PipelineViewModel(repo)
            store.put("pipeline", vm)
            runCurrent()

            vm.control(task, "stop", expectedRunId = "capture/run-0001")
            runCurrent()

            verify(repo, times(1)).controlPipeline("task", "stop", "capture/run-0001")
            assertEquals(
                "장비 소프트웨어 업데이트가 필요합니다. 수집은 종료되지 않았습니다.",
                vm.uiState.value.error
            )
            assertEquals("unknown", vm.uiState.value.pendingActions["task"])
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
