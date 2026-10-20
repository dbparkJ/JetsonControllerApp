package com.example.jetsoncontroller.ui.map

import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.tile.TileOperation

class GeoMapLoadStateTest {
    private val tile = GeoMapTileId(1, 2, 3, 0, 3)
    private val otherTile = tile.copy(x = 4)

    @Test fun normalTileLifecycleNeverShowsFailure() {
        var state = GeoMapLoadState().styleLoaded()
        listOf(TileOperation.RequestedFromCache, TileOperation.RequestedFromNetwork,
            TileOperation.LoadFromNetwork, TileOperation.LoadFromCache, TileOperation.StartParse,
            TileOperation.EndParse, TileOperation.Cancelled, TileOperation.NullOp).forEach {
            state = state.tileAction("raster", tile, it)
            assertFalse("$it is not an error", state.failed)
        }
        assertFalse(state.loading)
    }

    @Test fun errorsFromOverlaySourcesDoNotMarkTheBackgroundFailed() {
        listOf("zone", "trail", "device", "phone").forEach {
            assertFalse(GeoMapLoadState().tileAction(it, tile, TileOperation.Error).failed)
        }
    }

    @Test fun lateErrorsFromAPreviousStyleDoNotMarkTheCurrentStyleFailed() {
        val state = GeoMapLoadState().styleLoaded()
        assertFalse(state.tileAction("raster-old", tile, TileOperation.Error, "raster-current").failed)
        val failed = state.tileAction("raster-current", tile, TileOperation.Error, "raster-current")
        assertTrue(failed.failed)
        assertTrue(failed.tileAction("raster-old", tile, TileOperation.EndParse, "raster-current").failed)
    }

    @Test fun actualRasterFailureSurvivesStyleCompletionAndRetryRequest() {
        val failed = GeoMapLoadState().tileAction("raster", tile, TileOperation.Error).styleLoaded()
        assertTrue(failed.failed)
        assertFalse(failed.loading)
        assertTrue(failed.tileAction("raster", tile, TileOperation.RequestedFromNetwork).failed)
    }

    @Test fun recoveredRasterClearsItsOwnFailure() {
        val failed = GeoMapLoadState().styleLoaded().tileAction("raster", tile, TileOperation.Error)
        listOf(TileOperation.LoadFromNetwork, TileOperation.LoadFromCache, TileOperation.EndParse).forEach {
            assertFalse("$it recovers the failed tile", failed.tileAction("raster", tile, it).failed)
        }
    }

    @Test fun unrelatedSuccessfulTileDoesNotHideAnActualFailure() {
        val failed = GeoMapLoadState().styleLoaded().tileAction("raster", tile, TileOperation.Error)
        assertTrue(failed.tileAction("raster", otherTile, TileOperation.EndParse).failed)
        assertTrue(failed.tileAction("trail", tile, TileOperation.EndParse).failed)
    }

    @Test fun cancelledFailedRequestNoLongerReportsFailure() {
        val failed = GeoMapLoadState().styleLoaded().tileAction("raster", tile, TileOperation.Error)
        assertFalse(failed.tileAction("raster", tile, TileOperation.Cancelled).failed)
        assertTrue(failed.tileAction("raster", otherTile, TileOperation.Cancelled).failed)
    }

    @Test fun allFailedTilesMustRecover() {
        val failed = GeoMapLoadState().styleLoaded()
            .tileAction("raster", tile, TileOperation.Error)
            .tileAction("raster", otherTile, TileOperation.Error)
        val partial = failed.tileAction("raster", tile, TileOperation.EndParse)
        assertTrue(partial.failed)
        assertFalse(partial.tileAction("raster", otherTile, TileOperation.EndParse).failed)
    }

    @Test fun styleFailureEndsLoadingAndIsNotClearedByTileSuccess() {
        val failed = GeoMapLoadState().styleError()
        assertFalse(failed.loading)
        assertTrue(failed.failed)
        assertTrue(failed.tileAction("raster", tile, TileOperation.EndParse).failed)
        assertFalse(failed.styleLoaded().failed)
    }

    @Test fun aNewStyleStartsWithoutPreviousFailures() {
        val previous = GeoMapLoadState().styleError().tileAction("raster", tile, TileOperation.Error)
        assertTrue(previous.failed)
        val next = GeoMapLoadState()
        assertTrue(next.loading)
        assertFalse(next.failed)
    }
}
