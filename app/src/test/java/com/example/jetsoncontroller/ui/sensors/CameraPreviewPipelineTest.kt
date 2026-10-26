package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class CameraPreviewPipelineTest {
    @After fun resetMain() = Dispatchers.resetMain()

    private class Fixture {
        val transport = MutableStateFlow<TransportState>(TransportState.Connected(TransportType.LAN, deviceId = "A"))
        val repository = Mockito.mock(JetsonRepository::class.java).also {
            Mockito.`when`(it.selectedDeviceId).thenReturn(MutableStateFlow<String?>("A"))
            Mockito.`when`(it.transportState).thenReturn(transport)
            Mockito.`when`(it.registeredDevices).thenReturn(MutableStateFlow(emptyList<RegisteredDevice>()))
        }
        val bitmap = Mockito.mock(Bitmap::class.java)
        var environment = CameraPreviewEnvironment(unmetered = true, powerSave = false, localLanRoute = true)
    }

    private fun CameraPreviewViewModel.start() {
        setSensorActive(true)
        setVisible(true)
    }

    private suspend fun TestScope.simulate(overlap: Boolean): Int {
        val f = Fixture()
        f.environment = f.environment.copy(unmetered = overlap)
        var fetching = 0
        var decoding = 0
        var overlapObserved = false
        val startTime = testScheduler.currentTime
        val published = mutableListOf<Long>()
        val afterRevisions = mutableListOf<Long?>()
        val model = CameraPreviewViewModel(f.repository,
            elapsedRealtime = { 10_000 + testScheduler.currentTime },
            wallClock = { 1_700_000_000_000 + testScheduler.currentTime },
            fetchFrame = { after ->
                afterRevisions.add(after)
                fetching++
                assertEquals(1, fetching)
                try {
                    val elapsed = testScheduler.currentTime - startTime
                    val latest = elapsed / 67 + 1
                    if (after != null && latest <= after) delay(after * 67 - elapsed)
                    delay(30) // HTTP wait/download/authentication; latest-only server, no frame queue.
                    Result.success(CameraPreviewFrame(byteArrayOf(1), (testScheduler.currentTime - startTime) / 67 + 1))
                } finally { fetching-- }
            },
            decodeFrame = {
                decoding++
                assertEquals(1, decoding)
                overlapObserved = overlapObserved || fetching > 0
                try { delay(50); f.bitmap } finally { decoding-- }
            },
            environment = { f.environment }
        )
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { state -> state.frameRevision?.let { revision ->
                if (published.lastOrNull() != revision) published.add(revision)
            } }
        }
        try {
            model.start()
            advanceTimeBy(2_100); runCurrent()
            assertEquals(overlap, overlapObserved)
            assertEquals(published.sorted().distinct(), published)
            assertNull(afterRevisions.first())
            assertTrue(afterRevisions.drop(1).all { it != null })
            if (overlap) {
                assertTrue("15fps input: $published", published.size >= 30)
                assertTrue(published.zipWithNext().all { (a, b) -> b == a + 1 })
            }
            return published.size
        } finally {
            model.setVisible(false)
            runCurrent()
            assertEquals(0, fetching)
            assertEquals(0, decoding)
            observer.cancel()
            model.viewModelScope.cancel()
        }
    }

    @Test fun overlapPresentsAlmostEvery15FpsRevisionWithOneFetchAndOneDecode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fast = simulate(true)
        val serial = simulate(false)
        assertTrue("overlap=$fast serial=$serial", fast > serial)
    }

    @Test fun unsafeConditionsSerializeAndEachFollowingRequestReadsTheNewPolicy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val safe = CameraPreviewEnvironment(true, false, true)
        for (unsafe in listOf(safe.copy(unmetered = false), safe.copy(powerSave = true),
            safe.copy(localLanRoute = false), safe.copy(thermalLimited = true))) {
            val f = Fixture()
            var fetching = false
            var revision = 0L
            val overlapped = mutableListOf<Boolean>()
            val model = CameraPreviewViewModel(f.repository,
                elapsedRealtime = { 10_000 + testScheduler.currentTime },
                fetchFrame = {
                    assertFalse(fetching)
                    fetching = true
                    try { delay(30); Result.success(CameraPreviewFrame(byteArrayOf(1), ++revision)) }
                    finally { fetching = false }
                },
                decodeFrame = { overlapped.add(fetching); delay(50); f.bitmap },
                environment = { f.environment }
            )
            try {
                model.start()
                advanceTimeBy(40); runCurrent()
                assertEquals(listOf(true), overlapped)
                f.environment = unsafe // A request already started may finish; no new overlap starts.
                advanceTimeBy(200); runCurrent()
                assertTrue(overlapped.drop(1).isNotEmpty())
                assertTrue(overlapped.drop(1).none { it })
                f.environment = safe
                advanceTimeBy(200); runCurrent()
                assertTrue(overlapped.drop(2).any { it })
            } finally { model.setVisible(false); model.viewModelScope.cancel(); runCurrent() }
        }
    }

    @Test fun leavingScreenDiscardsEvenANonCancellableLookaheadResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        val late = CompletableDeferred<Unit>()
        var calls = 0
        var decoded = 0
        val model = CameraPreviewViewModel(f.repository,
            elapsedRealtime = { 10_000 + testScheduler.currentTime },
            fetchFrame = {
                calls++
                if (calls > 1) withContext(NonCancellable) { late.await() }
                Result.success(CameraPreviewFrame(byteArrayOf(1), calls.toLong()))
            },
            decodeFrame = { decoded++; delay(10); f.bitmap },
            environment = { f.environment }
        )
        try {
            model.start()
            advanceTimeBy(20); runCurrent()
            assertNotNull(model.uiState.value.frame)
            assertEquals(2, calls)
            model.setVisible(false)
            assertNull(model.uiState.value.frame)
            runCurrent()
            late.complete(Unit)
            runCurrent()
            assertNull(model.uiState.value.frame)
            assertEquals(1, decoded)
            assertEquals(2, calls)
        } finally { late.complete(Unit); model.viewModelScope.cancel() }
    }

    @Test fun failureWaitsOneSecondAndRefreshCannotAddAConcurrentRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        var calls = 0
        val model = CameraPreviewViewModel(f.repository,
            elapsedRealtime = { 10_000 + testScheduler.currentTime },
            fetchFrame = {
                calls++
                delay(20)
                Result.failure<CameraPreviewFrame>(IllegalStateException("offline"))
            },
            decodeFrame = { f.bitmap }, environment = { f.environment }
        )
        try {
            model.start()
            runCurrent()
            repeat(10) { model.refresh() }
            runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(20); runCurrent()
            // Explicit refresh can wake the retry delay, but is still processed by the same actor.
            advanceTimeBy(20); runCurrent()
            val beforeRetry = calls
            advanceTimeBy(999); runCurrent()
            assertEquals(beforeRetry, calls)
            advanceTimeBy(1); runCurrent()
            assertEquals(beforeRetry + 1, calls)
        } finally { model.setVisible(false); model.viewModelScope.cancel() }
    }

    @Test fun legacyFramesKeepThe67MillisDelayWithoutLookahead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        var calls = 0
        val model = CameraPreviewViewModel(f.repository,
            elapsedRealtime = { 10_000 + testScheduler.currentTime },
            fetchFrame = { after ->
                assertNull(after)
                calls++
                delay(20)
                Result.success(CameraPreviewFrame(byteArrayOf(1), null))
            },
            decodeFrame = { assertEquals(1, calls); delay(10); f.bitmap },
            environment = { f.environment }
        )
        try {
            model.start()
            advanceTimeBy(96); runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(1); runCurrent()
            assertEquals(2, calls)
        } finally { model.setVisible(false); model.viewModelScope.cancel() }
    }

    @Test fun unchangedLookaheadDoesNotDecodeAgainOrRefreshFrameFreshness() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        var calls = 0
        var decodes = 0
        val model = CameraPreviewViewModel(f.repository,
            elapsedRealtime = { 10_000 + testScheduler.currentTime },
            fetchFrame = {
                calls++
                delay(20)
                Result.success(CameraPreviewFrame(byteArrayOf(1), 1L))
            },
            decodeFrame = { decodes++; delay(10); f.bitmap },
            environment = { f.environment }
        )
        try {
            model.start()
            advanceTimeBy(40); runCurrent()
            assertEquals(2, calls)
            assertEquals(1, decodes)
            assertEquals(10_020L, model.uiState.value.frameFreshness.receivedAt)
            advanceTimeBy(66); runCurrent()
            assertEquals(2, calls)
            advanceTimeBy(1); runCurrent()
            assertEquals(3, calls)
            advanceTimeBy(20); runCurrent()
            assertEquals(1, decodes)
            assertEquals(10_020L, model.uiState.value.frameFreshness.receivedAt)
        } finally { model.setVisible(false); model.viewModelScope.cancel() }
    }
}
