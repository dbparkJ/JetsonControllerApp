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
    fun `loader cycle and keyframes match the source with opposite wave directions`() {
        assertEquals(2_400, GeoMotion.loaderCycle)
        val start = loaderFrame(88f, 0f)
        assertEquals(70f, start.period, 0.001f)
        assertEquals(106f, start.maskHeight, 0.001f)
        assertEquals(-12f, start.top, 0.001f)
        assertEquals(0f, start.mainX, 0.001f)
        assertEquals(88f, start.mainY, 0.001f)
        assertEquals(35f, start.secondX, 0.001f)
        assertEquals(80.96f, start.secondY, 0.001f)
        val secondAtTop = loaderFrame(88f, 0.66f)
        assertEquals(175f, secondAtTop.secondX, 0.001f)
        assertEquals(-24f, secondAtTop.secondY, 0.001f)
        assertEquals(0.32f, secondAtTop.secondAlpha, 0.001f)
        assertTrue(secondAtTop.mainY > -12f)
        val mainAtTop = loaderFrame(88f, 0.72f)
        assertEquals(-210f, mainAtTop.mainX, 0.001f)
        assertEquals(-12f, mainAtTop.mainY, 0.001f)
        assertEquals(1f, loaderFrame(88f, 0.88f).mainAlpha, 0.001f)
        assertEquals(-176f, loaderFrame(88f, 0.74f).shineX, 0.001f)
        assertEquals(0f, loaderFrame(88f, 0.96f).shineX, 0.001f)
        assertEquals(0f, loaderFrame(88f, 1f).mainAlpha, 0.001f)
        assertEquals(0f, loaderFrame(88f, 1f).secondAlpha, 0.001f)
    }
}
