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
import androidx.compose.ui.graphics.Color
import com.example.jetsoncontroller.ui.components.geo.GeoRowList
import com.example.jetsoncontroller.ui.components.geo.GeoRowTrailing
import com.example.jetsoncontroller.ui.components.geo.GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoInlineNote
import com.example.jetsoncontroller.ui.components.geo.GeoNoteKind
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.AppSpacing
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.geoCardShadow

/** Screen adapter for the R7 card surface; dimensions and drawing come from the shared kit. */
@Composable
internal fun R7Card(
    modifier: Modifier = Modifier,
    color: Color = LocalGeoColors.current.surface,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.cardInset),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().geoCardShadow().background(color, GeoShape.card)
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
    GeoSection(title, modifier, first, danger, count, note, actionLabel, onAction) {
        R7Card(color = if (danger) LocalGeoColors.current.dangerSoft else LocalGeoColors.current.surface,
            contentPadding = if (trailingColumn == null) PaddingValues(AppSpacing.cardInset)
                else PaddingValues(horizontal = AppSpacing.cardInset)) {
            if (trailingColumn == null) content() else GeoRowList(trailingColumn, content = content)
        }
    }
}
