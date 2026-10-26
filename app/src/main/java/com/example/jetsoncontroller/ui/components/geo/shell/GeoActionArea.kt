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
        val gutter by rememberUpdatedState(if (LocalGeoPane.current) 16.dp else LocalGeoWindow.current.gutterDp.dp)
        val sticky = stickyAction(chrome.viewportDp, chrome.topDp, chrome.bottomDp, chrome.imeDp,
            largestDp, scene, LocalGeoWindow.current.compactHeight, inlineForIme = LocalGeoWindow.current.widthDp >= 600f)
        // KIT §3.12: a pinned action area carries a 1 dp border hairline on top; inline it has none.
        val pinned by rememberUpdatedState(sticky)
        val actionContent = remember {
            movableContentOf {
                Surface(color = colors.surface) {
                    Box(Modifier.fillMaxWidth().heightIn(min = largestDp.dp)
                        .onSizeChanged { largestDp = retainedActionHeight(largestDp, it.height / density.density) }
                        .drawBehind { if (pinned) drawRect(colors.border, size = Size(size.width, 1.dp.toPx())) }
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
