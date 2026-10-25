package com.example.jetsoncontroller.ui.startup

import com.example.jetsoncontroller.ui.components.geo.loaderFrame
import com.example.jetsoncontroller.ui.theme.GeoMotion
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class LoaderTimingTest {
    @Test
    fun `unknown restore holds the first frame until 300 ms`() {
        val timing = StartupTiming(1_000)
        assertFalse(timing.canDraw(AppRestoreState.Loading, 1_000))
        assertFalse(timing.canDraw(AppRestoreState.Loading, 1_299))
        assertTrue(timing.canDraw(AppRestoreState.Loading, 1_300))
    }

    @Test
    fun `fast success or failure releases the splash without showing the loader`() {
        listOf(AppRestoreState.Restored(emptyList()), AppRestoreState.Failed(IOException())).forEach { state ->
            val timing = StartupTiming(1_000)
            assertTrue(timing.canDraw(state, 1_001))
            assertNull(timing.loadingVisibleAtMillis)
            assertEquals(0L, timing.remainingLoadingMillis(1_001))
            assertEquals(0, timing.crossfadeMillis(reducedMotion = false))
        }
    }

    @Test
    fun `loader stays for at least 400 ms measured from its actual first frame`() {
        val timing = StartupTiming(1_000)
        timing.loadingFrame(1_320)
        timing.loadingFrame(1_400)
        assertEquals(400L, timing.remainingLoadingMillis(1_320))
        assertEquals(1L, timing.remainingLoadingMillis(1_719))
        assertEquals(0L, timing.remainingLoadingMillis(1_720))
        assertEquals(0L, timing.remainingLoadingMillis(5_000))
        assertEquals(240, timing.crossfadeMillis(reducedMotion = false))
        assertEquals(0, timing.crossfadeMillis(reducedMotion = true))
    }

    @Test
    fun `a loader drawn behind the splash starts its minimum only when the splash is removed`() {
        val timing = StartupTiming(0, splashVisible = true)
        timing.loadingFrame(300)
        assertNull(timing.loadingVisibleAtMillis)
        timing.splashRemoved(AppRestoreState.Loading, 320)
        assertEquals(400L, timing.remainingLoadingMillis(320))
        assertEquals(1L, timing.remainingLoadingMillis(719))
        assertEquals(0L, timing.remainingLoadingMillis(720))
    }

    @Test
    fun `restore finishing while the splash still covers the loader bypasses the scene`() {
        val timing = StartupTiming(0, splashVisible = true)
        timing.loadingFrame(300)
        timing.splashRemoved(AppRestoreState.Restored(emptyList()), 310)
        assertNull(timing.loadingVisibleAtMillis)
        assertEquals(0L, timing.remainingLoadingMillis(310))
        assertEquals(0, timing.crossfadeMillis(reducedMotion = false))
    }

    @Test
    fun `retry starts a fresh minimum instead of using the failed attempts timestamp`() {
        val timing = StartupTiming(0)
        timing.loadingFrame(300)
        timing.retry()
        assertNull(timing.loadingVisibleAtMillis)
        timing.loadingFrame(2_000)
        assertEquals(400L, timing.remainingLoadingMillis(2_000))
        assertEquals(1L, timing.remainingLoadingMillis(2_399))
    }

    @Test
    fun `loader reveals linearly then holds and fades over 2400 ms`() {
        assertEquals(2_400, GeoMotion.loaderCycle)
        val start = loaderFrame(128f, 0f)
        assertEquals(0f, start.reveal, .001f)
        assertEquals(128f, start.clipTop, .001f)
        assertEquals(1f, start.foregroundAlpha, .001f)
        assertEquals(64f, loaderFrame(128f, .35f).clipTop, .001f)
        assertEquals(1f, loaderFrame(128f, .7f).reveal, .001f)
        assertEquals(1f, loaderFrame(128f, .9f).foregroundAlpha, .001f)
        assertEquals(.5f, loaderFrame(128f, .95f).foregroundAlpha, .001f)
        assertEquals(0f, loaderFrame(128f, 1f).foregroundAlpha, .001f)
    }

    @Test
    fun `reduced motion is a complete static symbol at every fraction`() {
        val still = loaderFrame(128f, 0f, reducedMotion = true)
        assertEquals(1f, still.reveal, 0f)
        assertEquals(1f, still.foregroundAlpha, 0f)
        assertEquals(0f, still.clipTop, 0f)
        listOf(-1f, 0f, .25f, .7f, .95f, 1f, 2f).forEach { fraction ->
            assertEquals(still, loaderFrame(128f, fraction, reducedMotion = true))
        }
    }
}
