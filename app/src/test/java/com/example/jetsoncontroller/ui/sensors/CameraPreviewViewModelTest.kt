package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.CameraPreviewFrame
import com.example.jetsoncontroller.model.RegisteredDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class CameraPreviewViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    private class Fixture {
        val selected = MutableStateFlow<String?>("A")
        val transport = MutableStateFlow<TransportState>(TransportState.Connected(TransportType.LAN, deviceId = "A"))
        val repository = Mockito.mock(JetsonRepository::class.java).also {
            Mockito.`when`(it.selectedDeviceId).thenReturn(selected)
            Mockito.`when`(it.transportState).thenReturn(transport)
            Mockito.`when`(it.registeredDevices).thenReturn(MutableStateFlow(emptyList<RegisteredDevice>()))
        }
        val bitmap = Mockito.mock(Bitmap::class.java)
        val frames = Channel<CameraPreviewFrame>(Channel.UNLIMITED)
        var requests = 0
        suspend fun frame(revision: Long?): Result<CameraPreviewFrame> {
            requests++
            return Result.success(frames.receive())
        }
        fun send(revision: Long) { check(frames.trySend(CameraPreviewFrame(byteArrayOf(1), revision)).isSuccess) }
    }

    @Test fun backgroundCancelsPollingDropsBitmapAndResumesWithoutFollowingAnotherDevice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        val viewModel = CameraPreviewViewModel(f.repository, { 10_000 + testScheduler.currentTime },
            fetchFrame = f::frame, decodeFrame = { f.bitmap })
        try {
            viewModel.setSensorActive(true)
            viewModel.setVisible(true)
            f.send(1)
            runCurrent()
            assertSame(f.bitmap, viewModel.uiState.value.frame)
            viewModel.setVisible(false)
            assertNull(viewModel.uiState.value.frame)
            runCurrent()
            val stoppedRequests = f.requests
            advanceTimeBy(10_000); runCurrent()
            assertEquals(stoppedRequests, f.requests)
            f.selected.value = "B"
            f.transport.value = TransportState.Connected(TransportType.LAN, deviceId = "B")
            viewModel.setVisible(true)
            runCurrent()
            assertEquals("A", viewModel.uiState.value.deviceId)
            assertFalse(viewModel.uiState.value.targetConnected)
            assertEquals(stoppedRequests, f.requests)
            f.selected.value = "A"
            f.transport.value = TransportState.Connected(TransportType.LAN, deviceId = "A")
            f.send(2)
            runCurrent()
            assertSame(f.bitmap, viewModel.uiState.value.frame)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun wallClockJumpsAndDeviceRevisionTimesCannotAffectFrameFreshness() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        var wallTime = 1_700_000_000_000L
        val viewModel = CameraPreviewViewModel(f.repository, { 10_000 + testScheduler.currentTime },
            { wallTime }, f::frame, { f.bitmap })
        try {
            viewModel.setSensorActive(true)
            viewModel.setVisible(true)
            f.send(1_799_999_999_000_000_000L)
            runCurrent()
            for (jump in listOf(-86_400_000L, 3_600_000L, 31_536_000_000L)) {
                wallTime += jump
                advanceTimeBy(1_100)
                f.send(wallTime * 1_000_000)
                runCurrent()
                val state = viewModel.uiState.value
                assertEquals(wallTime, state.updatedAtEpochMillis)
                assertEquals(10_000 + testScheduler.currentTime, state.frameFreshness.receivedAt)
                assertEquals(CameraStageState.LIVE, cameraStageState(true, true,
                    state.frameFreshness.receivedAt, state.checkedAtElapsedMillis, false, freshness = state.frameFreshness))
            }
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun lateDecodeCannotRestoreABitmapAfterStopOrMemoryTrim() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        val decoded = CompletableDeferred<Bitmap>()
        val viewModel = CameraPreviewViewModel(f.repository, { 10_000 + testScheduler.currentTime },
            fetchFrame = f::frame, decodeFrame = { withContext(NonCancellable) { decoded.await() } })
        try {
            viewModel.setSensorActive(true)
            viewModel.setVisible(true)
            f.send(1)
            runCurrent()
            viewModel.trimMemory()
            viewModel.setVisible(false)
            assertNull(viewModel.uiState.value.frame)
            runCurrent()
            decoded.complete(f.bitmap)
            runCurrent()
            assertNull(viewModel.uiState.value.frame)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun restoredOpenerAndDelayedCredentialRestoreKeepTheCorrectCameraTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        f.selected.value = null
        val saved = SavedStateHandle(mapOf(CAMERA_PREVIEW_TARGET_KEY to "A"))
        val viewModel = CameraPreviewViewModel(f.repository, { 10_000 + testScheduler.currentTime },
            fetchFrame = f::frame, decodeFrame = { f.bitmap }, savedStateHandle = saved)
        try {
            viewModel.setSensorActive(true)
            viewModel.setVisible(true)
            runCurrent()
            assertEquals(0, f.requests)
            f.selected.value = "A"
            f.send(1)
            runCurrent()
            assertSame(f.bitmap, viewModel.uiState.value.frame)
            viewModel.close()
            runCurrent()
            assertNull(saved.get<String>(CAMERA_PREVIEW_TARGET_KEY))
        } finally { viewModel.viewModelScope.cancel() }
    }
}
