package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone

enum class GeoNoteKind { HELP, LIVE_ERROR }

/** The caller keeps a help dismissal for this session; live errors cannot be dismissed. */
@Composable
fun GeoInlineNote(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.INFO,
    kind: GeoNoteKind = GeoNoteKind.HELP,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = LocalGeoColors.current
    Row(modifier.fillMaxWidth().background(colors.soft(tone), GeoShape.card)
        .then(if (kind == GeoNoteKind.LIVE_ERROR) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GeoToneIcon(tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = colors.text, style = LocalGeoType.current.body)
            if (actionLabel != null && onAction != null) GeoButton(actionLabel, onAction, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
            if (kind == GeoNoteKind.HELP && onDismiss != null) GeoButton("닫기", onDismiss, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
        }
    }
}
