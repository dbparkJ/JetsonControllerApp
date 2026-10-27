package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.example.jetsoncontroller.ui.theme.*

/** Group heading is inset 16dp and sits 8dp above a zero-padding surface. */
@Composable
fun GeoGroupedSection(title: String, modifier: Modifier = Modifier, first: Boolean = false,
    trailingColumn: GeoRowTrailing = GeoRowTrailing.None, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = if (first) 0.dp else 24.dp)) {
        Text(title, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
            style = LocalGeoType.current.groupHeading, color = LocalGeoColors.current.textSecondary)
        GeoRowList(trailingColumn, content = content)
    }
}

/** Selectable content value; the separate 48dp copy control never selects its label. */
@Composable
fun GeoCopyableValue(value: String, modifier: Modifier = Modifier, label: String? = null,
    showCopyButton: Boolean = true, onCopied: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val alignment = rememberFirstLineAlignment()
    val style = LocalGeoType.current.body
    val density = LocalDensity.current
    var labelHeight by remember(label) { mutableIntStateOf(0) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            label?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary,
                onTextLayout = { result -> labelHeight = result.size.height }) }
            SelectionContainer {
                Text(geoBreakableValue(value), style = style, color = LocalGeoColors.current.text,
                    onTextLayout = alignment::onTextLayout)
            }
        }
        if (showCopyButton) DisableSelection {
            Box(Modifier.offset(y = with(density) { labelHeight.toDp() }).alignToFirstTitleLine(alignment)) {
            GeoIconButton(GeoIcons.copy, if (label == null) "값 복사" else "$label 복사", {
                clipboard.setText(AnnotatedString(value))
                onCopied()
            })
            }
        }
    }
}

/** Scrolling filter row with a 24dp gutter; selection never inserts a check or changes width. */
@Composable
fun GeoFilterChipRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)) {
    require(options.isEmpty() || selectedIndex in options.indices)
    LazyRow(modifier.fillMaxWidth().selectableGroup(), contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { index, label -> GeoFilterChip(label, index == selectedIndex, { onSelect(index) }) }
    }
}

/** Secondary page tabs use a 48dp rectangular target and an underline, never a filter pill. */
@Composable
fun GeoTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    LazyRow(modifier.fillMaxWidth().selectableGroup(), contentPadding = PaddingValues(horizontal = 24.dp)) {
        itemsIndexed(options) { index, label ->
            Box(Modifier.heightIn(min = 48.dp).geoSelectable(index == selectedIndex) { onSelect(index) }
                .drawBehind { if (index == selectedIndex) drawLine(c.primary,
                    Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
                .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = LocalGeoType.current.label,
                    color = if (index == selectedIndex) c.primary else c.textSecondary)
            }
        }
    }
}
