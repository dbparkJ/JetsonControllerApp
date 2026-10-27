package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
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
    val colors = LocalGeoColors.current
    R7Card(modifier.testTag("file-selection-${entry.relativePath}"), color = if (selected) colors.primaryTint else colors.surface, contentPadding = PaddingValues(0.dp)) {
        GeoCopyableValue(entry.name, Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GeoCheckboxRow("선택", selected, { onSelect() }, Modifier.weight(1f),
                description = listOfNotNull(entry.sizeBytes?.let(Formats::size),
                    entry.modifiedAt?.let { localTimeOrDateLabel(it) }).joinToString(" · "),
                enabled = enabled, disabledReason = disabledReason)
            GeoIconButton(mediaIcon(entry), "${entry.name} 미리보기", onOpen, enabled = enabled)
        }
    }
}
