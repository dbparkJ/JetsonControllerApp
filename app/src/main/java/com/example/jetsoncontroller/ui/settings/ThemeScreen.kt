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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.GeoRadioRow
import com.example.jetsoncontroller.ui.components.geo.GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoSegmented
import com.example.jetsoncontroller.ui.components.geo.GeoSheet
import com.example.jetsoncontroller.ui.components.geo.GeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.showGeoSnackbar
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
                        onOpenPicker = { modePickerOpen = true }
                    )
                    state.modeCaption?.let { Text(it, Modifier.padding(top = 8.dp), color = colors.textSecondary, style = type.caption) }
                }
                GeoThemePaletteGroup("밝은 테마", lightThemeOptions, appearance.lightPalette, state.lightGroupCaption, ::save)
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
    onSelect: (GeoPalette) -> Unit
) {
    GeoSection(label, note = caption) {
        Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }) {
            options.forEach { option -> GeoThemePaletteRow(option, option.palette == selected) { onSelect(option.palette) } }
        }
    }
}

@Composable
private fun GeoThemePaletteRow(option: ThemePaletteOption, selected: Boolean, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
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
        Canvas(Modifier.fillMaxWidth().height(160.dp).clip(GeoShape.control).border(1.dp, c.borderStrong, GeoShape.control)
            .clearAndSetSemantics {
                contentDescription = "${palette.label} 미리 보기 · 작업 · GEO-X-1 · 수집 구간 · 할 일 · 저장 공간 · 512 GB 남음 · 예시 입력 · 수집 시작 · 작업, 장치, 전송, 설정"
            }) {
            fun text(value: String, x: Float, y: Float, color: Color, style: TextStyle = type.body) {
                drawText(measurer, value, topLeft = Offset(x.dp.toPx(), y.dp.toPx()),
                    style = style.copy(color = color, fontSize = style.fontSize * .6f, lineHeight = style.lineHeight * .6f))
            }
            fun rect(color: Color, x: Float, y: Float, width: Float, height: Float) {
                drawRect(color, Offset(x.dp.toPx(), y.dp.toPx()), Size(width.dp.toPx(), height.dp.toPx()))
            }
            val width = size.width.toDp().value
            rect(c.canvas, 0f, 0f, width, 160f)
            rect(c.topBar, 0f, 0f, width, 28f)
            text("작업", 12f, 5f, c.onTopBar, type.heading)
            text("GEO-X-1", width - 64f, 8f, c.onTopBar, type.titleSm)
            rect(c.surface, 8f, 34f, width - 16f, 48f)
            text("수집 구간", 16f, 36f, c.text, type.titleSm)
            text("GEO-X-1 · 수집 준비", 16f, 52f, c.textSecondary, type.caption)
            rect(c.neutralSoft, width - 46f, 38f, 30f, 16f)
            text("할 일", width - 41f, 40f, c.neutral, type.badge)
            text("저장 공간 · 512 GB 남음", 16f, 67f, c.text)
            text("예시 입력", 12f, 88f, c.textSecondary, type.label)
            val fieldWidth = (width - 32f) * .52f
            rect(c.field, 12f, 102f, fieldWidth, 26f)
            drawRect(c.borderStrong, Offset(12.dp.toPx(), 102.dp.toPx()),
                Size(fieldWidth.dp.toPx(), 26.dp.toPx()), style = Stroke(1.dp.toPx()))
            val buttonX = fieldWidth + 20f
            rect(c.primary, buttonX, 102f, width - buttonX - 12f, 26f)
            text("수집 시작", buttonX + 10f, 108f, c.onPrimary, type.button)
            rect(c.nav, 0f, 136f, width, 24f)
            listOf("작업", "장치", "전송", "설정").forEachIndexed { index, label ->
                text(label, width * (index + .5f) / 4f - 8f, 142f,
                    if (index == 0) c.navSelected else c.navUnselected, if (index == 0) type.tabSelected else type.tab)
            }
        }
    }
}
