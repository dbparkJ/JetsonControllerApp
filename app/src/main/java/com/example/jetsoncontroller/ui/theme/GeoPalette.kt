package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GEO& Field System — raw palette.
 *
 * Layer 1 of the token system. These are *values*, never used directly by a screen.
 * Screens read semantic roles from [GeoColors] (`LocalGeoColors.current`) so that a
 * value change here can never silently change what a colour *means*.
 *
 * Values are copied from the company app GEO& Work (formerly GEON Work):
 * `apps/web/src/shared/ui/tokens.css` at GEON Work main 10d6f1af (2026-10-16). The light
 * values come from `:root`, the dark values from its default dark palette (graphite,
 * `:root[data-theme="dark"]`). GEO& Work has no JSON export, so copy again from a newer
 * commit and update the SHA here and in docs/design/STYLE_GUIDE.md when it changes.
 */
internal object GeoPalette {

    // ---- Light: surfaces ------------------------------------------------------------
    /** --color-bg */
    val WorkBg = Color(0xFFEDF0F5)
    /** --color-surface */
    val WorkSurface = Color(0xFFFFFFFF)
    /** --color-surface-subtle */
    val WorkSurfaceSubtle = Color(0xFFF7F8FB)
    /** --color-surface-sunken */
    val WorkSurfaceSunken = Color(0xFFF3F5F8)
    /** --color-field-hover; also the app's chip, track and disabled fill. */
    val WorkFieldHover = Color(0xFFE9ECF2)

    // ---- Light: text ----------------------------------------------------------------
    /** --color-text */
    val WorkText = Color(0xFF151B31)
    /** --color-text-secondary */
    val WorkTextSecondary = Color(0xFF4B5570)
    /** --color-text-subtle */
    val WorkTextSubtle = Color(0xFF5F6880)

    // ---- Light: lines ---------------------------------------------------------------
    /** --color-border */
    val WorkBorder = Color(0xFFE7EAF0)
    /** --color-sidebar-divider; the stronger decorative line. */
    val WorkDivider = Color(0xFFD3D9E3)
    /** --color-border-strong / --color-input-border: 3:1 boundary for controls. */
    val WorkControlBorder = Color(0xFF838891)

    // ---- Light: brand ---------------------------------------------------------------
    /** --color-primary */
    val WorkPrimary = Color(0xFF1F52B8)
    /** --color-primary-hover / --color-primary-soft-text */
    val WorkPrimaryHover = Color(0xFF1A47A0)
    /** --color-primary-tint / --color-primary-soft */
    val WorkPrimaryTint = Color(0xFFE7EEFB)
    /** --color-primary-soft-border */
    val WorkPrimarySoftBorder = Color(0xFFB9CBEF)
    /** --color-rail: the navy chrome. Used for the device card's next-action band. */
    val WorkRail = Color(0xFF121A3D)
    /** --color-rail-text */
    val WorkRailText = Color(0xFFAEB9D9)

    // ---- Light: status --------------------------------------------------------------
    val WorkSuccess = Color(0xFF22704B)
    val WorkSuccessSoft = Color(0xFFE2F1E9)
    val WorkSuccessBorder = Color(0xFFA9D3BD)
    val WorkWarning = Color(0xFF8A5A06)
    val WorkWarningSoft = Color(0xFFFBF0DA)
    val WorkWarningBorder = Color(0xFFECCA8C)
    val WorkDanger = Color(0xFFB3372C)
    val WorkDangerSoft = Color(0xFFFBE8E5)
    val WorkDangerBorder = Color(0xFFF0B8B1)
    /** --color-todo: neutral state text, used for "미확인". */
    val WorkTodo = Color(0xFF5D667B)
    /** --color-todo-soft */
    val WorkTodoSoft = Color(0xFFEEF0F4)
    /** --option-1-border */
    val WorkTodoBorder = Color(0xFFD9DEE7)
    /** --option-6-*: GEO& Work has no "processing" status, so "처리 중" uses its violet option. */
    val WorkViolet = Color(0xFF3D3A86)
    val WorkVioletSoft = Color(0xFFE4E3F4)
    val WorkVioletBorder = Color(0xFFC4C1E8)

    // ---- Dark (graphite): surfaces ----------------------------------------------------
    val WorkDarkBg = Color(0xFF161719)
    val WorkDarkSurface = Color(0xFF1E1F21)
    val WorkDarkSurfaceSubtle = Color(0xFF222426)
    val WorkDarkSurfaceSunken = Color(0xFF191B1D)
    /** --color-field */
    val WorkDarkField = Color(0xFF292A2C)

    // ---- Dark: text -----------------------------------------------------------------
    val WorkDarkText = Color(0xFFE8E9EC)
    /** --color-ink-hover: the strongest text. */
    val WorkDarkTextStrong = Color(0xFFFFFFFF)
    val WorkDarkTextSecondary = Color(0xFFBDBFC4)
    val WorkDarkTextSubtle = Color(0xFF9EA2A7)

    // ---- Dark: lines ----------------------------------------------------------------
    val WorkDarkBorder = Color(0xFF323336)
    /** --option-1-border (dark) */
    val WorkDarkDivider = Color(0xFF46494E)
    val WorkDarkControlBorder = Color(0xFF7E8289)

    // ---- Dark: brand ----------------------------------------------------------------
    val WorkDarkPrimary = Color(0xFF8AADEC)
    val WorkDarkPrimaryHover = Color(0xFFA8C3F4)
    val WorkDarkPrimaryTint = Color(0xFF242E40)
    val WorkDarkPrimarySoftText = Color(0xFFB9CFF5)
    val WorkDarkPrimarySoftBorder = Color(0xFF435B85)
    val WorkDarkRail = Color(0xFF0E0F11)
    val WorkDarkRailText = Color(0xFFB3B6BB)

    // ---- Dark: status ---------------------------------------------------------------
    val WorkDarkSuccess = Color(0xFF88CCA5)
    val WorkDarkSuccessSoft = Color(0xFF203026)
    val WorkDarkSuccessBorder = Color(0xFF376048)
    val WorkDarkWarning = Color(0xFFE3BC7A)
    val WorkDarkWarningSoft = Color(0xFF332A1C)
    val WorkDarkWarningBorder = Color(0xFF67522D)
    val WorkDarkDanger = Color(0xFFE9938C)
    val WorkDarkDangerSoft = Color(0xFF392626)
    val WorkDarkDangerBorder = Color(0xFF734844)
    val WorkDarkTodo = Color(0xFFADB1B9)
    val WorkDarkTodoSoft = Color(0xFF2C2E31)
    val WorkDarkViolet = Color(0xFFCCC8F3)
    val WorkDarkVioletSoft = Color(0xFF2D2B3F)
    val WorkDarkVioletBorder = Color(0xFF565176)

    // ---- GEO& symbol: ampersand green (brand asset, not part of tokens.css) ------------
    val BrandGreen = Color(0xFF29674E)
    val BrandGreenLight = Color(0xFF64B993)
}
