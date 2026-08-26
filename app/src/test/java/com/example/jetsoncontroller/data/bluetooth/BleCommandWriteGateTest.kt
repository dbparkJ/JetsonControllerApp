package com.example.jetsoncontroller.data.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleCommandWriteGateTest {
    @Test
    fun `queued write waits for remote callback`() = runBlocking {
        val gate = BleCommandWriteGate(timeoutMillis = 1_000L)
        val initiated = CompletableDeferred<Unit>()
        val write = async {
            gate.write(
                initiate = {
                    initiated.complete(Unit)
                    true
                },
                abortInFlight = {}
            )
        }

        initiated.await()
        assertFalse(write.isCompleted)
        gate.complete(Result.success(Unit))

        assertTrue(write.await().isSuccess)
    }

    @Test
    fun `callback failure is returned to caller`() = runBlocking {
        val gate = BleCommandWriteGate(timeoutMillis = 1_000L)
        val initiated = CompletableDeferred<Unit>()
        val write = async {
            gate.write(
                initiate = {
                    initiated.complete(Unit)
                    true
                },
                abortInFlight = {}
            )
        }

        initiated.await()
        gate.complete(Result.failure(IllegalStateException("remote rejected write")))

        assertEquals("remote rejected write", write.await().exceptionOrNull()?.message)
    }

    @Test
    fun `command writes are single flight`() = runBlocking {
        val gate = BleCommandWriteGate(timeoutMillis = 1_000L)
        var initiationCount = 0
        val first = async {
            gate.write(
                initiate = {
                    initiationCount += 1
                    true
                },
                abortInFlight = {}
            )
        }
        while (initiationCount < 1) yield()

        val second = async {
            gate.write(
                initiate = {
                    initiationCount += 1
                    true
                },
                abortInFlight = {}
            )
        }
        yield()
        assertEquals(1, initiationCount)

        gate.complete(Result.success(Unit))
        assertTrue(first.await().isSuccess)
        while (initiationCount < 2) yield()
        assertFalse(second.isCompleted)
        gate.complete(Result.success(Unit))

        assertTrue(second.await().isSuccess)
    }

    @Test
    fun `cancelling an in flight write aborts it and releases the gate`() = runBlocking {
        val gate = BleCommandWriteGate(timeoutMillis = 1_000L)
        val firstInitiated = CompletableDeferred<Unit>()
        val aborted = CompletableDeferred<String>()
        val first = launch {
            gate.write(
                initiate = {
                    firstInitiated.complete(Unit)
                    true
                },
                abortInFlight = { aborted.complete(it) }
            )
        }

        firstInitiated.await()
        first.cancelAndJoin()
        assertEquals("BLE 명령 쓰기가 취소되었습니다.", aborted.await())

        val secondInitiated = CompletableDeferred<Unit>()
        val second = async {
            gate.write(
                initiate = {
                    secondInitiated.complete(Unit)
                    true
                },
                abortInFlight = {}
            )
        }
        secondInitiated.await()
        gate.complete(Result.success(Unit))
        assertTrue(second.await().isSuccess)
    }
}
