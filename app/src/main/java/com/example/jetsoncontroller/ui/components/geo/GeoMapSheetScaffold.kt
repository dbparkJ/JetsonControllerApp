package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.*

/** Non-interactive 32 by 4dp handle; the sheet container owns drag and accessibility actions. */
@Composable
fun GeoSheetDragHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 4.dp).background(LocalGeoColors.current.borderStrong, GeoShape.pill))
    }
}

/** Full map and draggable collection sheet; the stop action remains visible at either sheet position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMapSheetScaffold(
    title: String,
    primaryAction: GeoButtonAction,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 452.dp,
    controls: @Composable BoxScope.() -> Unit = {},
    map: @Composable BoxScope.() -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit
) {
    if (!com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane.current) {
        GeoSystemBars(navigationBarUsesSurface = true)
    }
    val c = LocalGeoColors.current
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(80.dp) }
    BoxWithConstraints(modifier.fillMaxSize().semantics { paneTitle = title }) {
        val sheetHeight = maxHeight * 0.85f
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            sheetPeekHeight = peekHeight.coerceAtMost(sheetHeight),
            sheetShape = GeoShape.sheetTop,
            sheetContainerColor = c.surface, sheetContentColor = c.text,
            sheetTonalElevation = 0.dp, sheetShadowElevation = 0.dp,
            sheetDragHandle = { GeoSheetDragHandle() },
            containerColor = c.canvas,
            sheetContent = {
                Column(Modifier.height((sheetHeight - 28.dp).coerceAtLeast(0.dp))
                    .geoContainScroll().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = footerHeight), content = sheetContent)
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                map()
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), content = controls)
            }
        }
        // Outside the moving sheet so its primary action never slides below the viewport.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.surface)
            .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
            .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            GeoButtonActions(listOf(primaryAction))
        }
    }
}
