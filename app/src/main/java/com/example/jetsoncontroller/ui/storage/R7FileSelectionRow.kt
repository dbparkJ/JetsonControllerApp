package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** The selection target and preview target stay separate; all send browsers share this row. */
@Composable
internal fun R7FileSelectionRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean,
    disabledReason: String,
    modifier: Modifier = Modifier,
    divider: Boolean = true
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val alignment = rememberFirstLineAlignment()
    Row(modifier.testTag("file-selection-${entry.relativePath}").fillMaxWidth().heightIn(min = 72.dp)
            .background(if (selected) colors.primaryTint else colors.surface)
            .geoClickable(enabled = enabled, onClick = onOpen)
            .contentDivider(startIndent = 56.dp, enabled = divider)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(24.dp).alignToFirstTitleLine(alignment)) { GeoIconTile(mediaIcon(entry)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(breakableFileName(entry.name), style = type.titleSm,
                color = if (enabled) colors.text else colors.disabledText, onTextLayout = alignment::onTextLayout)
            Text(fileEntryMeta(entry), style = type.supporting, color = colors.textSecondary)
            if (!enabled) Text(disabledReason, style = type.supporting, color = colors.textSecondary)
        }
        Box(Modifier.alignToFirstTitleLine(alignment)) {
            Checkbox(selected, { onSelect() }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "${entry.name} 선택" },
                colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.borderStrong,
                    checkmarkColor = colors.onPrimary, disabledCheckedColor = colors.disabledFill,
                    disabledUncheckedColor = colors.disabledText))
        }
    }
}

internal fun fileEntryMeta(entry: RemoteFileEntry): String = listOfNotNull(
    if (entry.type == com.example.jetsoncontroller.model.RemoteEntryType.DIRECTORY) "폴더" else mediaCategory(entry),
    entry.sizeBytes?.let(Formats::size),
    entry.modifiedAt?.let { localTimeOrDateLabel(it) }
).joinToString(" · ")
