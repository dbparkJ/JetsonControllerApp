package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.SlateHarmonyTokens

internal enum class HarmonySectionTone { BASE, SOFT, RAISED, DANGER }

/** Integration reference, NOT a compiled patch. For short groups only.
 * Keep long lists virtualized. The wrapper has no click action or device side effect.
 * DANGER uses a neutral group surface; child destructive actions keep danger colors.
 */
@Composable
internal fun SlateHarmonySection(
    title: String,
    tone: HarmonySectionTone,
    colors: SlateHarmonyTokens,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val background = when (tone) {
        HarmonySectionTone.BASE -> colors.sectionBase
        HarmonySectionTone.SOFT -> colors.sectionSoft
        HarmonySectionTone.RAISED -> colors.sectionRaised
        HarmonySectionTone.DANGER -> colors.sectionDanger
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        contentColor = colors.ink,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}
