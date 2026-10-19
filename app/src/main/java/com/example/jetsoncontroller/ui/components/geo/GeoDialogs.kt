package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class GeoConfirmKind { STANDARD, DANGER }

@Composable
fun GeoConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    kind: GeoConfirmKind = GeoConfirmKind.STANDARD,
    dismissLabel: String = "취소",
    confirmEnabled: Boolean = true,
    disabledReason: String? = null,
    busy: Boolean = false,
    openerFocusRequester: FocusRequester? = null,
    facts: @Composable ColumnScope.() -> Unit = {}
) {
    require(confirmLabel.isNotBlank() && confirmLabel != "확인" && confirmLabel != "예")
    require(confirmEnabled || !disabledReason.isNullOrBlank()) { "A disabled confirmation needs a visible reason" }
    OverlayDialog(title, onDismissRequest, modifier, openerFocusRequester, footer = {
        GeoOverlayActions(
            GeoOverlayAction(confirmLabel, onConfirm, enabled = confirmEnabled, busy = busy),
            GeoOverlayAction(dismissLabel, onDismissRequest),
            danger = kind == GeoConfirmKind.DANGER
        )
    }) {
        Text(body, color = LocalGeoColors.current.text, style = LocalGeoType.current.body)
        facts()
        if (!confirmEnabled && disabledReason != null) {
            Spacer(Modifier.height(8.dp))
            Text(disabledReason, color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.body)
        }
    }
}

@Composable
fun GeoInfoDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    openerFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OverlayDialog(title, onDismissRequest, modifier, openerFocusRequester, footer = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            GeoOverlayButton(GeoOverlayAction("닫기", onDismissRequest))
        }
    }, content = content)
}

@Composable
private fun OverlayDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    openerFocusRequester: FocusRequester?,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val window = LocalConfiguration.current
    var bodySpace by remember(window.screenWidthDp, window.screenHeightDp, density.fontScale) { mutableIntStateOf(Int.MAX_VALUE) }
    val fallback = sheetNeedsFullScreen(with(density) { bodySpace.toDp().value }, 0f, 0f)
    val t = LocalGeoColors.current
    RestoreOverlayFocus(openerFocusRequester)
    if (fallback) {
        OverlayFullScreenDialog(title, null, onDismissRequest, modifier, footer, content)
    } else {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                val gutter = overlayGutter(maxWidth.value).dp
                Box(Modifier.fillMaxSize().padding(gutter), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth().semantics { paneTitle = title },
                        shape = RoundedCornerShape(14.dp), color = t.surface, contentColor = t.text,
                        shadowElevation = 16.dp
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            OverlayViewport(
                                header = {
                                    Text(title, color = t.text, style = LocalGeoType.current.title, modifier = Modifier.semantics { heading() })
                                    Spacer(Modifier.height(8.dp))
                                },
                                footer = {
                                    Spacer(Modifier.height(16.dp))
                                    footer()
                                },
                                onBodySpaceMeasured = { bodySpace = it },
                                body = content
                            )
                        }
                    }
                }
            }
        }
    }
}
