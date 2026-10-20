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
import com.example.jetsoncontroller.ui.components.geo.contentTarget
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
internal fun WorkDeviceSwitcher(name: String, tone: Tone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    val color = when (tone) {
        Tone.SUCCESS -> c.chromeSuccess
        Tone.WARNING -> c.chromeWarning
        Tone.ERROR -> c.chromeDanger
        Tone.INFO -> c.chromeInfo
        Tone.PENDING -> c.chromePending
        Tone.UNKNOWN -> c.chromeNeutral
    }
    Row(modifier.contentTarget(onClick = onClick).heightIn(min = 48.dp)
        .padding(horizontal = 8.dp, vertical = 12.dp).semantics(mergeDescendants = true) {
            contentDescription = "현재 장치 $name, 장치 바꾸기"
        }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(name, Modifier.weight(1f, fill = false), color = c.onTopBar, style = LocalGeoType.current.titleSm)
        Icon(GeoIcons.chevronDown, null, Modifier.size(20.dp), tint = c.onTopBarIcon)
    }
}

/** The choice sheet paints the selected fill edge to edge; the state badge follows the name. */
@Composable
internal fun WorkDeviceChoiceRow(name: String, selected: Boolean, onClick: () -> Unit,
    word: String, tone: Tone, detail: String?) {
    GeoRadioRow(name, selected, onClick, description = detail, badge = { GeoBadge(word, tone) })
}
