package com.example.jetsoncontroller.ui.startup

import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore.StoredCredential
import com.example.jetsoncontroller.model.RegisteredDevice
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppRestoreStateTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `first run is possible only after a successful empty restore`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val result = CompletableDeferred<List<RegisteredDevice>>()
        val model = AppRestoreViewModel { result.await() }
        assertEquals(AppRestoreState.Loading, model.state.value)
        runCurrent()
        assertEquals(AppRestoreState.Loading, model.state.value)
        result.complete(emptyList())
        runCurrent()
        assertEquals(AppRestoreState.Restored(emptyList()), model.state.value)
    }

    @Test
    fun `registered devices are restored without an intermediate empty result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val devices = listOf(RegisteredDevice("device", "GEO-X-1"))
        val result = CompletableDeferred<List<RegisteredDevice>>()
        val model = AppRestoreViewModel { result.await() }
        runCurrent()
        assertFalse(model.state.value is AppRestoreState.Restored)
        result.complete(devices)
        runCurrent()
        assertEquals(AppRestoreState.Restored(devices), model.state.value)
    }

    @Test
    fun `credential store failure retains its cause and never becomes zero devices`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cause = IOException("private storage path")
        val model = AppRestoreViewModel {
            restoreRegisteredDevices(flow { throw cause }) { error("No credential was read") }
        }
        runCurrent()
        assertSame(cause, (model.state.value as AppRestoreState.Failed).cause)
        assertFalse(model.state.value is AppRestoreState.Restored)
        assertFalse(restoreFailureWords(cause).contains("private storage path"))
    }

    @Test
    fun `unreadable secret fails the complete restore instead of dropping the device`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stored = listOf(StoredCredential("device", "GEO-X-1", "encrypted", "iv"))
        val model = AppRestoreViewModel { restoreRegisteredDevices(flowOf(stored)) { null } }
        runCurrent()
        val failed = model.state.value as AppRestoreState.Failed
        assertTrue(failed.cause is CredentialRestoreException)
        assertTrue(restoreFailureWords(failed.cause).contains("장치 인증 정보"))
    }

    @Test
    fun `failure after reading one device cannot publish a partial restored list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cause = java.security.GeneralSecurityException("private key details")
        val stored = listOf(
            StoredCredential("one", "GEO-X-1", "encrypted", "iv"),
            StoredCredential("two", "GEO-X-2", "encrypted", "iv")
        )
        val model = AppRestoreViewModel {
            restoreRegisteredDevices(flowOf(stored)) { device ->
                if (device == "two") throw cause
                "secret"
            }
        }
        runCurrent()
        assertSame(cause, (model.state.value as AppRestoreState.Failed).cause)
        assertFalse(restoreFailureWords(cause).contains("private key details"))
    }

    @Test
    fun `retry reenters loading and ignores repeated taps until its read completes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var attempts = 0
        val result = CompletableDeferred<List<RegisteredDevice>>()
        val model = AppRestoreViewModel {
            attempts++
            if (attempts == 1) throw IOException()
            result.await()
        }
        runCurrent()
        assertTrue(model.state.value is AppRestoreState.Failed)
        model.retry()
        assertEquals(AppRestoreState.Loading, model.state.value)
        model.retry()
        runCurrent()
        assertEquals(2, attempts)
        result.complete(listOf(RegisteredDevice("device", "GEO-X-1")))
        runCurrent()
        assertEquals("device", (model.state.value as AppRestoreState.Restored).devices.single().deviceId)
    }

    @Test
    fun `cancellation is not presented as a storage failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = AppRestoreViewModel { throw CancellationException("view model cleared") }
        runCurrent()
        assertEquals(AppRestoreState.Loading, model.state.value)
    }
}
