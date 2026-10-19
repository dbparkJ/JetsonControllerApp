package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.jetsoncontroller.ui.theme.GeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

enum class GeoSheetKind { CHOICE, FORM, INFO }
enum class GeoOverlayTone { SUCCESS, WARNING, DANGER, INFO, PENDING, NEUTRAL }

data class GeoOverlayAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val disabledReason: String? = null
)

/** Heights include the measured header/footer, system insets and the IME exactly once. */
fun sheetNeedsFullScreen(
    availableHeightDp: Float,
    headerHeightDp: Float,
    footerHeightDp: Float,
    insetHeightDp: Float = 0f
): Boolean = availableHeightDp - headerHeightDp - footerHeightDp - insetHeightDp < 160f

internal fun overlayActionsFit(labelWidths: List<Int>, availableWidth: Int, gap: Int, equal: Boolean): Boolean {
    if (labelWidths.isEmpty()) return true
    val usable = availableWidth - gap * (labelWidths.size - 1)
    return if (equal) labelWidths.all { it <= usable / labelWidths.size }
    else labelWidths.sum() <= usable
}

// This is the only seam to today's tokens. No dependency on the other Phase 3 slices.
internal class OverlayTokens(private val colors: GeoColors) {
    val surface get() = colors.surface
    val text get() = colors.ink
    val textSecondary get() = colors.muted
    val border get() = colors.border
    val borderStrong get() = colors.controlBorder
    val scrim get() = colors.overlay
    val primary get() = colors.primary
    val primaryPressed get() = colors.primaryPressed
    val onPrimary get() = colors.onPrimary
    val danger get() = colors.danger
    val dangerPressed get() = lerp(colors.danger, colors.ink, .12f)
    val onDanger get() = colors.onDanger
    val pressed get() = colors.subtle
    val disabledFill get() = colors.disabled
    val disabledText get() = colors.onDisabled
    val focus get() = colors.focusRing
    fun tone(tone: GeoOverlayTone): Color = when (tone) {
        GeoOverlayTone.SUCCESS -> colors.success
        GeoOverlayTone.WARNING -> colors.warning
        GeoOverlayTone.DANGER -> colors.danger
        GeoOverlayTone.INFO -> colors.info
        GeoOverlayTone.PENDING -> colors.pending
        GeoOverlayTone.NEUTRAL -> colors.unknown
    }
    fun soft(tone: GeoOverlayTone): Color = when (tone) {
        GeoOverlayTone.SUCCESS -> colors.successBg
        GeoOverlayTone.WARNING -> colors.warningBg
        GeoOverlayTone.DANGER -> colors.dangerBg
        GeoOverlayTone.INFO -> colors.infoBg
        GeoOverlayTone.PENDING -> colors.pendingBg
        GeoOverlayTone.NEUTRAL -> colors.unknownBg
    }
}

internal enum class OverlayButtonStyle { PRIMARY, SECONDARY, TERTIARY, DANGER }

/** Private stand-ins remain behind this adapter until the controls slice is integrated. */
internal object OverlayAdapter {
    val tokens: OverlayTokens @Composable get() = OverlayTokens(LocalGeoColors.current)
    val body: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp)
    val title: TextStyle @Composable get() = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
    val titleSmall: TextStyle @Composable get() = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val button: TextStyle @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)
    val caption: TextStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
    val inverse: Color @Composable get() = MaterialTheme.colorScheme.inverseSurface
    val onInverse: Color @Composable get() = MaterialTheme.colorScheme.inverseOnSurface
    val inverseAction: Color @Composable get() = MaterialTheme.colorScheme.inversePrimary

    @Composable
    fun Action(action: GeoOverlayAction, modifier: Modifier = Modifier, style: OverlayButtonStyle = OverlayButtonStyle.TERTIARY,
               color: Color? = null) = OverlayButton(action, modifier, style, color)

    @Composable
    fun Actions(primary: GeoOverlayAction, secondary: GeoOverlayAction? = null, equal: Boolean = false,
                danger: Boolean = false) = OverlayActions(primary, secondary, equal, danger)

    @Composable
    fun StatusIcon(tone: GeoOverlayTone, modifier: Modifier = Modifier) = OverlayStatusIcon(tone, modifier)
}

@Composable
private fun OverlayButton(action: GeoOverlayAction, modifier: Modifier, style: OverlayButtonStyle, color: Color?) {
    val t = OverlayAdapter.tokens
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val enabled = action.enabled && !action.busy
    val filled = style == OverlayButtonStyle.PRIMARY || style == OverlayButtonStyle.DANGER
    val fill = when {
        !enabled && !action.busy -> t.disabledFill
        style == OverlayButtonStyle.PRIMARY -> if (pressed) t.primaryPressed else t.primary
        style == OverlayButtonStyle.DANGER -> if (pressed) t.dangerPressed else t.danger
        pressed && color != null -> color.copy(alpha = .12f)
        pressed -> t.pressed
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled && !action.busy -> t.disabledText
        color != null -> color
        style == OverlayButtonStyle.PRIMARY -> t.onPrimary
        style == OverlayButtonStyle.DANGER -> t.onDanger
        style == OverlayButtonStyle.SECONDARY -> t.text
        else -> t.primary
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.heightIn(min = if (filled || style == OverlayButtonStyle.SECONDARY) 52.dp else 48.dp)
            .widthIn(min = 48.dp)
            .drawBehind {
                if (focused) {
                    val offset = 4.dp.toPx()
                    val ring = color ?: t.focus
                    drawRoundRect(ring.copy(alpha = .24f), Offset(-offset * 2, -offset * 2),
                        Size(size.width + offset * 4, size.height + offset * 4),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                    drawRoundRect(ring, Offset(-offset, -offset),
                        Size(size.width + offset * 2, size.height + offset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }.background(fill, shape)
            .then(if (style == OverlayButtonStyle.SECONDARY && enabled) Modifier.border(1.dp, t.borderStrong, shape) else Modifier)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = action.onClick)
            .padding(horizontal = if (filled) 16.dp else 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (action.busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = foreground, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(action.label, color = foreground, style = OverlayAdapter.button, maxLines = 2)
    }
}

@Composable
private fun OverlayActions(primary: GeoOverlayAction, secondary: GeoOverlayAction?, equal: Boolean, danger: Boolean) {
    val measure = rememberTextMeasurer()
    val textStyle = OverlayAdapter.button
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val actions = listOfNotNull(secondary, primary)
        val widths = actions.map {
            val padding = with(density) { ((if (it === primary) 32.dp else 24.dp) + if (it.busy) 24.dp else 0.dp).roundToPx() }
            (measure.measure(it.label, textStyle, softWrap = false, maxLines = 1).size.width + padding)
                .coerceAtLeast(with(density) { 48.dp.roundToPx() })
        }
        val fits = overlayActionsFit(widths, constraints.maxWidth, with(density) { 8.dp.roundToPx() }, equal)
        val primaryStyle = if (danger) OverlayButtonStyle.DANGER else OverlayButtonStyle.PRIMARY
        if (fits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (secondary != null) OverlayButton(secondary, if (equal) Modifier.weight(1f) else Modifier,
                    if (equal) OverlayButtonStyle.SECONDARY else OverlayButtonStyle.TERTIARY, null)
                OverlayButton(primary, if (equal) Modifier.weight(1f) else Modifier, primaryStyle, null)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton(primary, Modifier.fillMaxWidth(), primaryStyle, null)
                if (secondary != null) OverlayButton(secondary, Modifier.fillMaxWidth(),
                    if (equal) OverlayButtonStyle.SECONDARY else OverlayButtonStyle.TERTIARY, null)
            }
        }
    }
}

@Composable
private fun OverlayStatusIcon(tone: GeoOverlayTone, modifier: Modifier) {
    val color = OverlayAdapter.tokens.tone(tone)
    Canvas(modifier.size(20.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (tone == GeoOverlayTone.WARNING) {
                drawPath(Path().apply { moveTo(12f, 4f); lineTo(2.8f, 19.5f); lineTo(21.2f, 19.5f); close() }, color, style = stroke)
            } else drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
            when (tone) {
                GeoOverlayTone.SUCCESS -> drawPath(Path().apply { moveTo(8f, 12.5f); lineTo(10.5f, 15f); lineTo(16f, 9.5f) }, color, style = stroke)
                GeoOverlayTone.WARNING, GeoOverlayTone.DANGER -> {
                    drawLine(color, Offset(12f, 8f), Offset(12f, 13.5f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                    drawCircle(color, 1f, Offset(12f, 17f))
                }
                GeoOverlayTone.PENDING, GeoOverlayTone.NEUTRAL -> {
                    drawLine(color, Offset(12f, 7f), Offset(12f, 12f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                    drawLine(color, Offset(12f, 12f), Offset(15f, 14f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                }
                GeoOverlayTone.INFO -> {
                    drawCircle(color, 1f, Offset(12f, 7.8f))
                    drawLine(color, Offset(12f, 11f), Offset(12f, 16.5f), strokeWidth = 1.75f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

internal fun overlayGutter(widthDp: Float): Int = when {
    widthDp >= 390 -> 24
    widthDp >= 340 -> 20
    else -> 16
}

@Composable
internal fun RestoreOverlayFocus(opener: FocusRequester?) {
    DisposableEffect(opener) {
        onDispose {
            // Device switching may remove the opener together with its confirmation.
            if (opener != null) runCatching { opener.requestFocus() }
        }
    }
}

@Composable
fun GeoSheetFooter(primaryAction: GeoOverlayAction, secondaryAction: GeoOverlayAction? = null) {
    OverlayAdapter.Actions(primaryAction, secondaryAction, equal = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    kind: GeoSheetKind = GeoSheetKind.CHOICE,
    subtitle: String? = null,
    openerFocusRequester: FocusRequester? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = OverlayAdapter.tokens
    val density = LocalDensity.current
    val window = LocalConfiguration.current
    // Keep the fallback for this window/font configuration so an IME animation cannot
    // repeatedly replace the dialog window and steal focus from an editor.
    var fullScreen by remember(window.screenWidthDp, window.screenHeightDp, density.fontScale) { mutableStateOf(false) }
    var bodySpace by remember(window.screenWidthDp, window.screenHeightDp, density.fontScale) { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(bodySpace) {
        if (sheetNeedsFullScreen(with(density) { bodySpace.toDp().value }, 0f, 0f)) fullScreen = true
    }
    require(kind != GeoSheetKind.INFO || footer == null) { "An info sheet has no action footer" }
    RestoreOverlayFocus(openerFocusRequester)
    if (fullScreen) {
        OverlayFullScreenDialog(title, subtitle, onDismissRequest, modifier, footer, content)
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = modifier.semantics { paneTitle = title },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = t.surface, contentColor = t.text, scrimColor = t.scrim,
            dragHandle = null,
            // Material owns the IME; the viewport owns safe drawing once, including its footer.
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            OverlayViewport(
                header = {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp, 4.dp).background(t.borderStrong, RoundedCornerShape(2.dp)))
                    }
                    OverlaySheetHeading(title, subtitle, onDismissRequest)
                },
                footer = footer?.let { { OverlayFooter(it) } },
                onBodySpaceMeasured = { bodySpace = it },
                body = { Column(Modifier.padding(horizontal = 24.dp), content = content) }
            )
        }
    }
}

@Composable
private fun OverlaySheetHeading(title: String, subtitle: String?, onDismiss: (() -> Unit)? = null) {
    val t = OverlayAdapter.tokens
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = t.text, style = OverlayAdapter.title, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = t.textSecondary, style = OverlayAdapter.body)
        if (onDismiss != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            OverlayAdapter.Action(GeoOverlayAction("닫기", onDismiss))
        }
    }
}

@Composable
private fun OverlayFooter(content: @Composable ColumnScope.() -> Unit) {
    Column {
        HorizontalDivider(color = OverlayAdapter.tokens.border)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), content = content)
    }
}

/** Measure chrome before the scroll body; short content does not itself cause fallback. */
@Composable
internal fun OverlayViewport(
    header: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    onBodySpaceMeasured: ((Int) -> Unit)? = null,
    inlineFooterWhenCrowded: Boolean = false,
    fillViewport: Boolean = false,
    body: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val head = subcompose("header") { Column(content = header) }.single().measure(loose)
        val foot = footer?.let { subcompose("footer") { Column(content = it) }.single().measure(loose) }
        val available = (constraints.maxHeight - head.height - (foot?.height ?: 0)).coerceAtLeast(0)
        val inline = inlineFooterWhenCrowded && sheetNeedsFullScreen(available.toDp().value, 0f, 0f)
        val scrollHeight = if (inline) (constraints.maxHeight - head.height).coerceAtLeast(0) else available
        val main = subcompose(if (inline) "inline-body" else "body") {
            Column(Modifier.verticalScroll(scroll).padding(bottom = 16.dp)) {
                body()
                if (inline && footer != null) footer()
            }
        }.single().measure(constraints.copy(minHeight = if (fillViewport) scrollHeight else 0, maxHeight = scrollHeight))
        val height = constraints.constrainHeight(head.height + main.height + if (inline) 0 else (foot?.height ?: 0))
        layout(constraints.maxWidth, height) {
            head.placeRelative(0, 0)
            main.placeRelative(0, head.height)
            if (!inline) foot?.placeRelative(0, head.height + main.height)
            onBodySpaceMeasured?.invoke(available)
        }
    }
}

@Composable
internal fun OverlayFullScreenDialog(
    title: String,
    subtitle: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = OverlayAdapter.tokens
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier.fillMaxSize().semantics { paneTitle = title }, color = t.surface, contentColor = t.text) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                OverlayViewport(
                    header = {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                            OverlayAdapter.Action(GeoOverlayAction("닫기", onDismissRequest))
                        }
                    },
                    footer = footer?.let { { OverlayFooter(it) } },
                    inlineFooterWhenCrowded = true,
                    fillViewport = true
                ) {
                    OverlaySheetHeading(title, subtitle)
                    Column(Modifier.padding(horizontal = 24.dp), content = content)
                }
            }
        }
    }
}
