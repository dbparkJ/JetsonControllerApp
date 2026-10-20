package com.example.jetsoncontroller.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMapLoadStateTest {
    @Test fun loadsUntilNaverReportsTheFirstLoad() {
        val start = GeoMapLoadState()
        assertTrue(start.loading)
        assertFalse(start.failed)
        val loaded = start.mapLoaded()
        assertFalse(loaded.loading)
        assertFalse(loaded.failed)
    }

    @Test fun aStalledFirstLoadFailsUntilTheMapArrives() {
        val stalled = GeoMapLoadState().loadStalled()
        assertTrue(stalled.failed)
        assertFalse("the failure replaces the loading note", stalled.loading)
        assertFalse(stalled.mapLoaded().failed)
        val late = GeoMapLoadState().mapLoaded()
        assertEquals("a timer that fires after the load changes nothing", late, late.loadStalled())
    }

    @Test fun aRejectedClientFailsEvenAfterALoad() {
        assertTrue(GeoMapLoadState().authFailure().failed)
        assertFalse(GeoMapLoadState().authFailure().loading)
        assertTrue(GeoMapLoadState().mapLoaded().authFailure().failed)
        assertTrue(GeoMapLoadState().authFailure().mapLoaded().failed)
    }

    @Test fun withoutAClientThereIsNothingToLoadOrFail() {
        // The screens say 배경 지도를 쓸 수 없습니다 for this case themselves.
        val none = GeoMapLoadState(clientConfigured = false)
        assertFalse(none.loading)
        assertFalse(none.failed)
        assertFalse(none.loadStalled().failed)
        assertFalse(none.authFailure().failed)
    }
}
