package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

internal data class PaneListRowCorners(val roundTop: Boolean, val roundBottom: Boolean)

/** Only the ends of the containing card have rounded corners. */
internal fun paneListRowCorners(index: Int, rowCount: Int): PaneListRowCorners {
    require(rowCount > 0 && index in 0 until rowCount)
    return PaneListRowCorners(roundTop = index == 0, roundBottom = index == rowCount - 1)
}

/** A boundary touching either selected row has no divider. */
internal fun paneListRowDivider(index: Int, rowCount: Int, selected: Boolean, nextSelected: Boolean): Boolean =
    index < rowCount - 1 && !selected && !nextSelected

/** The card supplies zero padding; the original row inset lives inside the selection fill. */
@Composable
internal fun GeoPaneListRow(
    title: String,
    onClick: () -> Unit,
    selected: Boolean,
    index: Int,
    rowCount: Int,
    nextSelected: Boolean,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val corners = paneListRowCorners(index, rowCount)
    val square = CornerSize(0.dp)
    val card = GeoShape.card
    val shape = card.copy(
        topStart = if (corners.roundTop) card.topStart else square,
        topEnd = if (corners.roundTop) card.topEnd else square,
        bottomStart = if (corners.roundBottom) card.bottomStart else square,
        bottomEnd = if (corners.roundBottom) card.bottomEnd else square
    )
    Box(modifier.fillMaxWidth().clip(shape)
        .background(if (selected) LocalGeoColors.current.primaryTint else Color.Transparent)) {
        GeoRow(title, onClick,
            modifier = Modifier.semantics { this.selected = selected },
            meta = meta, leading = leading, badge = badge,
            divider = paneListRowDivider(index, rowCount, selected, nextSelected))
    }
}
