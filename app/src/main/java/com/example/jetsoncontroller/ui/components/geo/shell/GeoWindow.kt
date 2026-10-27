package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val LocalGeoWindow = staticCompositionLocalOf { geoWindow(412f, 882f, 1f) }

val LocalGeoPane = staticCompositionLocalOf { false }

/** Only the detail root loses its back arrow; pushed pages keep normal navigation. */
val LocalGeoDetailRoot = staticCompositionLocalOf { false }

/** Owns the outer gutter once. Child pages retain their own scroll and action area. */
@Composable
fun GeoPaneLayout(
    modifier: Modifier = Modifier,
    policy: GeoPanePolicy = GeoPanePolicy.LIST_DETAIL,
    singlePane: GeoPaneSelection = GeoPaneSelection.BOTH,
    detailFill: Boolean = false,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val window = LocalGeoWindow.current
    val widths = geoPaneWidths(window, policy)
    val visiblePanes = geoVisiblePanes(widths != null, singlePane)
    var hadTwoPanes by rememberSaveable { mutableStateOf(false) }
    var previousListWidth by rememberSaveable { mutableFloatStateOf(400f) }
    if (widths != null) SideEffect { hadTwoPanes = true; previousListWidth = widths.listDp }
    val currentList by rememberUpdatedState(list)
    val currentDetail by rememberUpdatedState(detail)
    val listContent = remember { movableContentOf { currentList() } }
    val detailContent = remember { movableContentOf { currentDetail() } }
    if (widths != null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 1560.dp).fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GeoPane(Modifier.width(widths.listDp.dp).fillMaxHeight(), listContent)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    CompositionLocalProvider(LocalGeoDetailRoot provides true) {
                        GeoPane((if (detailFill || window.landscapeLayout) Modifier else Modifier.widthIn(max = 720.dp)).fillMaxSize(), detailContent)
                    }
                }
            }
        }
    } else {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                if (GeoPaneSelection.LIST in visiblePanes) listContent()
                if (GeoPaneSelection.DETAIL in visiblePanes) detailContent()
            }
            // A confirmation opened in the list is still a window-level overlay after narrowing.
            // Keep its movable owner and saved scroll alive without a visible or touchable list.
            if (singlePane == GeoPaneSelection.DETAIL && hadTwoPanes) {
                Box(Modifier.size(0.dp).clipToBounds().clearAndSetSemantics {}) {
                    GeoPane(Modifier.requiredSize(previousListWidth.dp, window.heightDp.dp), listContent)
                }
            }
        }
    }
}

@Composable
private fun GeoPane(modifier: Modifier, content: @Composable () -> Unit) {
    val window = LocalGeoWindow.current
    val chrome = LocalGeoChrome.current
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(
            LocalGeoPane provides true,
            LocalGeoWindow provides window.copy(paneWidthDp = maxWidth.value, gutterDp = 0f, twoPane = false),
            LocalGeoChrome provides GeoChromeMeasurements(maxHeight.value, imeDp = chrome.imeDp)
        ) { content() }
    }
}
