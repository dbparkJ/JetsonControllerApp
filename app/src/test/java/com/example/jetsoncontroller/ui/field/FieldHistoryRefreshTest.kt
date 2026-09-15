package com.example.jetsoncontroller.ui.field

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldHistoryRefreshTest {
    @Test
    fun `background refresh keeps loaded history steady`() {
        assertFalse(historyRefreshShowsLoading(more = false, retainLoaded = true, hasLoadedRuns = true))
        assertTrue(historyRefreshShowsLoading(more = false, retainLoaded = true, hasLoadedRuns = false))
        assertTrue(historyRefreshShowsLoading(more = true, retainLoaded = true, hasLoadedRuns = true))
        assertTrue(historyRefreshShowsLoading(more = false, retainLoaded = false, hasLoadedRuns = true))
    }
}
