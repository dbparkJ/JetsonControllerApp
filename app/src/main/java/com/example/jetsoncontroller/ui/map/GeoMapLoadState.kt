package com.example.jetsoncontroller.ui.map

/**
 * The base map counts as loaded once NAVER reports its first load. Until then a stalled load (no
 * network and nothing cached) or a rejected client ID is a failure; the overlays still draw.
 * Without a client ID there is no base map to wait for, and the screens say so themselves.
 */
internal data class GeoMapLoadState(
    val clientConfigured: Boolean = true,
    val loaded: Boolean = false,
    val stalled: Boolean = false,
    val authFailed: Boolean = false
) {
    val failed: Boolean get() = clientConfigured && (authFailed || (stalled && !loaded))
    val loading: Boolean get() = clientConfigured && !loaded && !failed

    fun mapLoaded() = copy(loaded = true, stalled = false)
    fun loadStalled() = if (loaded) this else copy(stalled = true)
    fun authFailure() = copy(authFailed = true)
}

/** How long a visible map may wait for NAVER's first load before the screens say the base map did not load. */
internal const val MapLoadStallMillis = 15_000L
