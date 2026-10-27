package com.example.jetsoncontroller.ui.dev

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.theme.*

internal val LocalDevOngoingStrip = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
internal fun DevFrame(title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    primaryAction: GeoButtonAction? = null, secondaryAction: GeoButtonAction? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val chrome = LocalGeoChrome.current
    var stripHeight by remember { mutableFloatStateOf(0f) }
    BackHandler(onBack = onBack)
    GeoLargeTitleScaffold(title, onBack = onBack, actions = actions,
        primaryAction = primaryAction, secondaryAction = secondaryAction) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.onSizeChanged { stripHeight = it.height / density.density }) { LocalDevOngoingStrip.current() }
            CompositionLocalProvider(LocalGeoChrome provides chrome.copy(topDp = chrome.topDp + stripHeight)) { content() }
        }
    }
}

@Composable
internal fun DevPage(title: String, onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}, footer: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    primaryAction: GeoButtonAction? = null, secondaryAction: GeoButtonAction? = null,
    content: @Composable ColumnScope.() -> Unit) {
    DevFrame(title, onBack, actions, primaryAction, secondaryAction) {
        val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
        val body: @Composable (@Composable () -> Unit) -> Unit = { inline ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = gutter).padding(bottom = 24.dp)) {
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
internal fun DevText(text: String, secondary: Boolean = false, mono: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier, color = if (secondary) LocalGeoColors.current.textSecondary else LocalGeoColors.current.text,
        style = if (mono) LocalGeoType.current.mono else LocalGeoType.current.body)
}

@Composable
internal fun DevLoading(text: String = "정보를 불러오는 중", modifier: Modifier = Modifier) {
    GeoSkeleton(text, modifier, kind = GeoSkeletonKind.Rows)
}

@Composable
internal fun DevDivider() = HorizontalDivider(color = LocalGeoColors.current.border)

@Composable
internal fun DevIdentity(name: String, detail: String? = null, status: String? = null,
    tone: Tone = Tone.UNKNOWN, statusInDetail: Boolean = false) {
    GeoRowList(if (status == null) GeoRowTrailing.None else GeoRowTrailing.Status) {
        GeoRow(name, {}, meta = detail, navigable = false, divider = false,
            leading = { GeoIconTile(GeoIcons.tabDevice) },
            badge = { status?.let { GeoBadge(it, tone, variant = GeoBadgeVariant.INLINE) } })
    }
}

@Composable
internal fun DevFeedback(message: String?, error: Boolean = false) {
    if (message != null) {
        if (error) GeoInlineNote(message,
            modifier = if (LocalGeoGroupInset.current) Modifier else Modifier.padding(vertical = 12.dp),
            tone = Tone.ERROR, kind = GeoNoteKind.LIVE_ERROR)
        else Text(message, Modifier.padding(horizontal = if (LocalGeoGroupInset.current) 16.dp else 0.dp, vertical = 12.dp),
            style = LocalGeoType.current.body, color = LocalGeoColors.current.text)
    }
}

@Composable
internal fun DevAddressRow(label: String, address: String, onClick: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    GeoActionRow(label, "주소 복사", onClick, { clipboard.setText(AnnotatedString(address)) },
        meta = geoBreakableValue(address), leading = { GeoIconTile(GeoIcons.router) })
}

/** Use fullScreen = false in a group; GeoStateBlock supplies the group's 16/12 inset. */
@Composable
internal fun DevEmpty(title: String, explanation: String, action: String? = null, onAction: (() -> Unit)? = null,
    fullScreen: Boolean = true) {
    GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, title, explanation, action), fullScreen = fullScreen, onAction = onAction)
}

@Composable
internal fun DevChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    enabled: Boolean = true, reason: String = "장치에 연결하면 바꿀 수 있습니다.") {
    var open by remember { mutableStateOf(false) }
    GeoSegmented(label, options, selected, onSelect, { open = true },
        enabled = enabled, disabledReason = if (enabled) null else reason)
    if (open) GeoSheet(label, { open = false }, kind = GeoSheetKind.CHOICE) {
        options.forEachIndexed { index, option -> GeoRadioRow(option, selected == index, { open = false; onSelect(index) }) }
    }
}

@Composable
internal fun DevCopyValue(label: String, value: String) {
    GeoCopyableValue(value, label = label)
}

/** Rows own their inset; forms and document facts get one shared content inset. */
@Composable
internal fun DevSection(title: String, modifier: Modifier = Modifier, first: Boolean = false,
    danger: Boolean = false, count: String? = null, note: String? = null,
    trailingColumn: GeoRowTrailing? = null, insetContent: Boolean = trailingColumn == null,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides if (danger) colors.copy(surface = colors.dangerSoft) else colors) {
        GeoGroupedSection(listOfNotNull(title, count).joinToString(" · "), modifier, first,
            trailingColumn = trailingColumn ?: GeoRowTrailing.None) {
            Column(if (insetContent) Modifier.padding(16.dp) else Modifier) {
                CompositionLocalProvider(LocalGeoGroupInset provides !insetContent) {
                    note?.let { Text(it, Modifier.padding(start = if (insetContent) 0.dp else 16.dp,
                        end = if (insetContent) 0.dp else 16.dp, top = if (insetContent) 0.dp else 12.dp,
                        bottom = if (insetContent) 8.dp else 12.dp), color = colors.textSecondary,
                        style = LocalGeoType.current.supporting) }
                    content()
                }
            }
        }
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
