package com.example.jetsoncontroller.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.GeoRadioRow
import com.example.jetsoncontroller.ui.storage.R7Section as GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoSegmented
import com.example.jetsoncontroller.ui.components.geo.GeoSheet
import com.example.jetsoncontroller.ui.components.geo.GeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.showGeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.contentDivider
import com.example.jetsoncontroller.ui.components.geo.shell.geoTabSelectedShapeColor
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.theme.*
import kotlinx.coroutines.launch

internal data class ThemeScreenState(val appearance: Appearance, val systemDark: Boolean) {
    val previewPalette: GeoPalette get() = appearance.palette(systemDark)
    val modeCaption: String? get() = if (appearance.mode == ThemeMode.SYSTEM)
        "기기 설정에 따라 밝은 테마와 어두운 테마를 바꿉니다." else null
    val lightGroupCaption: String? get() = if (previewPalette.isDark) "밝은 모드에서 사용합니다." else null
    val darkGroupCaption: String? get() = if (!previewPalette.isDark) "어두운 모드에서 사용합니다." else null

    fun selectPalette(palette: GeoPalette) = ThemePaletteSelection(
        appearance.withPalette(palette),
        if (palette.isDark == previewPalette.isDark) null
        else if (palette.isDark) "어두운 모드에서 쓸 테마로 저장했습니다."
        else "밝은 모드에서 쓸 테마로 저장했습니다."
    )
}

internal data class ThemePaletteSelection(val appearance: Appearance, val snackbar: String?)

internal data class ThemePaletteOption(val palette: GeoPalette, val description: String) {
    val label: String get() = palette.pickerLabel
    val swatch: ThemeSwatch get() = palette.colors.let { ThemeSwatch(it.surface, it.text, it.primary, it.nav, it.borderStrong) }
}

internal data class ThemeSwatch(val surface: Color, val text: Color, val primary: Color, val nav: Color, val border: Color)

internal val lightThemeOptions = listOf(
    ThemePaletteOption(GeoPalette.STANDARD, "GEO& Work와 같은 기본 화면입니다."),
    ThemePaletteOption(GeoPalette.NAVY_LIGHT, "위아래 막대를 네이비로 칠한 화면입니다."),
    ThemePaletteOption(GeoPalette.HIGH_CONTRAST, "햇빛 아래에서 읽기 쉽도록 글자와 선을 진하게 합니다.")
)

internal val darkThemeOptions = listOf(
    ThemePaletteOption(GeoPalette.GRAPHITE, "기본 어두운 화면입니다."),
    ThemePaletteOption(GeoPalette.NAVY_DARK, "푸른빛이 도는 어두운 화면입니다."),
    ThemePaletteOption(GeoPalette.MIST, "밝기 차이를 줄인 회색 화면입니다.")
)

/** Pushed settings page. The parent shell owns the status inset and persistent navigation. */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val store = rememberAppearanceStore()
    val appearance = rememberAppearance(store)
    val state = ThemeScreenState(appearance, isSystemInDarkTheme())
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val scroll = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var modePickerOpen by rememberSaveable { mutableStateOf(false) }
    val window = LocalGeoWindow.current
    val gutter = GeoSpace.gutterForPane(window.paneWidthDp)
    GeoSystemBars(navigationBarUsesSurface = window.rail || modePickerOpen)
    BackHandler(onBack = onBack)

    fun save(palette: GeoPalette) {
        val selection = state.selectPalette(palette)
        val message = if (store.setPalette(palette)) selection.snackbar else "화면 테마를 저장하지 못했습니다."
        if (message != null) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showGeoSnackbar(message)
        }
    }

    fun saveMode(mode: ThemeMode) {
        modePickerOpen = false
        if (!store.setMode(mode)) scope.launch { snackbar.showGeoSnackbar("화면 테마를 저장하지 못했습니다.") }
    }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            GeoThemeTopBar(onBack)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = gutter).padding(top = 16.dp, bottom = GeoSpace.scrollEnd)) {
                GeoSection("미리 보기", first = true) { GeoThemePreview(state.previewPalette) }
                GeoSection("모드") {
                    GeoSegmented(
                        label = "모드",
                        options = ThemeMode.entries.map { it.label },
                        selectedIndex = appearance.mode.ordinal,
                        onSelect = { saveMode(ThemeMode.entries[it]) },
                        onOpenPicker = { modePickerOpen = true },
                        showLabel = false
                    )
                    state.modeCaption?.let { Text(it, Modifier.padding(top = 8.dp), color = colors.textSecondary, style = type.caption) }
                }
                GeoThemePaletteGroup("밝은 테마", lightThemeOptions, appearance.lightPalette, state.lightGroupCaption, ::save, last = false)
                GeoThemePaletteGroup("어두운 테마", darkThemeOptions, appearance.darkPalette, state.darkGroupCaption, ::save)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { GeoSnackbar(snackbar) }
    }
    if (modePickerOpen) {
        GeoSheet(title = "모드", onDismissRequest = { modePickerOpen = false }) {
            Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "모드" }) {
                ThemeMode.entries.forEach { mode ->
                    GeoRadioRow(mode.label, appearance.mode == mode, { saveMode(mode) })
                }
            }
        }
    }
}

@Composable
private fun GeoThemeTopBar(onBack: () -> Unit) {
    val c = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    Row(Modifier.fillMaxWidth().background(c.topBar).heightIn(min = 56.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).drawBehind {
            if (focused) drawCircle(c.navFocus, style = Stroke(2.dp.toPx()))
        }.clip(CircleShape).background(if (pressed) c.onTopBar.copy(alpha = .08f) else Color.Transparent)
            .clickable(source, indication = null, role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "뒤로" }, contentAlignment = Alignment.Center) {
            Icon(GeoIcons.back, null, Modifier.size(24.dp), tint = c.onTopBar)
        }
        Text("화면 테마", Modifier.weight(1f).padding(vertical = 12.dp).semantics { heading() },
            style = LocalGeoType.current.title, color = c.onTopBar)
    }
}

@Composable
private fun GeoThemePaletteGroup(
    label: String,
    options: List<ThemePaletteOption>,
    selected: GeoPalette,
    caption: String?,
    onSelect: (GeoPalette) -> Unit,
    last: Boolean = true
) {
    GeoSection(label, note = caption) {
        Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }) {
            options.forEachIndexed { index, option -> GeoThemePaletteRow(option, option.palette == selected,
                divider = index < options.lastIndex || last) { onSelect(option.palette) } }
        }
    }
}

@Composable
private fun GeoThemePaletteRow(option: ThemePaletteOption, selected: Boolean, divider: Boolean = true, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    Row(Modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider() else Modifier).heightIn(min = 72.dp)
        .background(if (pressed) c.fieldPressed else Color.Transparent)
        .selectable(selected, source, indication = null, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(20.dp)) {
            if (focused) {
                drawCircle(c.focusHalo, radius = 16.dp.toPx(), style = Stroke(4.dp.toPx()))
                drawCircle(c.focus, radius = 13.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            val ring = if (selected) c.primary else c.borderStrong
            drawCircle(ring, radius = 9.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (selected) drawCircle(c.primary, radius = 5.dp.toPx())
        }
        GeoThemeSwatch(option.swatch)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(option.label, color = c.text, style = type.titleSm)
            Text(option.description, color = c.textSecondary, style = type.body)
        }
    }
}

@Composable
private fun GeoThemeSwatch(swatch: ThemeSwatch) {
    Canvas(Modifier.size(56.dp, 40.dp).clip(GeoShape.control).border(1.dp, swatch.border, GeoShape.control)
        .clearAndSetSemantics {}) {
        drawRect(swatch.surface)
        drawRect(swatch.nav, topLeft = Offset(0f, 28.dp.toPx()), size = Size(size.width, 12.dp.toPx()))
        drawRect(swatch.text, topLeft = Offset(10.dp.toPx(), 12.dp.toPx()), size = Size(16.dp.toPx(), 4.dp.toPx()))
        drawCircle(swatch.primary, radius = 4.dp.toPx(), center = Offset(42.dp.toPx(), 14.dp.toPx()))
    }
}

/** A fixed-size miniature, with one descriptive accessibility node and no input targets. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun GeoThemePreview(palette: GeoPalette) {
    val c = palette.colors
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        val measurer = rememberTextMeasurer()
        val type = GeoType.forPalette(palette)
        val tabs = listOf(
            rememberVectorPainter(GeoIcons.tabWorkFilled(c.navSelected, geoTabSelectedShapeColor(palette, c))),
            rememberVectorPainter(GeoIcons.tabDevice),
            rememberVectorPainter(GeoIcons.tabTransfer),
            rememberVectorPainter(GeoIcons.tabSettings)
        )
        val chevron = rememberVectorPainter(GeoIcons.chevronDown)
        Canvas(Modifier.fillMaxWidth().height(160.dp).clip(GeoShape.card).border(1.dp, c.border, GeoShape.card)
            .clearAndSetSemantics {
                contentDescription = "${palette.label} 미리 보기 · 작업 · GEO-X-1 · 신월4거리 북측 보조 구간 · 마감 10월 21일 · 할 일 · 저장 공간 · 512 GB 남음 · 예시 입력 · 수집 시작 · 작업, 장치, 전송, 설정"
            }) {
            fun text(value: String, x: Float, y: Float, color: Color, style: TextStyle = type.body,
                width: Float? = null): Float {
                val layout = measurer.measure(value,
                    style = style.copy(color = color, fontSize = style.fontSize * .6f, lineHeight = style.lineHeight * .6f),
                    constraints = Constraints(maxWidth = width?.let { it.dp.roundToPx().coerceAtLeast(1) } ?: Constraints.Infinity))
                drawText(layout, topLeft = Offset(x.dp.toPx(), y.dp.toPx()))
                return layout.size.height.toDp().value
            }
            fun rect(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float = 0f) {
                drawRoundRect(color, topLeft = Offset(x.dp.toPx(), y.dp.toPx()),
                    size = Size(width.dp.toPx(), height.dp.toPx()), cornerRadius = CornerRadius(radius.dp.toPx()))
            }
            val width = size.width.toDp().value
            rect(c.surface, 0f, 0f, width, 160f)
            rect(c.topBar, 0f, 0f, width, 32f)
            text("작업", 12f, 6f, c.onTopBar, type.heading)
            drawCircle(c.chromeSuccess, radius = 3.dp.toPx(), center = Offset((width - 82f).dp.toPx(), 16.dp.toPx()))
            text("GEO-X-1", width - 74f, 9f, c.onTopBar, type.titleSm)
            translate((width - 24f).dp.toPx(), 10.dp.toPx()) {
                with(chevron) { draw(Size(12.dp.toPx(), 12.dp.toPx()), colorFilter = ColorFilter.tint(c.onTopBarIcon)) }
            }
            val columnWidth = (width - 36f) / 2f
            val fieldX = 24f + columnWidth
            val titleHeight = text("신월4거리 북측 보조 구간", 12f, 36f, c.text, type.titleSm, width = columnWidth - 42f)
            val metaHeight = text("마감 10월 21일", 12f, 38f + titleHeight, c.textSecondary, type.caption,
                width = columnWidth - 42f)
            val badgeX = 12f + columnWidth - 36f
            drawCircle(c.neutral, radius = 2.dp.toPx(), center = Offset((badgeX + 7f).dp.toPx(), 44.dp.toPx()))
            text("할 일", badgeX + 12f, 38f, c.neutral, type.inlineStatus)
            val dividerY = 44f + titleHeight + metaHeight
            rect(c.border, 12f, dividerY, columnWidth, 1f)
            text("저장 공간", 12f, dividerY + 8f, c.textSecondary)
            text("512 GB 남음", 12f + columnWidth - 62f, dividerY + 8f, c.text)
            text("예시 입력", fieldX, 38f, c.text, type.label)
            rect(c.field, fieldX, 56f, columnWidth, 22f, radius = 4.8f)
            drawRoundRect(c.borderStrong, Offset(fieldX.dp.toPx(), 56.dp.toPx()),
                Size(columnWidth.dp.toPx(), 22.dp.toPx()), cornerRadius = CornerRadius(4.8.dp.toPx()), style = Stroke(1.dp.toPx()))
            rect(c.primary, fieldX, 86f, columnWidth, 24f, radius = 4.8f)
            text("수집 시작", fieldX + columnWidth / 2 - 24f, 91f, c.onPrimary, type.button)
            rect(c.nav, 0f, 128f, width, 32f)
            rect(c.navBorder, 0f, 128f, width, 1f)
            listOf("작업", "장치", "전송", "설정").forEachIndexed { index, label ->
                val centerX = width * (index + .5f) / 4f
                if (index == 0) {
                    rect(geoTabSelectedShapeColor(palette, c), centerX - 14.4f, 130f, 28.8f, 16.8f, radius = 4.8f)
                    if (palette.highContrast) drawRoundRect(c.navSelected,
                        Offset((centerX - 14.4f).dp.toPx(), 130.dp.toPx()), Size(28.8.dp.toPx(), 16.8.dp.toPx()),
                        cornerRadius = CornerRadius(4.8.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
                translate((centerX - 6f).dp.toPx(), 132.dp.toPx()) {
                    with(tabs[index]) { draw(Size(12.dp.toPx(), 12.dp.toPx()),
                        colorFilter = if (index == 0) null else ColorFilter.tint(c.navUnselected)) }
                }
                text(label, centerX - 8f, 146f,
                    if (index == 0) c.navSelected else c.navUnselected, if (index == 0) type.tabSelected else type.tab)
            }
        }
    }
}
