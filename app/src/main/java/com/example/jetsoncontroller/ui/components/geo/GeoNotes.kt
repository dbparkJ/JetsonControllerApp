package com.example.jetsoncontroller.ui.components.geo

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
import com.example.jetsoncontroller.ui.components.StatusTone

enum class GeoNoteKind { HELP, LIVE_ERROR }

/** The caller keeps a help dismissal for this session; live errors cannot be dismissed. */
@Composable
fun GeoInlineNote(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.INFO,
    kind: GeoNoteKind = GeoNoteKind.HELP,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = contentColors
    Row(modifier.fillMaxWidth().background(colors.soft(tone), RoundedCornerShape(12.dp))
        .then(if (kind == GeoNoteKind.LIVE_ERROR) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NoteIcon(tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = colors.text, style = ContentType.body)
            if (actionLabel != null && onAction != null) NoteAction(actionLabel, onAction)
            if (kind == GeoNoteKind.HELP && onDismiss != null) NoteAction("닫기", onDismiss)
        }
    }
}

// TODO(3-B merge): replace with the shared tertiary button and status icon.
@Composable
private fun NoteAction(label: String, onClick: () -> Unit) {
    Box(Modifier.contentTarget(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(label, color = contentColors.primary, style = ContentType.button)
    }
}

@Composable
private fun NoteIcon(tone: StatusTone) {
    val color = contentColors.tone(tone)
    Canvas(Modifier.size(20.dp)) {
        drawCircle(color, style = Stroke(1.75.dp.toPx()))
        val center = size.width / 2
        if (tone == StatusTone.SUCCESS) {
            drawLine(color, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .43f, size.height * .68f),
                1.75.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .43f, size.height * .68f), Offset(size.width * .76f, size.height * .33f),
                1.75.dp.toPx(), StrokeCap.Round)
        } else {
            val info = tone == StatusTone.INFO
            drawCircle(color, 1.dp.toPx(), Offset(center, size.height * if (info) .3f else .75f))
            drawLine(color, Offset(center, size.height * if (info) .45f else .25f),
                Offset(center, size.height * if (info) .75f else .55f), 1.75.dp.toPx(), StrokeCap.Round)
        }
    }
}
