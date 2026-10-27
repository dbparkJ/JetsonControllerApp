package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.jetsoncontroller.ui.components.geo.GeoRowTrailing
import com.example.jetsoncontroller.ui.components.geo.GeoInlineNote
import com.example.jetsoncontroller.ui.components.geo.GeoNoteKind
import com.example.jetsoncontroller.ui.components.geo.GeoGroupedSection
import com.example.jetsoncontroller.ui.components.geo.GeoRow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/** Compatibility name; R8 grouped surfaces own no row padding or shadow. */
@Composable
internal fun R7Card(
    modifier: Modifier = Modifier,
    color: Color = LocalGeoColors.current.surface,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().clip(GeoShape.card).background(color, GeoShape.card)
        .padding(contentPadding), content = content)
}

/** Help and ordinary status messages use a neutral surface; only warnings and failures use soft fills. */
@Composable
internal fun R7InlineNote(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.INFO,
    kind: GeoNoteKind = GeoNoteKind.HELP,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val c = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides c.copy(primaryTint = c.surface,
        successSoft = c.surface, pendingSoft = c.surface, neutralSoft = c.surface)) {
        GeoInlineNote(text, modifier, tone, kind, actionLabel, onAction, onDismiss)
    }
}

@Composable
internal fun R7Section(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    danger: Boolean = false,
    count: String? = null,
    note: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingColumn: GeoRowTrailing? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides if (danger) colors.copy(surface = colors.dangerSoft) else colors) {
        GeoGroupedSection(listOfNotNull(title, count).joinToString(" · "), modifier, first,
            trailingColumn ?: GeoRowTrailing.None) {
            if (trailingColumn == null) Column(Modifier.padding(16.dp), content = content) else content()
            note?.let { Text(it, Modifier.padding(16.dp), style = LocalGeoType.current.supporting,
                color = colors.textSecondary) }
            if (actionLabel != null && onAction != null) GeoRow(actionLabel, onAction, divider = false)
        }
    }
}
