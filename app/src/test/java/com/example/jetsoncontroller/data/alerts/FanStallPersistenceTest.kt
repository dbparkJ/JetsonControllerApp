package com.example.jetsoncontroller.data.alerts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.FanStatus
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FanStallPersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `fan history retry after reopen preserves one record timestamp and read state`() = runTest {
        val file = File(folder.root, "history.preferences_pb")
        val firstJob = SupervisorJob()
        val target = AlertTarget("device-a", "GEO-X-1")
        val first = AlertHistoryStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }
        ), clock = { 123L })
        try {
            first.addFanStall("episode-a", "title", "message", target)
            first.markRead("episode-a")
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        val reopened = AlertHistoryStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }
        ), clock = { 999L })
        try {
            val retried = reopened.addFanStall("episode-a", "retry", "retry", target)
            assertEquals(123L, retried.createdAtEpochMillis)
            assertTrue(retried.read)
            assertEquals("title", retried.title)
            assertEquals(AlertSeverity.ERROR, retried.severity)
            assertEquals(AlertDestination.SENSORS, retried.destination)
            assertEquals(listOf(retried), reopened.alerts.first())
            reopened.addFanStall("episode-b", "new fault", "message", target)
            assertEquals(2, reopened.alerts.first().size)
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun `pending survives disk reopen and retries without requiring another stall`() = runTest {
        val file = File(folder.root, "alerts.preferences_pb")
        val firstJob = SupervisorJob()
        val first = AlertPreferencesStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }
        ))
        val episode: String
        try {
            first.setStorageThreshold(90)
            first.setLatches(storage = true, temperature = true)
            episode = requireNotNull(first.claimFanStall("DEVICE-A"))
            assertEquals(episode, first.claimFanStall("device-a"))
            val other = requireNotNull(first.claimFanStall("device-b"))
            first.completeFanStall("device-b", other)
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        val reopened = AlertPreferencesStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }
        ))
        try {
            assertEquals(episode, reopened.pendingFanStall("device-a"))
            assertNull(reopened.claimFanStall("device-b"))
            val completed = CompletableDeferred<Unit>()
            val tracking = object : FanStallLatchStore by reopened {
                override suspend fun completeFanStall(deviceId: String, episodeId: String) {
                    reopened.completeFanStall(deviceId, episodeId)
                    completed.complete(Unit)
                }
            }
            val state = MutableStateFlow<TransportState>(TransportState.Connected(
                TransportType.WIFI_DIRECT, "192.168.49.1", "device-a", "GEO-X-1"
            ))
            val deliveries = mutableListOf<String>()
            val poller = launch {
                FanAlertPoller(state, { Result.failure<FanStatus>(IllegalStateException("offline")) },
                    tracking, { false }) { transport, allowed, id ->
                    assertEquals("device-a", transport.deviceId)
                    assertFalse(allowed)
                    deliveries += id
                }.run()
            }
            completed.await()
            poller.cancelAndJoin()
            assertEquals(listOf(episode), deliveries)
            assertNull(reopened.pendingFanStall("device-a"))
            assertNull(reopened.claimFanStall("device-a"))
            reopened.clearFanStall("device-a")
            assertNotEquals(episode, reopened.claimFanStall("device-a"))
            assertNull(reopened.claimFanStall("device-b"))
            val settings = reopened.settings.first()
            assertEquals(90, settings.storageThresholdPercent)
            assertTrue(settings.storageAlertLatched)
            assertTrue(settings.temperatureAlertLatched)
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun `concurrent claims reuse one episode and stale completion cannot consume a new episode`() = runTest {
        val job = SupervisorJob()
        val store = AlertPreferencesStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(folder.root, "claims.preferences_pb") }
        ))
        try {
            val claims = (1..8).map { async { store.claimFanStall("device-a") } }.awaitAll()
            val first = requireNotNull(claims.first())
            assertEquals(setOf(first), claims.toSet())
            store.clearFanStall("device-a")
            assertEquals(first, store.pendingFanStall("device-a"))
            store.completeFanStall("device-a", "wrong-episode")
            assertEquals(first, store.pendingFanStall("device-a"))
            store.completeFanStall("device-a", first)
            assertNull(store.claimFanStall("device-a"))
            store.clearFanStall("device-a")
            val next = requireNotNull(store.claimFanStall("device-a"))
            assertNotEquals(first, next)
            store.completeFanStall("device-a", first)
            assertEquals(next, store.pendingFanStall("device-a"))
        } finally { job.cancelAndJoin() }
    }
}
