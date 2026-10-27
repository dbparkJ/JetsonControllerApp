package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemeScreenStateTest {
    @Test
    fun `picker offers exactly three distinct palettes in each labelled mode group`() {
        assertEquals(listOf(GeoPalette.STANDARD, GeoPalette.NAVY_LIGHT, GeoPalette.HIGH_CONTRAST), lightThemeOptions.map { it.palette })
        assertEquals(listOf(GeoPalette.GRAPHITE, GeoPalette.NAVY_DARK, GeoPalette.MIST), darkThemeOptions.map { it.palette })
        assertEquals(listOf("표준", "네이비", "고대비"), lightThemeOptions.map { it.label })
        assertEquals(listOf("그래파이트", "네이비", "흐림"), darkThemeOptions.map { it.label })
        assertEquals(listOf("시스템 따름", "밝게", "어둡게"), ThemeMode.entries.map { it.label })
        assertEquals(listOf(
            "GEO& Work와 같은 기본 화면입니다.", "위아래 막대를 네이비로 칠한 화면입니다.",
            "햇빛 아래에서 읽기 쉽도록 글자와 선을 진하게 합니다.", "기본 어두운 화면입니다.",
            "푸른빛이 도는 어두운 화면입니다.", "밝기 차이를 줄인 회색 화면입니다."
        ), (lightThemeOptions + darkThemeOptions).map { it.description })
    }

    @Test
    fun `all modes resolve the selected palette and only the unused group gets a caption`() {
        ThemeMode.entries.forEach { mode ->
            listOf(false, true).forEach { systemDark ->
                lightThemeOptions.forEach { light -> darkThemeOptions.forEach { dark ->
                    val appearance = Appearance(mode, light.palette, dark.palette)
                    val state = ThemeScreenState(appearance, systemDark)
                    val darkVisible = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && systemDark)
                    assertEquals(if (darkVisible) dark.palette else light.palette, state.previewPalette)
                    assertEquals(if (darkVisible) "밝은 모드에서 사용합니다." else null, state.lightGroupCaption)
                    assertEquals(if (darkVisible) null else "어두운 모드에서 사용합니다.", state.darkGroupCaption)
                    assertEquals(if (mode == ThemeMode.SYSTEM) "기기 설정에 따라 밝은 테마와 어두운 테마를 바꿉니다." else null,
                        state.modeCaption)
                } }
            }
        }
    }

    @Test
    fun `choosing another mode palette preserves preview and returns the saved-for-other-mode snackbar`() {
        listOf(false, true).forEach { systemDark ->
            ThemeMode.entries.forEach { mode ->
                val before = Appearance(mode, GeoPalette.HIGH_CONTRAST, GeoPalette.MIST)
                val state = ThemeScreenState(before, systemDark)
                val other = if (state.previewPalette.isDark) GeoPalette.NAVY_LIGHT else GeoPalette.NAVY_DARK
                val selected = state.selectPalette(other)
                assertEquals(before.mode, selected.appearance.mode)
                assertEquals(state.previewPalette, selected.appearance.palette(systemDark))
                assertEquals(other, selected.appearance.paletteForDarkTheme(other.isDark))
                assertEquals(if (other.isDark) "어두운 모드에서 쓸 테마로 저장했습니다." else "밝은 모드에서 쓸 테마로 저장했습니다.",
                    selected.snackbar)
            }
        }
    }

    @Test
    fun `active palette changes immediately without the other-mode snackbar and keeps the other selection`() {
        val before = Appearance(lightPalette = GeoPalette.HIGH_CONTRAST, darkPalette = GeoPalette.MIST)
        listOf(false, true).forEach { systemDark ->
            val palette = if (systemDark) GeoPalette.NAVY_DARK else GeoPalette.NAVY_LIGHT
            val selected = ThemeScreenState(before, systemDark).selectPalette(palette)
            assertEquals(palette, selected.appearance.palette(systemDark))
            assertEquals(before.paletteForDarkTheme(!systemDark), selected.appearance.paletteForDarkTheme(!systemDark))
            assertNull(selected.snackbar)
        }
    }

    @Test
    fun `each swatch uses its own real surface text primary navigation and boundary tokens`() {
        (lightThemeOptions + darkThemeOptions).forEach { option ->
            val c = option.palette.colors
            assertEquals(ThemeSwatch(c.surface, c.text, c.primary, c.nav, c.borderStrong), option.swatch)
        }
        assertNotEquals(lightThemeOptions[0].swatch.nav, lightThemeOptions[1].swatch.nav)
        assertEquals(6, (lightThemeOptions + darkThemeOptions).map { it.swatch }.distinct().size)
    }

    @Test
    fun `settings summaries describe both system palettes and the selected explicit mode`() {
        assertEquals("시스템 · 표준 / 그래파이트", Appearance().summary)
        val chosen = Appearance(ThemeMode.SYSTEM, GeoPalette.HIGH_CONTRAST, GeoPalette.NAVY_DARK)
        assertEquals("시스템 · 고대비 / 네이비", chosen.summary)
        assertEquals("밝게 · 고대비", chosen.copy(mode = ThemeMode.LIGHT).summary)
        assertEquals("어둡게 · 네이비", chosen.copy(mode = ThemeMode.DARK).summary)
    }

    @Test
    fun `each system bar follows the surface below it including navy light sheets and focused flows`() {
        GeoPalette.entries.forEach { palette ->
            listOf(false, true).forEach { surfaceInset ->
                val bars = systemBarAppearance(palette, surfaceInset)
                assertEquals(palette.colors.canvas, bars.statusBar)
                assertEquals(if (surfaceInset) palette.colors.surface else palette.colors.canvas, bars.navigationBar)
                assertEquals(!palette.isDark, bars.darkStatusIcons)
                assertEquals(!palette.isDark, bars.darkNavigationIcons)
            }
        }
    }
}
