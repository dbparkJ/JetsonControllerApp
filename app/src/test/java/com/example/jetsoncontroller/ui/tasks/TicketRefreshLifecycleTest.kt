package com.example.jetsoncontroller.ui.tasks

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketRefreshLifecycleTest {
    @After fun resetMain() = Dispatchers.resetMain()

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test fun `polls immediately on entry every thirty seconds and immediately after returning`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = Owner()
        var requests = 0
        val polling = backgroundScope.launch { refreshTicketsWhileVisible(owner.lifecycle) { requests++ } }
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        runCurrent()
        assertEquals(0, requests)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(29_999); runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, requests)
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        runCurrent()
        advanceTimeBy(90_000); runCurrent()
        assertEquals(2, requests)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(3, requests)
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(3, requests)
        assertTrue(polling.isCompleted)
    }

    @Test fun `leaving cancels an in flight refresh and returning starts a new one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = Owner()
        var started = 0
        var finished = 0
        val polling = backgroundScope.launch {
            refreshTicketsWhileVisible(owner.lifecycle) {
                started++
                try { delay(60_000) } finally { finished++ }
            }
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(1, started)
        assertEquals(0, finished)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        runCurrent()
        assertEquals(1, finished)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, started)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(2, started)
        polling.cancel()
        runCurrent()
        assertEquals(2, finished)
    }
}
