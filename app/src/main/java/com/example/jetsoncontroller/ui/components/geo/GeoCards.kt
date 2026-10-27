package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.*

/** Plain 24dp leading icon; the containing row supplies the touch target. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun GeoIconTile(icon: ImageVector, modifier: Modifier = Modifier, selected: Boolean = false,
    tone: Tone? = null, transparent: Boolean = false) {
    val c = LocalGeoColors.current
    Icon(icon, null, modifier.size(24.dp), tint = when {
        tone == Tone.WARNING || tone == Tone.ERROR -> c.tone(tone)
        selected -> c.primaryTintText
        else -> c.textSecondary
    })
}

/** One dark today card per screen, with no action inside its inverse/sunken surface. */
@Composable
fun GeoTodayCard(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    meta: String? = null,
    clock: String? = null,
    status: String? = null,
    statusTone: Tone = Tone.UNKNOWN,
    deadline: String? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val chosen = geoWorkCardColors(LocalGeoPalette.current)
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val tablet = LocalGeoWindow.current.widthDp >= 600f
    val titleStyle = if (tablet) type.tabletWorkTitle else type.workTitle
    val firstLine = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
    val alignment = rememberFirstLineAlignment()
    Column(modifier.fillMaxWidth().clip(GeoShape.card).background(chosen.background)
        .padding(if (tablet) 24.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalGeoColors provides c.copy(text = chosen.content, textSecondary = chosen.content,
            neutral = chosen.content, success = chosen.content, warning = chosen.content,
            danger = chosen.content, primary = chosen.content, pending = chosen.content)) {
            if (label != null || deadline != null) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val labelWidth = with(density) {
                    measurer.measure(AnnotatedString(label.orEmpty()), type.badge, softWrap = false).size.width.toDp().value
                }
                val deadlineWidth = with(density) {
                    measurer.measure(AnnotatedString(deadline.orEmpty()), type.badge, softWrap = false).size.width.toDp().value
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (geoTodayLabelStacks(maxWidth.value, density.fontScale, labelWidth, deadlineWidth)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            label?.let { Text(it, style = type.badge, color = chosen.content) }
                            deadline?.let { Text(it, Modifier.align(Alignment.End), style = type.badge, color = chosen.content) }
                        }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (label != null) Text(label, Modifier.weight(1f), style = type.badge, color = chosen.content)
                        else Spacer(Modifier.weight(1f))
                        deadline?.let { Text(it, style = type.badge, color = chosen.content) }
                    }
                }
            }
            val stacked = LocalDensity.current.fontScale >= 1.3f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(title, Modifier.weight(1f), style = titleStyle, color = chosen.content,
                    onTextLayout = alignment::onTextLayout)
                if (!stacked && status != null) Box(Modifier.alignToFirstTitleLine(alignment).heightIn(min = firstLine),
                    contentAlignment = Alignment.Center) { GeoBadge(status, statusTone, variant = GeoBadgeVariant.INLINE) }
            }
            if (stacked && status != null) GeoBadge(status, statusTone,
                Modifier.align(Alignment.End), variant = GeoBadgeVariant.INLINE)
            meta?.let { Text(it, style = type.supporting, color = chosen.content) }
            clock?.let { Text(it, style = type.clock, color = chosen.content) }
            content()
        }
    }
}

/** Compatibility wrapper: the action is outside the dark card; new pages pin it in their scaffold. */
@Composable
fun GeoWorkCard(title: String, action: GeoButtonAction, modifier: Modifier = Modifier,
    label: String? = null, meta: String? = null, clock: String? = null, status: String? = null,
    statusTone: Tone = Tone.UNKNOWN, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        GeoTodayCard(title, label = label, meta = meta, clock = clock, status = status,
            statusTone = statusTone, content = content)
        GeoFixedActionBar(action)
    }
}

/** Device identity uses a 40dp photo row and a four-cell metric strip on one surface. */
@Composable
fun GeoDevicePhotoCard(name: String, status: String, statusTone: Tone, meta: String,
    figures: List<GeoFigure>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    statusWidth: androidx.compose.ui.unit.Dp = 72.dp) {
    require(figures.size == 4) { "A device photo card requires four figures" }
    Column(modifier.fillMaxWidth().clip(GeoShape.card).background(LocalGeoColors.current.surface)) {
        GeoRowList(GeoRowTrailing.Status) {
            GeoRow(name, onClick ?: {}, meta = meta, navigable = onClick != null, leadingIsPhoto = true,
                leading = { Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp)) },
                badge = { GeoBadge(status, statusTone, minWidth = statusWidth, variant = GeoBadgeVariant.INLINE) },
                divider = false)
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalGeoColors.current.border)
        GeoMetricStrip(figures, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

data class GeoQuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val selected: Boolean = false)

/** Compatibility grid policy; phones use the horizontal tile row in R8. */
fun quickActionColumns(fontScale: Float): Int = when {
    fontScale >= 2f -> 1
    fontScale >= 1.3f -> 2
    else -> 3
}

/** Phone tiles snap at 104dp; tablet quick functions form a 3 by 2 grid. */
@Composable
fun GeoQuickActions(actions: List<GeoQuickAction>, modifier: Modifier = Modifier) {
    if (LocalGeoWindow.current.widthDp < 600f) {
        GeoHorizontalTiles(actions, modifier)
    } else BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = tabletQuickActionColumns(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { GeoQuickTile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

fun tabletQuickActionColumns(widthDp: Float, fontScale: Float): Int = GeoTabletRules.quickColumns(widthDp, fontScale)

/** Use at the full pane width; contentPadding owns the 24dp leading gutter and next-item peek. */
@Composable
fun GeoHorizontalTiles(actions: List<GeoQuickAction>, modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)) {
    val state = rememberLazyListState()
    LazyRow(modifier.fillMaxWidth().testTag("geo-horizontal-tiles"), state = state, contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp), flingBehavior = rememberSnapFlingBehavior(state)) {
        items(actions) { GeoQuickTile(it, Modifier.width(104.dp)) }
    }
}

/** Flat 104 by 96 phone tile, growing vertically for enlarged labels. */
@Composable
fun GeoQuickTile(action: GeoQuickAction, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    Column(modifier.heightIn(min = 96.dp).clip(GeoShape.card)
        .geoClickable(shape = GeoShape.card, background = if (action.selected) c.primaryTint else c.surface,
            onClick = action.onClick).padding(horizontal = 8.dp, vertical = 16.dp)
        .semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Icon(action.icon, null, Modifier.size(24.dp), tint = if (action.selected) c.primaryTintText else c.text)
        Text(action.label.replace('\n', ' '), style = LocalGeoType.current.caption,
            color = if (action.selected) c.primaryTintText else c.text, textAlign = TextAlign.Center)
    }
}
