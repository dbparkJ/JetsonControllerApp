package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** A compact bar appears only after the entire large title has scrolled away. */
fun geoHeaderCollapsed(offsetPx: Float, rangePx: Float): Boolean =
    rangePx > 0f && offsetPx >= rangePx - 0.5f

@Stable
class GeoLargeTitleState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var rangePx by mutableFloatStateOf(0f)
        private set
    val collapsed: Boolean get() = geoHeaderCollapsed(offsetPx, rangePx)

    internal fun updateRange(range: Float) {
        val wasCollapsed = collapsed
        rangePx = range.coerceAtLeast(0f)
        offsetPx = if (wasCollapsed) rangePx else offsetPx.coerceIn(0f, rangePx)
    }

    internal fun restoreOffset(offset: Float) { offsetPx = offset.coerceIn(0f, rangePx) }

    private fun scroll(delta: Float): Float {
        val before = offsetPx
        offsetPx = (offsetPx - delta).coerceIn(0f, rangePx)
        return before - offsetPx
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) Offset(0f, scroll(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f) Offset(0f, scroll(available.y)) else Offset.Zero
    }
}

/** Remember one state per independently scrolling page or pane. */
@Composable
fun rememberGeoLargeTitleState(): GeoLargeTitleState = rememberSaveable(saver = Saver<GeoLargeTitleState, List<Float>>(
    save = { listOf(it.offsetPx, it.rangePx) },
    restore = { values -> GeoLargeTitleState().apply { updateRange(values[1]); restoreOffset(values[0]) } }
)) { GeoLargeTitleState() }

/** Detail tools occupy 48dp; each icon remains a 48dp touch target. */
@Composable
fun GeoDetailToolbar(onBack: () -> Unit, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GeoIconButton(GeoIcons.back, "뒤로", onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Measured 32/40 large heading collapses to a canvas 64dp bar without a white app bar. */
@Composable
fun GeoLargeTitleHeader(
    title: String,
    state: GeoLargeTitleState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val back = onBack?.takeUnless { LocalGeoDetailRoot.current }
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    val stackActions = LocalDensity.current.fontScale >= 1.3f
    SubcomposeLayout(modifier.fillMaxWidth().background(c.canvas).clipToBounds()) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val expanded = subcompose("large") {
            Column(Modifier.then(if (state.collapsed) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (back != null) GeoDetailToolbar(back, actions = actions)
                Column(Modifier.padding(start = gutter, end = gutter, top = 8.dp, bottom = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, style = t.largeTitle,
                            color = c.text, maxLines = 2)
                        if (back == null && !stackActions) Row(content = actions)
                    }
                    if (back == null && stackActions) Row(Modifier.align(Alignment.End), content = actions)
                    subtitle?.let { Text(it, color = c.textSecondary, style = t.supporting) }
                }
            }
        }.single().measure(loose)
        val compact = subcompose("compact") {
            Column(Modifier.then(if (!state.collapsed) Modifier.clearAndSetSemantics {} else Modifier)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("geo-compact-bar")
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }.padding(horizontal = if (back == null) gutter else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (back != null) GeoIconButton(GeoIcons.back, "뒤로", back)
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = t.compactTitle, color = c.text, maxLines = 2)
                    actions()
                }
            }
        }.single().measure(loose)
        val range = (expanded.height - compact.height).coerceAtLeast(0).toFloat()
        val offset = state.offsetPx.coerceIn(0f, range)
        val height = (expanded.height - offset.toInt()).coerceAtLeast(compact.height)
        layout(constraints.maxWidth, height) {
            state.updateRange(range)
            if (state.collapsed) compact.placeRelative(0, 0)
            else expanded.placeRelative(0, -offset.toInt())
        }
    }
}

/** Canvas footer for the page's primary action; the shell owns system insets by default. */
@Composable
fun GeoFixedActionBar(
    primaryAction: GeoButtonAction,
    modifier: Modifier = Modifier,
    secondaryAction: GeoButtonAction? = null,
    systemInset: Boolean = false
) {
    Box(modifier.fillMaxWidth().background(LocalGeoColors.current.canvas)
        .then(if (systemInset) Modifier.navigationBarsPadding() else Modifier)
        .padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp,
            vertical = if (LocalGeoPane.current) 0.dp else 12.dp), contentAlignment = Alignment.Center) {
        GeoButtonActions(listOfNotNull(secondaryAction, primaryAction))
    }
}

/** Root/detail/flow scaffold; content owns a LazyColumn or verticalScroll, with no extra app bar. */
@Composable
fun GeoLargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    primaryAction: GeoButtonAction? = null,
    secondaryAction: GeoButtonAction? = null,
    state: GeoLargeTitleState = rememberGeoLargeTitleState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().background(LocalGeoColors.current.canvas).nestedScroll(state.connection)) {
        GeoLargeTitleHeader(title, state, subtitle = subtitle, onBack = onBack, actions = actions)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        primaryAction?.let { GeoFixedActionBar(it, secondaryAction = secondaryAction) }
    }
}
