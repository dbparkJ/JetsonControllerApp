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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** A compact bar appears only after the entire large title has scrolled away. */
fun geoHeaderCollapsed(offsetPx: Float, rangePx: Float): Boolean =
    rangePx > 0f && offsetPx >= rangePx - 0.5f

/** Title and controls are measured at the current font scale before choosing a line. */
@Suppress("UNUSED_PARAMETER")
internal fun geoHeaderActionsStack(widthDp: Float, fontScale: Float, actionsWidthDp: Float, titleWidthDp: Float = 0f): Boolean =
    actionsWidthDp > 0f && titleWidthDp + 8f + actionsWidthDp > widthDp

internal fun geoHeaderTitleTrailingStacks(widthDp: Float, fontScale: Float, titleWidthDp: Float, trailingWidthDp: Float): Boolean =
    titleWidthDp + 12f + trailingWidthDp > widthDp

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
    GeoDetailTools(onBack, modifier, actions)
}

@Composable
private fun GeoDetailTools(onBack: (() -> Unit)?, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = if (LocalGeoPane.current) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) GeoIconButton(GeoIcons.back, "뒤로", onBack)
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
    titleTrailingContent: (@Composable () -> Unit)? = null,
    subtitleStatus: GeoStatusSpec? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val back = onBack?.takeUnless { LocalGeoDetailRoot.current }
    val detail = onBack != null || LocalGeoDetailRoot.current
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleWidth = measurer.measure(AnnotatedString(title), t.largeTitle, softWrap = false).size.width
    SubcomposeLayout(modifier.fillMaxWidth().background(c.canvas).clipToBounds()) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val availableWidth = (constraints.maxWidth - (gutter * 2).roundToPx()).coerceAtLeast(0)
        val actionWidth = subcompose("action-measure") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }.single().measure(Constraints(maxWidth = availableWidth)).width
        val actionModifier = Modifier
        val showTools = detail && (back != null || actionWidth > 0)
        val stackActions = !detail && geoHeaderActionsStack(availableWidth.toDp().value,
            density.fontScale, actionWidth.toDp().value, titleWidth.toDp().value)
        val expanded = subcompose("large") {
            Column(Modifier.then(if (state.collapsed) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (showTools) GeoDetailTools(back, actions = {
                    Row(actionModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                })
                Column(Modifier.padding(start = gutter, end = gutter, top = 8.dp, bottom = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeoLargeTitleBlock(title, subtitle, titleTrailingContent, Modifier.weight(1f), subtitleStatus)
                        if (!detail && !stackActions) Row(actionModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                    }
                    if (stackActions) Row(actionModifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                }
            }
        }.single().measure(loose)
        val compact = subcompose("compact") {
            Column(Modifier.fillMaxWidth().testTag("geo-compact-bar")
                .then(if (!state.collapsed) Modifier.clearAndSetSemantics {} else Modifier)
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .padding(horizontal = if (back == null) gutter else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (back != null) GeoIconButton(GeoIcons.back, "뒤로", back)
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = t.compactTitle, color = c.text, maxLines = 2)
                    if (!stackActions) Row(actionModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                }
                if (stackActions) Row(Modifier.align(Alignment.End).padding(horizontal = gutter).then(actionModifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
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

/** Measure the photo independently so the title keeps its full width when the pair cannot fit. */
@Composable
private fun GeoLargeTitleBlock(title: String, subtitle: String?, titleTrailingContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier, subtitleStatus: GeoStatusSpec? = null) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleWidthPx = measurer.measure(AnnotatedString(title), t.largeTitle, softWrap = false).size.width
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val trailing = titleTrailingContent?.let { content ->
            subcompose("title-photo") { Box { content() } }.single().measure(loose)
        }
        val stacked = trailing != null && geoHeaderTitleTrailingStacks(
            constraints.maxWidth.toDp().value, density.fontScale, titleWidthPx.toDp().value, trailing.width.toDp().value)
        val gap = if (trailing == null) 0 else 12.dp.roundToPx()
        val textWidth = if (stacked) constraints.maxWidth else (constraints.maxWidth - (trailing?.width ?: 0) - gap).coerceAtLeast(0)
        val text = subcompose("title-text") {
            Column {
                Text(title, Modifier.semantics { heading() }, style = t.largeTitle, color = c.text, maxLines = 2)
                GeoSubtitleLine(subtitle, subtitleStatus)
            }
        }.single().measure(loose.copy(maxWidth = textWidth))
        val height = if (stacked) text.height + gap + trailing!!.height else maxOf(text.height, trailing?.height ?: 0)
        layout(constraints.maxWidth, height) {
            text.placeRelative(0, if (stacked) 0 else (height - text.height) / 2)
            trailing?.placeRelative(constraints.maxWidth - trailing.width,
                if (stacked) text.height + gap else (height - trailing.height) / 2)
        }
    }
}

/** Canvas footer for the page's primary action; the shell owns system insets by default. */
@Composable
fun GeoFixedActionBar(
    primaryAction: GeoButtonAction,
    modifier: Modifier = Modifier,
    secondaryAction: GeoButtonAction? = null,
    systemInset: Boolean = false,
    note: (@Composable () -> Unit)? = null
) {
    Box(modifier.fillMaxWidth().background(LocalGeoColors.current.canvas)
        .then(if (systemInset) Modifier.navigationBarsPadding() else Modifier)
        .padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp,
            vertical = 12.dp), contentAlignment = Alignment.Center) {
        GeoButtonActions(listOfNotNull(secondaryAction, primaryAction), note = note)
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
    titleTrailingContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    subtitleStatus: GeoStatusSpec? = null,
    note: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().background(LocalGeoColors.current.canvas).nestedScroll(state.connection)) {
        GeoLargeTitleHeader(title, state, subtitle = subtitle, onBack = onBack,
            titleTrailingContent = titleTrailingContent, actions = actions, subtitleStatus = subtitleStatus)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        primaryAction?.let { GeoFixedActionBar(it, secondaryAction = secondaryAction, note = note) }
    }
}

data class GeoStatusSpec(val word: String, val tone: com.example.jetsoncontroller.ui.model.Tone)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeoSubtitleLine(subtitle: String?, status: GeoStatusSpec?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        status?.let { GeoBadge(it.word, it.tone) }
        subtitle?.let {
            Text(if (status == null) it else "· $it", color = LocalGeoColors.current.textSecondary,
                style = LocalGeoType.current.supporting)
        }
    }
}
