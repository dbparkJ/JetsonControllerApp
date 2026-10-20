package com.example.jetsoncontroller.ui.dev

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.theme.*

internal val LocalDevOngoingStrip = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
internal fun DevFrame(title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalGeoColors.current
    val density = LocalDensity.current
    val chrome = LocalGeoChrome.current
    var headerHeight by remember { mutableFloatStateOf(56f) }
    var stripHeight by remember { mutableFloatStateOf(0f) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Row(Modifier.fillMaxWidth().background(c.topBar)
            .onSizeChanged { headerHeight = it.height / density.density }
            .heightIn(min = 56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalGeoColors provides c.copy(textSecondary = c.onTopBarIcon,
                primary = c.navFocus, field = c.onTopBar.copy(alpha = .08f))) {
                GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
            }
            Text(title, Modifier.weight(1f).padding(vertical = 12.dp).semantics { heading() },
                style = LocalGeoType.current.title, color = c.onTopBar)
            CompositionLocalProvider(LocalGeoColors provides c.copy(textSecondary = c.onTopBarIcon,
                primary = c.navFocus, field = c.onTopBar.copy(alpha = .08f))) { actions() }
        }
        Column(Modifier.onSizeChanged { stripHeight = it.height / density.density }) { LocalDevOngoingStrip.current() }
        CompositionLocalProvider(LocalGeoChrome provides chrome.copy(topDp = chrome.topDp + headerHeight + stripHeight)) { content() }
    }
}

@Composable
internal fun DevPage(title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}, footer: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    DevFrame(title, onBack, actions) {
        val gutter = GeoSpace.gutterForPane(LocalGeoWindow.current.paneWidthDp)
        val body: @Composable (@Composable () -> Unit) -> Unit = { inline ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = gutter).padding(top = 16.dp, bottom = 32.dp)) {
                content()
                if (footer != null) { Spacer(Modifier.height(24.dp)); inline() }
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (footer == null) body({}) else GeoActionArea(title, action = footer, body = body)
            Box(Modifier.align(Alignment.BottomCenter)) { overlay() }
        }
    }
}

@Composable
internal fun DevText(text: String, secondary: Boolean = false, mono: Boolean = false) {
    Text(text, color = if (secondary) LocalGeoColors.current.textSecondary else LocalGeoColors.current.text,
        style = if (mono) LocalGeoType.current.mono else LocalGeoType.current.body)
}

@Composable
internal fun DevDivider() = HorizontalDivider(color = LocalGeoColors.current.border)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DevIdentity(name: String, detail: String? = null, status: String? = null,
    tone: Tone = Tone.UNKNOWN, statusInDetail: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = LocalGeoType.current.heading, color = LocalGeoColors.current.text)
        if (status != null && !statusInDetail) GeoBadge(status, tone)
    }
    if ((status != null && statusInDetail) || detail != null) {
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status != null && statusInDetail) GeoBadge(status, tone)
            detail?.let { DevText(it, secondary = true) }
        }
    }
}

@Composable
internal fun DevFeedback(message: String?, error: Boolean = false) {
    if (message != null) Text(message, Modifier.padding(vertical = 12.dp), style = LocalGeoType.current.body,
        color = if (error) LocalGeoColors.current.danger else LocalGeoColors.current.text)
}

@Composable
internal fun DevAddressRow(label: String, address: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalGeoType provides LocalGeoType.current.copy(
        caption = LocalGeoType.current.mono)) {
        GeoRow(label, onClick, meta = address)
    }
}

@Composable
internal fun DevEmpty(title: String, explanation: String, action: String? = null, onAction: (() -> Unit)? = null) {
    GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, title, explanation, action), fullScreen = true, onAction = onAction)
}

@Composable
internal fun DevChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    enabled: Boolean = true, reason: String = "장치에 연결하면 바꿀 수 있습니다.") {
    var open by remember { mutableStateOf(false) }
    GeoSegmented(label, options, selected, onSelect, { open = true },
        enabled = enabled, disabledReason = if (enabled) null else reason)
    if (open) GeoSheet(label, { open = false }) {
        options.forEachIndexed { index, option -> GeoRadioRow(option, selected == index, { open = false; onSelect(index) }) }
    }
}

@Composable
internal fun DevCopyValue(label: String, value: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DevText(label, secondary = true)
                        SelectionContainer { DevText(value, mono = true) }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(1f), style = LocalGeoType.current.body, color = LocalGeoColors.current.textSecondary)
                    SelectionContainer(Modifier.weight(2f)) {
                        Text(value, Modifier.fillMaxWidth(), style = LocalGeoType.current.mono,
                            color = LocalGeoColors.current.text, textAlign = TextAlign.End)
                    }
                }
            }
            GeoIconButton(GeoIcons.copy, "$label 주소 복사",
                { clipboard.setText(androidx.compose.ui.text.AnnotatedString(value)) })
        }
        HorizontalDivider(color = LocalGeoColors.current.border)
    }
}

@Composable
internal fun rememberTechnicalInfo(): MutableState<Boolean> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("geo_developer", Context.MODE_PRIVATE) }
    val value = remember { mutableStateOf(preferences.getBoolean("show_technical_info", false)) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_technical_info") value.value = preferences.getBoolean(key, false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(value.value) { preferences.edit().putBoolean("show_technical_info", value.value).apply() }
    return value
}

internal fun devTimestamp(value: String?): String = com.example.jetsoncontroller.ui.model.Formats.time(
    value?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() })
