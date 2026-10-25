package com.example.jetsoncontroller.ui.devices

import androidx.lifecycle.ViewModelStore
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.RegisteredDevice
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListLoadingTest {
    @Test fun `loading lasts until a stored snapshot arrives and scanning does not restart it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = mock(JetsonRepository::class.java)
            val stored = MutableStateFlow<List<RegisteredDevice>?>(null)
            val scanning = MutableStateFlow<BleScanState>(BleScanState.Idle)
            `when`(repository.registeredDeviceSnapshot).thenReturn(stored)
            `when`(repository.devices).thenReturn(MutableStateFlow<List<JetsonDevice>>(emptyList()))
            `when`(repository.scanState).thenReturn(scanning)
            `when`(repository.connectionState).thenReturn(MutableStateFlow<ConnectionState>(ConnectionState.Disconnected))
            val viewModel = DeviceListViewModel(repository)
            store.put("devices", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)
            stored.value = emptyList()
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.registeredDevices.isEmpty())
            val device = RegisteredDevice("device", "GEO-X")
            stored.value = listOf(device)
            scanning.value = BleScanState.Scanning
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.isScanning)
            assertEquals(listOf(device), viewModel.uiState.value.registeredDevices)
        } finally {
            backgroundScope.coroutineContext.cancelChildren()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
