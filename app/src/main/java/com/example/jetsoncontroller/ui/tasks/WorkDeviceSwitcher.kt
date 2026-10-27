package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.GeoBadge
import com.example.jetsoncontroller.ui.components.geo.GeoRadioRow
import com.example.jetsoncontroller.ui.components.geo.geoClickable
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
internal fun WorkDeviceSwitcher(name: String, tone: Tone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    val color = when (tone) {
        Tone.SUCCESS -> c.success
        Tone.WARNING -> c.warning
        Tone.ERROR -> c.danger
        Tone.INFO -> c.primary
        Tone.PENDING -> c.pending
        Tone.UNKNOWN -> c.neutral
    }
    Row(modifier.geoClickable(onClick = onClick).heightIn(min = 48.dp)
        .padding(horizontal = 8.dp, vertical = 12.dp).semantics(mergeDescendants = true) {
            contentDescription = "현재 장치 $name, 장치 바꾸기"
        }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(name, Modifier.weight(1f, fill = false), color = c.text, style = LocalGeoType.current.titleSm)
        Icon(GeoIcons.chevronDown, null, Modifier.size(24.dp), tint = c.textSecondary)
    }
}

/** The choice sheet owns the edge-to-edge selection surface; connection remains a separate fact. */
@Composable
internal fun WorkDeviceChoiceRow(name: String, selected: Boolean, onClick: () -> Unit,
    word: String, tone: Tone, detail: String?) {
    GeoRadioRow(name, selected, onClick, description = detail, badge = { GeoBadge(word, tone) })
}
