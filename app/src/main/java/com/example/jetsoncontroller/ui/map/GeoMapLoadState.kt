package com.example.jetsoncontroller.ui.map

import org.maplibre.android.tile.TileOperation

internal data class GeoMapTileId(val x: Int, val y: Int, val z: Int, val wrap: Int, val overscaledZ: Int)

/** A failed request is no longer a map failure once that tile recovers or leaves the viewport. */
internal data class GeoMapLoadState(
    val loading: Boolean = true,
    val styleFailed: Boolean = false,
    val failedTiles: Set<GeoMapTileId> = emptySet()
) {
    val failed: Boolean get() = styleFailed || failedTiles.isNotEmpty()

    fun styleLoaded() = copy(loading = false, styleFailed = false)
    fun styleError() = copy(loading = false, styleFailed = true)

    fun tileAction(source: String, tile: GeoMapTileId, operation: TileOperation, rasterSource: String = "raster"): GeoMapLoadState {
        if (source != rasterSource) return this
        return when (operation) {
            TileOperation.Error -> copy(failedTiles = failedTiles + tile)
            TileOperation.EndParse, TileOperation.LoadFromCache, TileOperation.LoadFromNetwork,
            TileOperation.Cancelled -> copy(failedTiles = failedTiles - tile)
            else -> this
        }
    }
}
