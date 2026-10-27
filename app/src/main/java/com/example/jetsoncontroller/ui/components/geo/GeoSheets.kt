package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoType
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

internal val LocalGeoChoiceSheet = staticCompositionLocalOf { false }

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

@Composable
internal fun GeoOverlayButton(action: GeoOverlayAction, modifier: Modifier = Modifier,
    style: GeoButtonStyle = GeoButtonStyle.Tertiary, color: Color? = null, edge: GeoButtonEdge = GeoButtonEdge.None) {
    GeoButton(action.label, action.onClick, modifier.width(IntrinsicSize.Max), style, action.enabled, action.busy,
        busyLabel = action.label.takeIf { action.busy }, contentColor = color, edge = edge,
        actionArea = style == GeoButtonStyle.Primary || style == GeoButtonStyle.DangerFilled)
}

@Composable
internal fun GeoOverlayActions(primary: GeoOverlayAction, secondary: GeoOverlayAction? = null,
    equal: Boolean = false, danger: Boolean = false) {
    GeoButtonActions(listOfNotNull(secondary?.let {
        GeoButtonAction(it.label, it.onClick, if (equal) GeoButtonStyle.Secondary else GeoButtonStyle.Tertiary,
            it.enabled, it.busy, it.label.takeIf { _ -> it.busy })
    }, GeoButtonAction(primary.label, primary.onClick,
        if (danger) GeoButtonStyle.DangerFilled else GeoButtonStyle.Primary,
        primary.enabled, primary.busy, primary.label.takeIf { primary.busy })),
        layout = if (equal) GeoActionLayout.EqualShares else GeoActionLayout.Dialog)
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
    GeoOverlayActions(primaryAction, secondaryAction, equal = true)
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
    val t = LocalGeoColors.current
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
    val sheetContent: @Composable ColumnScope.() -> Unit = {
        CompositionLocalProvider(LocalGeoChoiceSheet provides (kind == GeoSheetKind.CHOICE)) { content() }
    }
    if (fullScreen) {
        OverlayFullScreenDialog(title, subtitle, onDismissRequest, modifier, footer, sheetContent)
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = modifier.semantics { paneTitle = title },
            shape = GeoShape.sheetTop,
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
                body = { Column(Modifier.padding(horizontal = 24.dp), content = sheetContent) }
            )
        }
    }
}

@Composable
private fun OverlaySheetHeading(title: String, subtitle: String?, onDismiss: (() -> Unit)? = null) {
    val t = LocalGeoColors.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = if (onDismiss == null) 24.dp else 12.dp,
        top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = t.text, style = LocalGeoType.current.title, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, color = t.textSecondary, style = LocalGeoType.current.body)
        }
        if (onDismiss != null) GeoIconButton(GeoIcons.close, "닫기", onDismiss)
    }
}

@Composable
private fun OverlayFooter(content: @Composable ColumnScope.() -> Unit) {
    Column {
        HorizontalDivider(color = LocalGeoColors.current.border)
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), content = content)
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
            Column(Modifier.geoContainScroll().verticalScroll(scroll).padding(bottom = 16.dp)) {
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
    val t = LocalGeoColors.current
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GeoDialogCoversDisplay()
        Surface(modifier.fillMaxSize().semantics { paneTitle = title }, color = t.surface, contentColor = t.text) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                OverlayViewport(
                    header = {
                        OverlaySheetHeading(title, subtitle, onDismissRequest)
                    },
                    footer = footer?.let { { OverlayFooter(it) } },
                    inlineFooterWhenCrowded = true,
                    fillViewport = true
                ) {
                    Column(Modifier.padding(horizontal = 24.dp), content = content)
                }
            }
        }
    }
}
