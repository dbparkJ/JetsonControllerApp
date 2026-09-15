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

val GeoLight = GeoColors(
    canvas = GeoPalette.N50,
    surface = GeoPalette.N0,
    surfaceRaised = GeoPalette.N0,
    surfaceSunken = GeoPalette.N100,
    subtle = GeoPalette.N100,
    overlay = Color(0x66101822),

    ink = GeoPalette.N800,
    inkStrong = GeoPalette.N900,
    muted = GeoPalette.N600,
    inkSubtle = GeoPalette.N500,

    border = GeoPalette.N200,
    borderStrong = GeoPalette.N300,
    controlBorder = GeoPalette.N400,
    focusRing = GeoPalette.Brand500,

    primary = GeoPalette.Brand500,
    onPrimary = GeoPalette.N0,
    primaryPressed = GeoPalette.Brand700,
    brandSoft = GeoPalette.Brand50,
    onBrandSoft = GeoPalette.Brand700,
    accent = GeoPalette.Brand100,
    onAccent = GeoPalette.Brand700,
    accentGreen = GeoPalette.BrandGreen,

    hero = GeoPalette.Brand700,
    heroText = GeoPalette.N0,
    heroMuted = GeoPalette.Brand200,

    success = GeoPalette.PositiveLightFg,
    successBg = GeoPalette.PositiveLightBg,
    successBorder = GeoPalette.PositiveLightBorder,
    onSuccess = GeoPalette.N0,

    warning = GeoPalette.CautionLightFg,
    warningBg = GeoPalette.CautionLightBg,
    warningBorder = GeoPalette.CautionLightBorder,
    onWarning = GeoPalette.N0,

    danger = GeoPalette.CriticalLightFg,
    dangerBg = GeoPalette.CriticalLightBg,
    dangerBorder = GeoPalette.CriticalLightBorder,
    onDanger = GeoPalette.N0,

    info = GeoPalette.Brand600,
    infoBg = GeoPalette.Brand50,
    infoBorder = GeoPalette.Brand200,

    pending = GeoPalette.PendingLightFg,
    pendingBg = GeoPalette.PendingLightBg,
    pendingBorder = GeoPalette.PendingLightBorder,

    unknown = GeoPalette.UnknownLightFg,
    unknownBg = GeoPalette.UnknownLightBg,
    unknownBorder = GeoPalette.UnknownLightBorder,

    disabled = GeoPalette.N100,
    onDisabled = GeoPalette.N500,

    sectionBase = GeoPalette.N0,
    sectionSoft = GeoPalette.N25,
    sectionRaised = GeoPalette.N100,
    sectionDanger = GeoPalette.CriticalLightBg,
    sectionBorder = GeoPalette.N200,

    navSelected = GeoPalette.Brand100,
    onNavSelected = GeoPalette.Brand700
)

val GeoDark = GeoColors(
    canvas = GeoPalette.N950,
    surface = GeoPalette.N900,
    surfaceRaised = GeoPalette.N850,
    surfaceSunken = GeoPalette.N950,
    subtle = GeoPalette.N800,
    overlay = Color(0x99000000),

    ink = Color(0xFFE7EAEF),
    inkStrong = Color(0xFFF5F7FA),
    muted = Color(0xFFA8B1BD),
    inkSubtle = Color(0xFF8E97A5),

    border = Color(0xFF333A45),
    borderStrong = Color(0xFF454D5A),
    controlBorder = Color(0xFF6E7787),
    focusRing = GeoPalette.Sky400,

    primary = GeoPalette.Brand300,
    onPrimary = GeoPalette.Brand900,
    primaryPressed = GeoPalette.Brand200,
    brandSoft = Color(0xFF1A2438),
    onBrandSoft = GeoPalette.Brand200,
    accent = Color(0xFF223052),
    onAccent = GeoPalette.Brand200,
    accentGreen = GeoPalette.BrandGreenLight,

    hero = GeoPalette.Navy800,
    heroText = Color(0xFFE7EAEF),
    heroMuted = Color(0xFFA9B4CB),

    success = GeoPalette.PositiveDarkFg,
    successBg = GeoPalette.PositiveDarkBg,
    successBorder = GeoPalette.PositiveDarkBorder,
    onSuccess = Color(0xFF07231A),

    warning = GeoPalette.CautionDarkFg,
    warningBg = GeoPalette.CautionDarkBg,
    warningBorder = GeoPalette.CautionDarkBorder,
    onWarning = Color(0xFF241A06),

    danger = GeoPalette.CriticalDarkFg,
    dangerBg = GeoPalette.CriticalDarkBg,
    dangerBorder = GeoPalette.CriticalDarkBorder,
    onDanger = Color(0xFF2B100D),

    info = GeoPalette.Brand300,
    infoBg = Color(0xFF1A2438),
    infoBorder = Color(0xFF32486E),

    pending = GeoPalette.PendingDarkFg,
    pendingBg = GeoPalette.PendingDarkBg,
    pendingBorder = GeoPalette.PendingDarkBorder,

    unknown = GeoPalette.UnknownDarkFg,
    unknownBg = GeoPalette.UnknownDarkBg,
    unknownBorder = GeoPalette.UnknownDarkBorder,

    disabled = Color(0xFF2B313A),
    onDisabled = Color(0xFF8C95A2),

    sectionBase = GeoPalette.N900,
    sectionSoft = GeoPalette.N850,
    sectionRaised = GeoPalette.N800,
    sectionDanger = GeoPalette.CriticalDarkBg,
    sectionBorder = Color(0xFF333A45),

    navSelected = Color(0xFF223052),
    onNavSelected = GeoPalette.Brand200
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
