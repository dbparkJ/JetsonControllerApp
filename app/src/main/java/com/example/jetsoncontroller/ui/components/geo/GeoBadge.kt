package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.StatusTone

enum class GeoBadgeVariant { SOFT, ON_MAP, ON_DARK }

/** Accepts a status word, never a timestamp or an action. */
@Composable
fun GeoBadge(
    word: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    variant: GeoBadgeVariant = GeoBadgeVariant.SOFT,
    highContrast: Boolean = LocalContentHighContrast.current
) {
    val colors = contentColors
    val ink = if (variant == GeoBadgeVariant.ON_DARK) colors.onMedia(tone) else colors.tone(tone)
    val fill = when (variant) {
        GeoBadgeVariant.SOFT -> colors.soft(tone)
        GeoBadgeVariant.ON_MAP -> colors.surface
        GeoBadgeVariant.ON_DARK -> colors.mediaPlate
    }
    val shape = RoundedCornerShape(50)
    Row(modifier
        .then(if (variant == GeoBadgeVariant.ON_MAP) Modifier.shadow(1.dp, shape) else Modifier)
        .background(fill, shape)
        .then(if (highContrast) Modifier.border(1.dp, ink, shape) else Modifier)
        .clearAndSetSemantics { stateDescription = word }
        .heightIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(ink, CircleShape))
        Text(word, color = ink, style = ContentType.caption.copy(fontWeight = FontWeight.SemiBold))
    }
}
