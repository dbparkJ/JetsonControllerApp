package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Root and its list/detail copy share the same saved scroll position in the owning tab. */
val LocalGeoPaneScrollStates = staticCompositionLocalOf<Map<String, LazyListState>> { emptyMap() }

@Composable
fun rememberGeoPaneListState(key: String): LazyListState {
    val fallback = rememberLazyListState()
    val entry = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current as? androidx.navigation.NavBackStackEntry
    val owner = entry?.destination?.parent?.route
    return LocalGeoPaneScrollStates.current["$owner:$key"] ?: fallback
}
