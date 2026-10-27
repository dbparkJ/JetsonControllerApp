package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The body places inlineAction last in its own scroll container (including LazyColumn). */
@Composable
fun GeoActionArea(
    visibilityKey: Any,
    modifier: Modifier = Modifier,
    scene: Boolean = false,
    chrome: GeoChromeMeasurements = LocalGeoChrome.current,
    action: @Composable () -> Unit,
    visible: Boolean = true,
    body: @Composable (inlineAction: @Composable () -> Unit) -> Unit
) {
    key(visibilityKey) {
        val density by rememberUpdatedState(LocalDensity.current)
        var largestDp by remember { mutableFloatStateOf(0f) }
        val latestAction = rememberUpdatedState(action)
        val colors by rememberUpdatedState(LocalGeoColors.current)
        val gutter by rememberUpdatedState(if (LocalGeoPane.current) 0.dp else 24.dp)
        val sticky = stickyAction(chrome.viewportDp, chrome.topDp, chrome.bottomDp, chrome.imeDp,
            largestDp, scene, LocalGeoWindow.current.compactHeight, inlineForIme = LocalGeoWindow.current.widthDp >= 600f)
        val actionContent = remember {
            movableContentOf {
                Surface(color = colors.canvas) {
                    Box(Modifier.fillMaxWidth().heightIn(min = largestDp.dp)
                        .onSizeChanged { largestDp = retainedActionHeight(largestDp, it.height / density.density) }
                        .padding(horizontal = gutter, vertical = 12.dp)) {
                        latestAction.value()
                    }
                }
            }
        }
        Column(modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { body(if (!visible || sticky) ({}) else actionContent) }
            if (visible && sticky) actionContent()
        }
    }
}
