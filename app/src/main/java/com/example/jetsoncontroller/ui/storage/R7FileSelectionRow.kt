package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/** The selection target and preview target stay separate; all send browsers share this row. */
@Composable
internal fun R7FileSelectionRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean,
    disabledReason: String,
    modifier: Modifier = Modifier
) {
    val c = LocalGeoColors.current
    var focused by remember { mutableStateOf(false) }
    R7Card(modifier.then(if (selected) Modifier.border(1.dp, c.primary, GeoShape.card) else Modifier),
        color = if (selected) c.primaryTint else c.surface,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).onFocusChanged { focused = it.isFocused }
                .geoFocusRing(GeoShape.control, focused)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .semantics { contentDescription = "${entry.name} 선택" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    val color = if (!enabled) c.disabledText else if (selected) c.primary else c.borderStrong
                    drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                    if (selected) drawCircle(color, radius = 5.dp.toPx())
                }
            }
            GeoRow(breakableFileName(entry.name), onOpen, Modifier.weight(1f),
                meta = listOfNotNull(entry.sizeBytes?.let(Formats::size), entry.modifiedAt?.let { localTimeOrDateLabel(it) }).joinToString(" · "),
                leading = { GeoIconTile(mediaIcon(entry)) }, divider = false,
                enabled = enabled, disabledReason = disabledReason)
        }
    }
}
