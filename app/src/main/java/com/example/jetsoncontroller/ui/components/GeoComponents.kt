package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.GeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.OutlinedButton

// =====================================================================================
// Surfaces
// =====================================================================================

/**
 * A grouped block of related content.
 *
 * Separation comes from a hairline border on a flat surface rather than from a shadow
 * or a tonal tint. Shadows disappear outdoors and tonal tints pulled the previous
 * design's "raised" surfaces toward the primary hue, which competed with status colour.
 *
 * Pass [tone] only when the *whole section* carries that status — it draws a coloured
 * rule down the leading edge, which reads before any text does. Do not tint the section
 * background; that was the previous design's habit and it made every screen a patchwork
 * of coloured blocks.
 */
@Composable
fun GeoSection(
    modifier: Modifier = Modifier,
    tone: StatusTone? = null,
    contentPadding: PaddingValues = PaddingValues(GeoSpace.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalGeoColors.current
    val ruleColor = tone?.let { statusVisuals(it).content }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(GeoSize.hairline, c.border),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (ruleColor != null) {
                Box(
                    Modifier
                        .width(GeoSize.statusRule)
                        .fillMaxHeight()
                        .background(ruleColor)
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.md),
                content = content
            )
        }
    }
}

// =====================================================================================
// Headers and rows
// =====================================================================================

/** A section title, optionally preceded by a quiet eyebrow and followed by one action. */
@Composable
fun GeoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = LocalGeoColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow, style = GeoType.eyebrow, color = c.muted)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        trailing?.invoke()
    }
}

/**
 * A label/value pair.
 *
 * The value uses tabular figures so a refreshing number does not shift the row, and
 * [valueUnavailable] renders an explicit "미확인" rather than a dash that the user could
 * read as zero. A missing value and a zero value are different facts.
 */
@Composable
fun GeoDataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueUnavailable: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = LocalGeoColors.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = c.inkSubtle)
            }
        }
        Text(
            text = if (valueUnavailable) "미확인" else value,
            style = GeoType.numeric,
            color = if (valueUnavailable) c.unknown else c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

/** A thin rule between rows inside a section. */
@Composable
fun GeoRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = LocalGeoColors.current.border)
}

/**
 * "마지막 확인 · 12:03" — states when the app last heard from the source.
 *
 * Any screen that can show a value it did not just fetch must show one of these. The
 * brief's hardest rule is that stale data must never be presented as live.
 */
@Composable
fun GeoFreshnessLabel(
    text: String,
    modifier: Modifier = Modifier,
    stale: Boolean = false
) {
    val c = LocalGeoColors.current
    Text(
        text = text,
        modifier = modifier,
        style = GeoType.numericSmall,
        color = if (stale) c.warning else c.muted
    )
}

// =====================================================================================
// Status presentation
// =====================================================================================

/**
 * One independent status axis on the field home: 앱↔장치 / 수집 실행 / 장치 저장 / 서버 전송.
 *
 * Each tile owns exactly one axis. They are rendered side by side precisely so that the
 * operator can see that "연결됨" and "저장 확인" are separate facts — the single most
 * costly misreading this product can produce.
 */
@Composable
fun GeoStatusTile(
    title: String,
    value: String,
    detail: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    freshness: String? = null
) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(tone)
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 112.dp),
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(GeoSize.hairline, c.border),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(GeoSpace.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    visuals.icon,
                    contentDescription = null,
                    tint = visuals.content,
                    modifier = Modifier.size(GeoSize.iconSm)
                )
                Text(title, style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = visuals.content
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (freshness != null) {
                GeoFreshnessLabel(freshness, stale = tone == StatusTone.UNKNOWN)
            }
        }
    }
}

/**
 * Empty, failed and not-yet-known screens, in the shape the brief prescribes:
 * 현재 무슨 일인지 → 사용자 작업에 어떤 영향이 있는지 → 다음에 무엇을 할 수 있는지.
 *
 * [impact] is required rather than optional on purpose. An error message that does not
 * say what it means for the operator's work is the kind of message they learn to skip.
 */
@Composable
fun GeoStateBlock(
    title: String,
    impact: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(tone)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GeoSpace.xl, vertical = GeoSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
    ) {
        Icon(
            icon ?: visuals.icon,
            contentDescription = null,
            tint = visuals.content,
            modifier = Modifier.size(GeoSize.iconXl)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = c.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            impact,
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (primaryLabel != null && onPrimary != null) {
            Button(
                onClick = onPrimary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
            ) { Text(primaryLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
            ) { Text(secondaryLabel) }
        }
    }
}

// =====================================================================================
// Actions
// =====================================================================================

/**
 * The bottom action bar for a task screen.
 *
 * [readiness] states, in one line, why the primary action is or is not available. The
 * previous design disabled buttons silently, which left the operator guessing; the
 * brief calls that out directly ("경고색만 바꾼 활성 시작 버튼" and its mirror image).
 *
 * The bar pads itself for the navigation bar so that it clears gesture handles and
 * Samsung's three-button navigation — a regression this project has already had to fix
 * once on real hardware.
 */
@Composable
fun GeoBottomActionBar(
    readiness: String,
    modifier: Modifier = Modifier,
    readinessTone: StatusTone = StatusTone.INFO,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.surface,
        contentColor = c.ink
    ) {
        Column {
            GeoRowDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.md),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val visuals = statusVisuals(readinessTone)
                    Icon(
                        visuals.icon,
                        contentDescription = null,
                        tint = visuals.content,
                        modifier = Modifier.size(GeoSize.iconSm)
                    )
                    Text(
                        readiness,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                }
                content()
            }
        }
    }
}

/** The one action a task screen exists for. Sized for a gloved thumb, not a mouse. */
@Composable
fun GeoPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(GeoSize.iconMd))
            Spacer(Modifier.width(GeoSpace.sm))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A destructive or disruptive action (수집 중지 · 재부팅 · 전원 종료 · 자료 정리).
 *
 * [target] names the device and the work that will be affected. The brief asks for
 * "어느 장치의 어떤 작업에 영향을 주는지" instead of a bare "정말 실행할까요?", and the target
 * is shown next to the button rather than only inside the confirmation dialog, so the
 * operator reads it before committing to the gesture.
 */
@Composable
fun GeoDangerAction(
    label: String,
    target: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = LocalGeoColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(target, style = MaterialTheme.typography.bodySmall, color = c.muted)
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(GeoSize.hairline, c.dangerBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
            modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction)
        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
    }
}

/** A quiet secondary action used in a row of peers. */
@Composable
fun RowScope.GeoSecondaryAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.weight(1f).heightIn(min = GeoSize.secondaryAction)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An identifier the operator may have to read out or compare (Run ID, 점검 ID, 경로).
 * Monospaced and announced as a whole so a screen reader does not spell it letter by
 * letter from the middle.
 */
@Composable
fun GeoIdentifier(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val c = LocalGeoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(
            value,
            style = GeoType.identifier,
            color = c.muted,
            modifier = Modifier.weight(1f).clearAndSetSemantics { },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
