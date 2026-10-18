package com.example.jetsoncontroller.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * GEO& Field System — semantic colour roles (layer 2).
 *
 * Every role below answers "what does this colour mean", not "what colour is it".
 * The previous *Slate Harmony* roles are preserved by name so that screens which have
 * not been migrated keep compiling and immediately inherit the corrected values; the
 * roles added underneath them are what new work should use.
 *
 * Two corrections drive this file:
 *
 *  1. `success` used to be `#525862` — the same grey as `muted`. A verified-good state
 *     was visually identical to secondary text, which is the exact failure mode the
 *     product brief warns about ("연결됐으니 저장도 됐다고 오해한다"). Status now owns
 *     real hues, held to a low chroma so the screen still reads as calm.
 *  2. There was no role for *not knowing*. The brief's state table repeatedly demands
 *     "미확인 / 오래됨 / 결과 확인 필요", which must never be painted as success or as
 *     failure. `pending` and `unknown` exist for exactly that.
 */
@Immutable
data class GeoColors(
    // ---- Surfaces ---------------------------------------------------------------
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val subtle: Color,
    val overlay: Color,

    // ---- Text -------------------------------------------------------------------
    val ink: Color,
    val inkStrong: Color,
    val muted: Color,
    val inkSubtle: Color,

    // ---- Lines ------------------------------------------------------------------
    val border: Color,
    val borderStrong: Color,
    val controlBorder: Color,
    val focusRing: Color,

    // ---- Brand / interactive ----------------------------------------------------
    val primary: Color,
    val onPrimary: Color,
    val primaryPressed: Color,
    val brandSoft: Color,
    val onBrandSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val accentGreen: Color,

    // ---- Hero / brand surface ---------------------------------------------------
    val hero: Color,
    val heroText: Color,
    val heroMuted: Color,

    // ---- Status: positive (확인됨) -------------------------------------------------
    val success: Color,
    val successBg: Color,
    val successBorder: Color,
    val onSuccess: Color,

    // ---- Status: caution (주의) ----------------------------------------------------
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val onWarning: Color,

    // ---- Status: critical (차단·실패) -----------------------------------------------
    val danger: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val onDanger: Color,

    // ---- Status: informational (안내) -----------------------------------------------
    val info: Color,
    val infoBg: Color,
    val infoBorder: Color,

    // ---- Status: pending (처리 중 · 결과 대기) ----------------------------------------
    val pending: Color,
    val pendingBg: Color,
    val pendingBorder: Color,

    // ---- Status: unknown (미확인 · 오래됨 · 캐시) --------------------------------------
    val unknown: Color,
    val unknownBg: Color,
    val unknownBorder: Color,

    // ---- Disabled ---------------------------------------------------------------
    val disabled: Color,
    val onDisabled: Color,

    // ---- Section surfaces (legacy role names, retained) -------------------------
    val sectionBase: Color,
    val sectionSoft: Color,
    val sectionRaised: Color,
    val sectionDanger: Color,
    val sectionBorder: Color,

    // ---- Navigation -------------------------------------------------------------
    val navSelected: Color,
    val onNavSelected: Color
)

// Values follow GEO& Work tokens.css (main 10d6f1af); see GeoPalette for the source names.
val GeoLight = GeoColors(
    canvas = GeoPalette.WorkBg,
    surface = GeoPalette.WorkSurface,
    surfaceRaised = GeoPalette.WorkSurface,
    surfaceSunken = GeoPalette.WorkSurfaceSunken,
    subtle = GeoPalette.WorkFieldHover,
    // --color-scrim: navy at 32%.
    overlay = Color(0x52121A3D),

    ink = GeoPalette.WorkText,
    inkStrong = GeoPalette.WorkText,
    muted = GeoPalette.WorkTextSecondary,
    inkSubtle = GeoPalette.WorkTextSubtle,

    border = GeoPalette.WorkBorder,
    borderStrong = GeoPalette.WorkDivider,
    // 3.56:1 against the white surface, above the 3:1 minimum for a control boundary.
    controlBorder = GeoPalette.WorkControlBorder,
    focusRing = GeoPalette.WorkPrimary,

    primary = GeoPalette.WorkPrimary,
    onPrimary = GeoPalette.WorkSurface,
    primaryPressed = GeoPalette.WorkPrimaryHover,
    brandSoft = GeoPalette.WorkPrimaryTint,
    onBrandSoft = GeoPalette.WorkPrimaryHover,
    accent = GeoPalette.WorkPrimaryTint,
    onAccent = GeoPalette.WorkPrimaryHover,
    accentGreen = GeoPalette.BrandGreen,

    hero = GeoPalette.WorkRail,
    heroText = GeoPalette.WorkSurface,
    heroMuted = GeoPalette.WorkRailText,

    success = GeoPalette.WorkSuccess,
    successBg = GeoPalette.WorkSuccessSoft,
    successBorder = GeoPalette.WorkSuccessBorder,
    onSuccess = GeoPalette.WorkSurface,

    warning = GeoPalette.WorkWarning,
    warningBg = GeoPalette.WorkWarningSoft,
    warningBorder = GeoPalette.WorkWarningBorder,
    onWarning = GeoPalette.WorkSurface,

    danger = GeoPalette.WorkDanger,
    dangerBg = GeoPalette.WorkDangerSoft,
    dangerBorder = GeoPalette.WorkDangerBorder,
    onDanger = GeoPalette.WorkSurface,

    // GEO& Work uses its primary blue for information.
    info = GeoPalette.WorkPrimary,
    infoBg = GeoPalette.WorkPrimaryTint,
    infoBorder = GeoPalette.WorkPrimarySoftBorder,

    pending = GeoPalette.WorkViolet,
    pendingBg = GeoPalette.WorkVioletSoft,
    pendingBorder = GeoPalette.WorkVioletBorder,

    unknown = GeoPalette.WorkTodo,
    unknownBg = GeoPalette.WorkTodoSoft,
    unknownBorder = GeoPalette.WorkTodoBorder,

    disabled = GeoPalette.WorkFieldHover,
    // 4.70:1 on the disabled surface. A disabled control still has to say what it is,
    // because this product disables the start button far more often than it enables it.
    onDisabled = GeoPalette.WorkTextSubtle,

    sectionBase = GeoPalette.WorkSurface,
    sectionSoft = GeoPalette.WorkSurfaceSubtle,
    sectionRaised = GeoPalette.WorkFieldHover,
    sectionDanger = GeoPalette.WorkDangerSoft,
    sectionBorder = GeoPalette.WorkBorder,

    navSelected = GeoPalette.WorkPrimaryTint,
    onNavSelected = GeoPalette.WorkPrimaryHover
)

val GeoDark = GeoColors(
    canvas = GeoPalette.WorkDarkBg,
    surface = GeoPalette.WorkDarkSurface,
    surfaceRaised = GeoPalette.WorkDarkSurfaceSubtle,
    surfaceSunken = GeoPalette.WorkDarkSurfaceSunken,
    subtle = GeoPalette.WorkDarkField,
    // --color-scrim (dark): near-black at 60%.
    overlay = Color(0x99050608),

    ink = GeoPalette.WorkDarkText,
    inkStrong = GeoPalette.WorkDarkTextStrong,
    muted = GeoPalette.WorkDarkTextSecondary,
    inkSubtle = GeoPalette.WorkDarkTextSubtle,

    border = GeoPalette.WorkDarkBorder,
    borderStrong = GeoPalette.WorkDarkDivider,
    controlBorder = GeoPalette.WorkDarkControlBorder,
    focusRing = GeoPalette.WorkDarkPrimary,

    primary = GeoPalette.WorkDarkPrimary,
    // GEO& Work fills dark-theme primary buttons with surface-coloured text.
    onPrimary = GeoPalette.WorkDarkSurface,
    primaryPressed = GeoPalette.WorkDarkPrimaryHover,
    brandSoft = GeoPalette.WorkDarkPrimaryTint,
    onBrandSoft = GeoPalette.WorkDarkPrimarySoftText,
    accent = GeoPalette.WorkDarkPrimaryTint,
    onAccent = GeoPalette.WorkDarkPrimarySoftText,
    accentGreen = GeoPalette.BrandGreenLight,

    hero = GeoPalette.WorkDarkRail,
    heroText = GeoPalette.WorkDarkText,
    heroMuted = GeoPalette.WorkDarkRailText,

    success = GeoPalette.WorkDarkSuccess,
    successBg = GeoPalette.WorkDarkSuccessSoft,
    successBorder = GeoPalette.WorkDarkSuccessBorder,
    onSuccess = GeoPalette.WorkDarkBg,

    warning = GeoPalette.WorkDarkWarning,
    warningBg = GeoPalette.WorkDarkWarningSoft,
    warningBorder = GeoPalette.WorkDarkWarningBorder,
    onWarning = GeoPalette.WorkDarkBg,

    danger = GeoPalette.WorkDarkDanger,
    dangerBg = GeoPalette.WorkDarkDangerSoft,
    dangerBorder = GeoPalette.WorkDarkDangerBorder,
    onDanger = GeoPalette.WorkDarkBg,

    info = GeoPalette.WorkDarkPrimary,
    infoBg = GeoPalette.WorkDarkPrimaryTint,
    infoBorder = GeoPalette.WorkDarkPrimarySoftBorder,

    pending = GeoPalette.WorkDarkViolet,
    pendingBg = GeoPalette.WorkDarkVioletSoft,
    pendingBorder = GeoPalette.WorkDarkVioletBorder,

    unknown = GeoPalette.WorkDarkTodo,
    unknownBg = GeoPalette.WorkDarkTodoSoft,
    unknownBorder = GeoPalette.WorkDarkDivider,

    disabled = GeoPalette.WorkDarkField,
    onDisabled = GeoPalette.WorkDarkTextSubtle, // 5.6:1 on the disabled surface

    sectionBase = GeoPalette.WorkDarkSurface,
    sectionSoft = GeoPalette.WorkDarkSurfaceSubtle,
    sectionRaised = GeoPalette.WorkDarkField,
    sectionDanger = GeoPalette.WorkDarkDangerSoft,
    sectionBorder = GeoPalette.WorkDarkBorder,

    navSelected = GeoPalette.WorkDarkPrimaryTint,
    onNavSelected = GeoPalette.WorkDarkPrimarySoftText
)

val LocalGeoColors = staticCompositionLocalOf { GeoLight }

// ---------------------------------------------------------------------------------
// Compatibility aliases.
//
// The whole app previously read `LocalCobaltColors.current`. Keeping the alias means
// the redesign lands everywhere at once instead of leaving half the product on the old
// palette, and lets screens migrate to `LocalGeoColors` file by file.
// ---------------------------------------------------------------------------------

typealias CobaltColors = GeoColors

val CobaltLight: GeoColors = GeoLight
val CobaltDark: GeoColors = GeoDark
val LocalCobaltColors = LocalGeoColors
