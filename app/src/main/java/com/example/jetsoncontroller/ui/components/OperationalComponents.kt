package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoRadius
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

// StatusTone은 Compose를 참조하지 않는 StatusTone.kt로 옮겼습니다. 같은 패키지라
// 호출부는 그대로입니다. 순수 로직(FieldStage 등)이 상태 어휘를 쓰면서도 UI 없이
// 단위 테스트될 수 있어야 하기 때문입니다.

/**
 * Colour plus icon plus shape for one tone.
 *
 * Every consumer takes all three. Colour alone would fail the accessibility bar the
 * brief sets (색 + 아이콘 또는 형태 + 문구 함께 사용), and it also fails outdoors in
 * direct sunlight where hue separation collapses long before shape does.
 */
@Immutable
data class GeoToneVisuals(
    val container: Color,
    val content: Color,
    val border: Color,
    val icon: ImageVector
)

@Composable
fun statusVisuals(tone: StatusTone): GeoToneVisuals {
    val c = LocalGeoColors.current
    return when (tone) {
        StatusTone.SUCCESS -> GeoToneVisuals(c.successBg, c.success, c.successBorder, Icons.Default.CheckCircle)
        StatusTone.WARNING -> GeoToneVisuals(c.warningBg, c.warning, c.warningBorder, Icons.Default.WarningAmber)
        StatusTone.ERROR -> GeoToneVisuals(c.dangerBg, c.danger, c.dangerBorder, Icons.Default.ErrorOutline)
        StatusTone.INFO -> GeoToneVisuals(c.infoBg, c.info, c.infoBorder, Icons.Default.Info)
        StatusTone.PENDING -> GeoToneVisuals(c.pendingBg, c.pending, c.pendingBorder, Icons.Default.HourglassEmpty)
        StatusTone.UNKNOWN -> GeoToneVisuals(c.unknownBg, c.unknown, c.unknownBorder, Icons.AutoMirrored.Filled.HelpOutline)
    }
}

/**
 * An inline message attached to the thing it is about.
 *
 * Deliberately not a toast: the brief requires that a problem needing a decision stays
 * on screen with its explanation and its next action, rather than disappearing after
 * three seconds.
 */
@Composable
fun AppBanner(
    message: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val visuals = statusVisuals(tone)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = visuals.container,
        contentColor = visuals.content,
        border = BorderStroke(GeoSize.hairline, visuals.border),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(
                start = GeoSpace.md,
                top = GeoSpace.md,
                end = if (onDismiss != null) GeoSpace.xs else GeoSpace.md,
                bottom = GeoSpace.md
            ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Icon(visuals.icon, contentDescription = null, modifier = Modifier.size(GeoSize.iconMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(GeoSize.minTouchTarget)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "메시지 닫기",
                        modifier = Modifier.size(GeoSize.iconSm)
                    )
                }
            }
        }
    }
}

/**
 * A compact status label. Always colour + dot + text, so that the meaning survives a
 * greyscale screenshot, a colour-blind reader and a sunlit screen.
 */
@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val visuals = statusVisuals(tone)
    Surface(
        modifier = modifier,
        color = visuals.container,
        contentColor = visuals.content,
        border = BorderStroke(GeoSize.hairline, visuals.border),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(GeoRadius.xs)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GeoSpace.sm, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(GeoSize.statusDot)
                    .background(visuals.content, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Progress through a fixed, ordered sequence (등록 → 연결 → 준비 → 수집).
 *
 * Steps ahead of the cursor are rendered as unknown rather than as info, because the
 * user has not yet established anything about them.
 */
@Composable
fun ConnectionStepper(
    labels: List<String>,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val c = LocalGeoColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        labels.forEachIndexed { index, label ->
            val completed = index < currentStep
            val selected = index == currentStep

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    color = when {
                        completed -> c.success
                        selected -> c.primary
                        else -> c.unknownBg
                    },
                    contentColor = when {
                        completed -> c.onSuccess
                        selected -> c.onPrimary
                        else -> c.unknown
                    },
                    border = if (completed || selected) null else BorderStroke(GeoSize.hairline, c.unknownBorder),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (completed) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(GeoSize.iconSm)
                            )
                        } else {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(GeoSpace.sm))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) c.ink else c.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (index < labels.lastIndex) {
                Surface(
                    modifier = Modifier
                        .padding(top = 15.dp)
                        .size(width = 12.dp, height = 2.dp),
                    color = if (index < currentStep) c.success else c.border
                ) {}
            }
        }
    }
}
