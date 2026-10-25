package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.*

/** Decorative tile; a clickable parent must supply a target of at least 48dp. */
@Composable
fun GeoIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: Tone? = null
) {
    val c = LocalGeoColors.current
    val alert = tone == Tone.WARNING || tone == Tone.ERROR
    Box(modifier.size(40.dp).background(when {
        alert -> c.soft(tone!!)
        selected -> c.primaryTint
        else -> c.sunken
    }, GeoShape.iconTile), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = when {
            alert -> c.tone(tone!!)
            selected -> c.primaryTintText
            else -> c.textSecondary
        })
    }
}

/** Same frame for assignment waiting and collection in progress. At most one per screen. */
@Composable
fun GeoWorkCard(
    title: String,
    action: GeoButtonAction,
    modifier: Modifier = Modifier,
    label: String? = null,
    meta: String? = null,
    clock: String? = null,
    status: String? = null,
    statusTone: Tone = Tone.UNKNOWN,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val palette = LocalGeoPalette.current
    val chosen = geoWorkCardColors(palette)
    val type = LocalGeoType.current
    val c = LocalGeoColors.current
    val lineHeight = with(LocalDensity.current) { type.workTitle.lineHeight.toDp() }
    Column(modifier.fillMaxWidth().geoCardShadow().background(chosen.background, GeoShape.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalGeoColors provides c.copy(text = chosen.content, textSecondary = chosen.content,
            neutral = chosen.content, success = chosen.content, warning = chosen.content,
            danger = chosen.content, primary = chosen.content, pending = chosen.content)) {
            label?.let { Text(it, style = type.caption, color = chosen.content) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(title, Modifier.weight(1f), style = type.workTitle, color = chosen.content)
                status?.let {
                    Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                        GeoBadge(it, statusTone, variant = GeoBadgeVariant.INLINE)
                    }
                }
            }
            meta?.let { Text(it, style = type.body, color = chosen.content) }
            clock?.let { Text(it, style = type.clock, color = chosen.content) }
            content()
        }
        GeoButton(action.label, action.onClick, Modifier.fillMaxWidth(), action.style,
            action.enabled, action.busy, action.busyLabel, action.icon, action.disabledReason,
            actionArea = true,
            contentColor = if (action.style == GeoButtonStyle.Primary) chosen.onAction else null,
            containerColor = if (action.style == GeoButtonStyle.Primary) chosen.action else null)
    }
}

/** Photo, heading and metadata form one identity; the four figures belong to this device. */
@Composable
fun GeoDevicePhotoCard(
    name: String,
    status: String,
    statusTone: Tone,
    meta: String,
    figures: List<GeoFigure>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    statusWidth: androidx.compose.ui.unit.Dp = 64.dp
) {
    require(figures.size == 4) { "A device photo card requires four figures" }
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val line = with(LocalDensity.current) { type.deviceName.lineHeight.toDp() }
    Column(modifier.fillMaxWidth().geoCardShadow().clip(GeoShape.card).background(c.surface)
        .then(if (onClick == null) Modifier else Modifier.contentTarget(onClick = onClick))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Image(painterResource(R.drawable.geo_device), null, Modifier.size(72.dp, 64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(name, Modifier.weight(1f), style = type.deviceName, color = c.text)
                    Box(Modifier.height(line), contentAlignment = Alignment.Center) {
                        GeoBadge(status, statusTone, Modifier.width(statusWidth), minWidth = statusWidth)
                    }
                }
                Text(meta, style = type.supporting, color = c.textSecondary)
            }
        }
        GeoFigureGrid(figures)
    }
}

data class GeoQuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val selected: Boolean = false
)

fun quickActionColumns(fontScale: Float): Int = when {
    fontScale >= 2f -> 1
    fontScale >= 1.5f -> 2
    else -> 5
}

@Composable
fun GeoQuickActions(actions: List<GeoQuickAction>, modifier: Modifier = Modifier) {
    val columns = quickActionColumns(LocalDensity.current.fontScale)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action -> GeoQuickTile(action, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun GeoQuickTile(action: GeoQuickAction, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    Column(modifier.widthIn(min = 48.dp).heightIn(min = 88.dp)
        .contentTarget(onClick = action.onClick)
        .semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).geoCardShadow(GeoShape.iconTile)
            .background(if (action.selected) c.primaryTint else c.surface, GeoShape.iconTile),
            contentAlignment = Alignment.Center) {
            Icon(action.icon, null, Modifier.size(24.dp),
                tint = if (action.selected) c.primaryTintText else c.textSecondary)
        }
        Text(action.label, style = LocalGeoType.current.caption, color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
