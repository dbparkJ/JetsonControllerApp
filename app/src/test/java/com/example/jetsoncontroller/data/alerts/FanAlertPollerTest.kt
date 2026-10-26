package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.FanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FanAlertPollerTest {
    private val stopped = FanStatus(true, "AUTO", percent = 62, rpm = 0)
    private fun connected(id: String = "device-a", type: TransportType = TransportType.LAN) =
        TransportState.Connected(type, "192.168.1.10", id, "GEO-X-1")

    private class MemoryLatches : FanStallLatchStore {
        val devices = mutableSetOf<String>()
        val pending = mutableMapOf<String, String>()
        var nextEpisode = 0
        var afterClaim: suspend () -> Unit = {}
        var beforeComplete: suspend () -> Unit = {}
        override suspend fun claimFanStall(deviceId: String): String? {
            val key = fanLatchKey(deviceId)
            val episode = pending[key] ?: if (key in devices) null else
                "episode-${++nextEpisode}".also { pending[key] = it }
            afterClaim()
            return episode
        }
        override suspend fun pendingFanStall(deviceId: String) = pending[fanLatchKey(deviceId)]
        override suspend fun completeFanStall(deviceId: String, episodeId: String) {
            beforeComplete()
            val key = fanLatchKey(deviceId)
            if (pending[key] == episodeId) {
                devices.add(key)
                pending.remove(key)
            }
        }
        override suspend fun clearFanStall(deviceId: String) { devices.remove(fanLatchKey(deviceId)) }
    }

    private inner class Harness {
        val state = MutableStateFlow<TransportState>(connected())
        val latches = MemoryLatches()
        val history = mutableListOf<Pair<TransportState.Connected, Boolean>>()
        var allowed = true
        var reads = 0
        var read: suspend () -> Result<FanStatus> = { Result.success(stopped) }
        var beforePublish: suspend () -> Unit = {}
        var afterPublish: suspend () -> Unit = {}
        val episodes = mutableSetOf<String>()
        fun start(scope: CoroutineScope) = scope.launch {
            FanAlertPoller(state, { reads++; read() }, latches, { allowed }) { device, notify, episode ->
                beforePublish()
                if (episodes.add(episode)) history += device to notify
                afterPublish()
            }.run()
        }
    }

    @Test fun `two samples five seconds apart publish history even without permission`() = runTest {
        val h = Harness()
        h.allowed = false
        h.start(backgroundScope)
        runCurrent()
        assertEquals(1, h.reads)
        assertEquals(0, h.history.size)
        advanceTimeBy(4_999); runCurrent()
        assertEquals(0, h.history.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, h.history.size)
        assertFalse(h.history.single().second)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, h.history.size)
    }

    @Test fun `fresh recovery rearms but unknown unsupported and stopped output do not`() = runTest {
        val h = Harness()
        h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        listOf(null, stopped.copy(available = false), stopped.copy(percent = 0), stopped.copy(rpm = null)).forEach { sample ->
            h.read = { sample?.let { Result.success(it) } ?: Result.failure(IllegalStateException()) }
            advanceTimeBy(5_000); runCurrent()
            assertEquals(setOf("device-a"), h.latches.devices)
        }
        h.read = { Result.success(stopped.copy(rpm = 900)) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(emptySet<String>(), h.latches.devices)
        h.read = { Result.success(stopped) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, h.history.size)
    }

    @Test fun `failed query breaks debounce instead of reusing previous zero`() = runTest {
        val h = Harness()
        h.start(backgroundScope); runCurrent()
        h.read = { Result.failure(IllegalStateException("offline")) }
        advanceTimeBy(5_000); runCurrent()
        h.read = { Result.success(stopped) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.history.size)
    }

    @Test fun `reconnect requires two new samples and does not repeat latched fault`() = runTest {
        val h = Harness()
        h.start(backgroundScope); runCurrent()
        h.state.value = TransportState.Disconnected; runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, h.reads)
        assertEquals(0, h.history.size)
        h.state.value = connected(); runCurrent()
        assertEquals(0, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.history.size)
        h.state.value = TransportState.Disconnected; runCurrent()
        h.state.value = connected(type = TransportType.WIFI_DIRECT); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, h.history.size)
    }

    @Test fun `poller recreation retains per device latch and replacement device may alert`() = runTest {
        val h = Harness()
        val first = h.start(backgroundScope); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        first.cancel(); runCurrent()
        h.start(backgroundScope); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, h.history.size)
        h.state.value = connected("device-b"); runCurrent()
        assertEquals(1, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("device-a", "device-b"), h.history.map { it.first.deviceId })
    }

    @Test fun `late old device response cannot publish or clear its latch`() = runTest {
        val h = Harness()
        h.latches.devices.add("device-a")
        val response = CompletableDeferred<Result<FanStatus>>()
        h.read = { withContext(NonCancellable) { response.await() } }
        h.start(backgroundScope); runCurrent()
        h.state.value = connected("device-b"); runCurrent()
        h.read = { Result.success(stopped) }
        response.complete(Result.success(stopped.copy(rpm = 900))); runCurrent()
        assertEquals(setOf("device-a"), h.latches.devices)
        assertEquals(0, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals("device-b", h.history.single().first.deviceId)
    }

    @Test fun `old transport zero response is ignored after transport switch`() = runTest {
        val h = Harness()
        val response = CompletableDeferred<Result<FanStatus>>()
        var call = 0
        h.read = {
            if (++call == 2) withContext(NonCancellable) { response.await() }
            else Result.success(stopped)
        }
        h.start(backgroundScope); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        h.state.value = connected(type = TransportType.WIFI_DIRECT); runCurrent()
        response.complete(Result.success(stopped)); runCurrent()
        assertEquals(0, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(TransportType.WIFI_DIRECT, h.history.single().first.type)
    }

    @Test fun `repository stale generation cancellation resets samples and polling continues`() = runTest {
        val h = Harness()
        h.start(backgroundScope); runCurrent()
        h.read = { throw CancellationException("stale generation") }
        advanceTimeBy(5_000); runCurrent()
        h.read = { Result.success(stopped) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, h.history.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.history.size)
    }

    @Test fun `BLE missing identity and missing endpoint never poll`() = runTest {
        val h = Harness()
        h.state.value = connected(type = TransportType.BLE)
        h.start(backgroundScope); runCurrent()
        h.state.value = connected().copy(deviceId = null); runCurrent()
        h.state.value = connected().copy(endpoint = null); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, h.reads)
        assertEquals(0, h.history.size)
    }

    @Test fun `cancellation immediately after durable claim retries on poller restart`() = runTest {
        val h = Harness()
        val blocked = CompletableDeferred<Unit>()
        h.latches.afterClaim = { blocked.await() }
        val first = h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        val episode = h.latches.pending.getValue("device-a")
        assertTrue(h.latches.devices.isEmpty())
        assertTrue(h.history.isEmpty())
        first.cancel(); runCurrent()
        h.latches.afterClaim = {}
        h.start(backgroundScope); runCurrent()
        assertEquals(setOf(episode), h.episodes)
        assertEquals(setOf("device-a"), h.latches.devices)
        assertTrue(h.latches.pending.isEmpty())
    }

    @Test fun `transport replacement after claim preserves pending for that device reconnect`() = runTest {
        val h = Harness()
        h.latches.afterClaim = {
            h.state.value = connected("device-b", TransportType.WIFI_DIRECT)
        }
        h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        val episode = h.latches.pending.getValue("device-a")
        assertTrue(h.history.isEmpty())
        assertTrue(h.latches.devices.isEmpty())
        h.latches.afterClaim = {}
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("device-b"), h.history.map { it.first.deviceId })
        assertEquals(episode, h.latches.pending["device-a"])
        h.state.value = TransportState.Disconnected; runCurrent()
        h.state.value = connected(type = TransportType.WIFI_DIRECT); runCurrent()
        assertEquals(listOf("device-b", "device-a"), h.history.map { it.first.deviceId })
        assertTrue(h.latches.pending.isEmpty())
    }

    @Test fun `history failure backs off retries and continues monitoring another device`() = runTest {
        val h = Harness()
        var attempts = 0
        h.beforePublish = { if (++attempts == 1) throw IllegalStateException("history failed") }
        val job = h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertTrue(job.isActive)
        assertTrue(h.latches.devices.isEmpty())
        assertEquals(1, h.latches.pending.size)
        advanceTimeBy(4_999); runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, attempts)
        assertEquals(1, h.history.size)
        assertTrue(h.latches.pending.isEmpty())
        h.state.value = connected("device-b"); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("device-a", "device-b"), h.history.map { it.first.deviceId })
    }

    @Test fun `notification failure retries same episode without duplicate history and monitors next device`() = runTest {
        val h = Harness()
        var attempts = 0
        h.afterPublish = { if (++attempts == 1) throw SecurityException("notify failed") }
        val job = h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertTrue(job.isActive)
        assertEquals(1, h.history.size)
        assertTrue(h.latches.devices.isEmpty())
        val episode = h.latches.pending.getValue("device-a")
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, attempts)
        assertEquals(setOf(episode), h.episodes)
        assertEquals(1, h.history.size)
        assertTrue(h.latches.pending.isEmpty())
        h.state.value = connected("device-b"); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("device-a", "device-b"), h.history.map { it.first.deviceId })
    }

    @Test fun `failed completion retries same episode even after positive recovery`() = runTest {
        val h = Harness()
        var completes = 0
        h.latches.beforeComplete = { if (++completes == 1) throw IllegalStateException("disk failed") }
        h.start(backgroundScope)
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.latches.pending.size)
        h.read = { Result.success(stopped.copy(rpm = 900)) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, completes)
        assertEquals(1, h.history.size)
        assertTrue(h.latches.pending.isEmpty())
        assertTrue(h.latches.devices.isEmpty())
        h.read = { Result.success(stopped) }
        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, h.history.size)
    }
}
