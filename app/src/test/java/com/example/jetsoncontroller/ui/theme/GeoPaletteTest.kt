@file:Suppress("DEPRECATION")

package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

class GeoPaletteTest {
    @Test
    fun `every palette has every role and the exact R3 table values`() {
        val rows = expected.trimIndent().lines().map { it.split(" ") }
        val roleNames = rows.map { it.first() }.toSet() + setOf("info", "infoSoft", "focus", "switchTrackOn")
        GeoPalette.entries.forEachIndexed { index, palette ->
            val roles = palette.colors.asRoleMap()
            assertEquals(palette.name, roleNames, roles.keys)
            rows.forEach { cells ->
                val hex = cells[index + 1].removePrefix("#")
                val argb = (if (hex.length == 6) "FF$hex" else hex).toLong(16).toInt()
                assertEquals("${palette.name}.${cells.first()}", argb, roles.getValue(cells.first()).toArgb())
            }
            assertEquals(roles["primary"], roles["info"])
            assertEquals(roles["primaryTint"], roles["infoSoft"])
            assertEquals(roles["primary"], roles["focus"])
            assertEquals(roles["primary"], roles["switchTrackOn"])
        }
    }

    @Test
    fun `defaults and system bar tones follow the selected palette`() {
        assertEquals(GeoPalette.STANDARD, GeoPalette.defaultFor(false))
        assertEquals(GeoPalette.GRAPHITE, GeoPalette.defaultFor(true))
        assertEquals(listOf("표준", "네이비 (밝게)", "고대비", "그래파이트", "네이비 (어둡게)", "흐림"), GeoPalette.entries.map { it.label })
        GeoPalette.entries.forEach { palette ->
            assertEquals(palette.isDark || palette == GeoPalette.NAVY_LIGHT, palette.lightStatusBarIcons)
            assertEquals(palette.lightStatusBarIcons, palette.lightNavigationBarIcons)
            assertEquals(palette.isDark, palette.lightSurfaceBarIcons)
        }
    }

    // R3-final §4.2, in enum order. Includes alpha and absent chrome hairlines.
    private val expected = """
        canvas #EDF0F5 #EDF0F5 #FFFFFF #161719 #13171F #26282B
        surface #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
        sunken #F3F5F8 #F3F5F8 #F2F4F7 #191B1D #171B23 #2A2B2E
        field #F1F3F7 #F1F3F7 #F2F4F7 #292A2C #262A33 #3A3C3F
        fieldPressed #E9ECF2 #E9ECF2 #E6E9EF #303234 #2D323A #424447
        text #151B31 #151B31 #0B1022 #E8E9EC #E6E9F0 #EBEDEF
        textSecondary #4B5570 #4B5570 #283045 #BDBFC4 #BBBFC8 #CACCD0
        textSubtle #5F6880 #5F6880 #3B4256 #9EA2A7 #9DA1AB #B3B6BB
        border #E7EAF0 #E7EAF0 #C3C9D4 #323336 #2F333C #444649
        borderStrong #838891 #838891 #4D5466 #7E8289 #7D828D #90949A
        primary #1F52B8 #1F52B8 #163E8F #8AADEC #8BACEF #9ABBF6
        primaryPressed #1A47A0 #1A47A0 #103170 #A8C3F4 #A9C3F7 #B9D2FE
        onPrimary #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
        primaryTint #E7EEFB #E7EEFB #E3EBF9 #242E40 #242F46 #374154
        primaryTintText #1A47A0 #1A47A0 #103170 #B9CFF5 #BBCEF5 #C1D7FD
        focusHalo #3D1F52B8 #3D1F52B8 #52163E8F #528AADEC #528BACEF #529ABBF6
        success #22704B #22704B #0E5634 #88CCA5 #88CCA5 #91D6AE
        successSoft #E2F1E9 #E2F1E9 #DFF0E6 #203026 #203026 #314238
        successBorder #A9D3BD #A9D3BD #0E5634 #376048 #376048 #477159
        warning #8A5A06 #8A5A06 #5F3D00 #E3BC7A #E3BC7A #EDC684
        warningSoft #FBF0DA #FBF0DA #FAEDD3 #332A1C #332A1C #453C2D
        warningBorder #ECCA8C #ECCA8C #5F3D00 #67522D #67522D #78623D
        danger #B3372C #B3372C #8B1E15 #E9938C #E9938C #F39D95
        dangerPressed #9A2E25 #9A2E25 #6F150E #F0A9A3 #F0A9A3 #F7B2AB
        onDanger #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
        dangerSoft #FBE8E5 #FBE8E5 #FBE4E1 #392626 #392626 #4B3837
        dangerBorder #F0B8B1 #F0B8B1 #8B1E15 #734844 #734844 #855854
        infoBorder #B9CBEF #B9CBEF #163E8F #435B85 #455A85 #546C97
        pending #3D3A86 #3D3A86 #2C2970 #CCC8F3 #CCC8F3 #D5D0FC
        pendingSoft #E4E3F4 #E4E3F4 #E5E4F5 #2D2B3F #2D2B3F #3F3D52
        pendingBorder #C4C1E8 #C4C1E8 #2C2970 #565176 #565176 #676288
        neutral #5D667B #5D667B #3D4456 #ADB1B9 #ACB1BD #BBBFC7
        neutralSoft #EEF0F4 #EEF0F4 #ECEEF2 #2C2E31 #292E38 #3D4044
        neutralBorder #D9DEE7 #D9DEE7 #3D4456 #46494E #444955 #56595F
        disabledFill #E9ECF2 #E9ECF2 #E6E9EF #292A2C #262A33 #3A3C3F
        disabledText #5F6880 #5F6880 #555D70 #9EA2A7 #9DA1AB #B3B6BB
        switchTrackOff #7D8598 #7D8598 #5E6577 #7E8289 #7D828D #90949A
        switchThumbOff #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
        switchThumbOn #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
        topBar #FFFFFF #121A3D #FFFFFF #1E1F21 #1B1F28 #2F3033
        onTopBar #151B31 #FFFFFF #0B1022 #E8E9EC #E6E9F0 #EBEDEF
        onTopBarIcon #4B5570 #AEB9D9 #283045 #BDBFC4 #BBBFC8 #CACCD0
        topBarBorder #E7EAF0 #00000000 #4D5466 #323336 #2F333C #444649
        nav #FFFFFF #121A3D #FFFFFF #0E0F11 #0C0F17 #1E1F22
        navSelected #1F52B8 #FFFFFF #163E8F #A8C3F4 #A9C3F7 #B9D2FE
        navUnselected #5F6880 #AEB9D9 #5A6276 #9EA2A7 #9DA1AB #B3B6BB
        navBorder #E7EAF0 #00000000 #4D5466 #00000000 #00000000 #00000000
        navFocus #1F52B8 #8FB0F0 #163E8F #9BBAF0 #9DB9F0 #A5C3FA
        attention #D0342C #F08F86 #B3261E #F08F86 #F08F86 #FB988F
        onAttention #FFFFFF #121A3D #FFFFFF #161719 #13171F #26282B
        chromeSuccess #22704B #88CCA5 #0E5634 #88CCA5 #88CCA5 #91D6AE
        chromeWarning #8A5A06 #E3BC7A #5F3D00 #E3BC7A #E3BC7A #EDC684
        chromeDanger #B3372C #F08F86 #8B1E15 #E9938C #E9938C #F39D95
        chromeInfo #1F52B8 #8FB0F0 #163E8F #8AADEC #8BACEF #9ABBF6
        chromePending #3D3A86 #CCC8F3 #2C2970 #CCC8F3 #CCC8F3 #D5D0FC
        chromeNeutral #5D667B #AEB9D9 #3D4456 #ADB1B9 #ACB1BD #BBBFC7
        inverse #151B31 #151B31 #0B1022 #3D3E41 #3A3E48 #4F5155
        onInverse #FFFFFF #FFFFFF #FFFFFF #E8E9EC #E6E9F0 #EBEDEF
        inverseAction #8FB0F0 #8FB0F0 #A9C3F7 #A8C3F4 #A9C3F7 #B9D2FE
        scrim #52121A3D #52121A3D #7A0B1022 #99050608 #9904060E #99060709
        mapPlate #FFFFFF #FFFFFF #FFFFFF #1E1F21 #1B1F28 #2F3033
    """
}
