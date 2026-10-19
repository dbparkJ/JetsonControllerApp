package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarTimeoutTest {
    @Test
    fun baseDurationIsEightSecondsWithActionAndFourWithout() {
        assertEquals(8_000L, snackbarTimeoutMillis(hasAction = true))
        assertEquals(4_000L, snackbarTimeoutMillis(hasAction = false))
    }

    @Test
    fun accessibilityReceivesTextAndActionControlsAndItsTimeoutIsUsed() {
        val timeout = snackbarTimeoutMillis(hasAction = true) { original, icons, text, controls ->
            assertEquals(8_000L, original)
            assertFalse(icons)
            assertTrue(text)
            assertTrue(controls)
            60_000L
        }
        assertEquals(60_000L, timeout)
    }

    @Test
    fun visibleDismissIconIsAControlEvenWithoutUndo() {
        assertEquals(20_000L, snackbarTimeoutMillis(hasAction = false, hasDismissAction = true) { original, icons, text, controls ->
            assertEquals(4_000L, original)
            assertTrue(icons)
            assertTrue(text)
            assertTrue(controls)
            20_000L
        })
    }

    @Test
    fun plainMessageDoesNotClaimControlsAndIndefiniteRecommendationIsPreserved() {
        assertEquals(Long.MAX_VALUE, snackbarTimeoutMillis(false) { _, icons, text, controls ->
            assertFalse(icons)
            assertTrue(text)
            assertFalse(controls)
            Long.MAX_VALUE
        })
    }

    @Test
    fun undoRequiresBothVisibleLabelAndCallbackAndKeepsItsOriginalTarget() {
        assertNull(snackbarAction("실행 취소", null))
        assertNull(snackbarAction(null, {}))
        assertNull(snackbarAction(" ", {}))
        var target = ""
        var callback: () -> Unit = { target = "first" }
        val action = snackbarAction("실행 취소", callback)!!
        callback = { target = "second" }
        action.onClick()
        assertEquals("실행 취소", action.label)
        assertEquals("first", target)
        callback()
        assertEquals("second", target)
    }

    @Test
    fun drawnUndoCompletesTheHostActionAndTheHostDoesNotStartAShorterTimer() = runTest {
        val host = SnackbarHostState()
        val result = async { host.showGeoSnackbar("휴지통으로 옮겼습니다.", "실행 취소", withDismissAction = true) }
        runCurrent()
        val data = host.currentSnackbarData!!
        assertEquals("실행 취소", data.visuals.actionLabel)
        assertTrue(data.visuals.withDismissAction)
        assertEquals(SnackbarDuration.Indefinite, data.visuals.duration)
        data.performAction()
        assertEquals(SnackbarResult.ActionPerformed, result.await())
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun dismissalAdvancesExactlyOneMessageAndCancellationConsumesTheNext() = runTest {
        val host = SnackbarHostState()
        val first = async { host.showGeoSnackbar("first") }
        val second = async { host.showGeoSnackbar("second") }
        runCurrent()
        assertEquals("first", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.dismiss()
        assertEquals(SnackbarResult.Dismissed, first.await())
        runCurrent()
        assertEquals("second", host.currentSnackbarData?.visuals?.message)
        second.cancelAndJoin()
        assertNull(host.currentSnackbarData)
    }
}
