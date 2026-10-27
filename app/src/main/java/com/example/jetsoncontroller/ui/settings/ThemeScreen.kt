package com.example.jetsoncontroller.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.jetsoncontroller.ui.components.geo.GeoRadioRow
import com.example.jetsoncontroller.ui.dev.DevSection as GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoGroupedSection
import com.example.jetsoncontroller.ui.components.geo.GeoSheetKind
import com.example.jetsoncontroller.ui.components.geo.geoPressFeedback
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
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
    val swatch: ThemeSwatch get() = palette.colors.let { ThemeSwatch(it.canvas, it.text, it.primary, it.surface, it.borderStrong) }
}

internal data class ThemeSwatch(val surface: Color, val text: Color, val primary: Color, val nav: Color, val border: Color)

internal val lightThemeOptions = listOf(
    ThemePaletteOption(GeoPalette.STANDARD, "GEO& Work와 같은 기본 화면입니다."),
    ThemePaletteOption(GeoPalette.NAVY_LIGHT, "푸른빛이 도는 밝은 화면입니다."),
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
    val gutter = window.gutterDp.dp
    GeoSystemBars(navigationBarUsesSurface = !window.rail || modePickerOpen)
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
        GeoLargeTitleScaffold("화면 테마", onBack = onBack) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = gutter).padding(bottom = 24.dp)) {
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
                GeoThemePaletteGroup("밝은 테마", lightThemeOptions, appearance.lightPalette, state.lightGroupCaption, ::save)
                GeoThemePaletteGroup("어두운 테마", darkThemeOptions, appearance.darkPalette, state.darkGroupCaption, ::save)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { GeoSnackbar(snackbar) }
    }
    if (modePickerOpen) {
        GeoSheet(title = "모드", onDismissRequest = { modePickerOpen = false }, kind = GeoSheetKind.CHOICE) {
            Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "모드" }) {
                ThemeMode.entries.forEach { mode ->
                    GeoRadioRow(mode.label, appearance.mode == mode, { saveMode(mode) })
                }
            }
        }
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
    Column {
    GeoGroupedSection(label) {
        if (LocalGeoWindow.current.widthDp >= 600f) BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val columns = themeGridColumns(maxWidth.value, LocalDensity.current.fontScale)
            Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { option ->
                            GeoThemePaletteTile(option, option.palette == selected, Modifier.weight(1f)) { onSelect(option.palette) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }) {
            options.forEach { option -> GeoThemePaletteRow(option, option.palette == selected) { onSelect(option.palette) } }
        }
    }
    caption?.let { Text(it, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.supporting) }
    }
}

@Composable
private fun GeoThemePaletteRow(option: ThemePaletteOption, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalGeoColors.current
    GeoRadioRow(option.label, selected, onClick, modifier = Modifier
        .background(if (selected) colors.primaryTint else colors.surface).padding(horizontal = 16.dp),
        description = option.description, badge = { GeoThemeSwatch(option.swatch) })
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

/** Decorative miniature follows the same palette roles without scaling real UI text. */
@Composable
private fun GeoThemePreview(palette: GeoPalette) {
    val c = palette.colors
    val today = geoWorkCardColors(palette)
    Canvas(Modifier.fillMaxWidth().height(160.dp).clip(GeoShape.card).border(1.dp, c.border, GeoShape.card)
        .clearAndSetSemantics { contentDescription = "${palette.label} 미리 보기" }) {
        fun rect(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float = 0f) {
            drawRoundRect(color, Offset(x.dp.toPx(), y.dp.toPx()), Size(width.dp.toPx(), height.dp.toPx()),
                CornerRadius(radius.dp.toPx()))
        }
        val width = size.width.toDp().value
        val content = width - 32f
        rect(c.canvas, 0f, 0f, width, 160f)
        rect(c.text, 16f, 14f, content * .35f, 8f, 4f)
        rect(today.background, 16f, 34f, content, 48f, 12f)
        rect(today.content, 28f, 46f, content * .5f, 6f, 3f)
        rect(today.content, 28f, 60f, content * .3f, 4f, 2f)
        rect(c.primary, 16f, 94f, content, 22f, 8f)
        rect(c.onPrimary, width / 2f - content * .12f, 103f, content * .24f, 4f, 2f)
        rect(c.surface, 0f, 128f, width, 32f)
        repeat(4) { index ->
            val center = width * (index + .5f) / 4f
            if (index == 0) rect(c.primaryTint, center - 16f, 132f, 32f, 16f, 8f)
            drawCircle(if (index == 0) c.primary else c.textSubtle, 4.dp.toPx(),
                Offset(center.dp.toPx(), 140.dp.toPx()))
        }
    }
}

internal fun themeGridColumns(widthDp: Float, fontScale: Float): Int =
    com.example.jetsoncontroller.ui.components.geo.shell.GeoTabletRules.themeColumns(widthDp, fontScale)

@Composable
private fun GeoThemePaletteTile(option: ThemePaletteOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val source = remember { MutableInteractionSource() }
    Column(modifier.clip(GeoShape.card).background(colors.surface)
        .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.border, GeoShape.card)
        .geoPressFeedback(source, background = if (selected) colors.primaryTint else colors.surface)
        .selectable(selected, source, indication = null, role = Role.RadioButton, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GeoThemePreview(option.palette)
        Text(option.label, color = colors.text, style = type.titleSm)
        Text(option.description, color = colors.textSecondary, style = type.caption)
        if (selected) Text("현재 적용", color = colors.primary, style = type.caption)
    }
}
