package com.example.jetsoncontroller.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Integration reference only: NOT compiled against the target repository.
 * Adapt to the existing theme type and Material3 version, do not create parallel state.
 */
@Immutable
internal data class SlateHarmonyTokens(
    val canvas: Color,
    val surface: Color,
    val subtle: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val controlBorder: Color,
    val focusRing: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Color,
    val heroText: Color,
    val heroMuted: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val info: Color,
    val infoBg: Color,
    val disabled: Color,
    val onDisabled: Color,
    val onDanger: Color,
    val sectionBase: Color,
    val sectionSoft: Color,
    val sectionRaised: Color,
    val sectionDanger: Color,
    val sectionBorder: Color,
    val navSelected: Color,
    val onNavSelected: Color
)

private val LightHarmonyTokens = SlateHarmonyTokens(
    canvas = Color(0xFFF4F5F7),
    surface = Color(0xFFFBFCFD),
    subtle = Color(0xFFEBEEF1),
    ink = Color(0xFF272C32),
    muted = Color(0xFF525862),
    border = Color(0xFFD0D3D9),
    controlBorder = Color(0xFF717884),
    focusRing = Color(0xFF556887),
    primary = Color(0xFF526584),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFD3DEF1),
    onAccent = Color(0xFF38465B),
    hero = Color(0xFFE0E7F1),
    heroText = Color(0xFF272C32),
    heroMuted = Color(0xFF525862),
    success = Color(0xFF525862),
    successBg = Color(0xFFEBEEF1),
    warning = Color(0xFF7A5729),
    warningBg = Color(0xFFF5EDDF),
    danger = Color(0xFF9B4B46),
    dangerBg = Color(0xFFF8ECEA),
    info = Color(0xFF526584),
    infoBg = Color(0xFFE6EBF4),
    disabled = Color(0xFFE1E5EA),
    onDisabled = Color(0xFF62666D),
    onDanger = Color(0xFFFFFFFF),
    sectionBase = Color(0xFFFBFCFD),
    sectionSoft = Color(0xFFEBEEF1),
    sectionRaised = Color(0xFFE1E5EA),
    sectionDanger = Color(0xFFEBEEF1),
    sectionBorder = Color(0xFFD0D3D9),
    navSelected = Color(0xFFD3DEF1),
    onNavSelected = Color(0xFF38465B)
)

private val DarkHarmonyTokens = SlateHarmonyTokens(
    canvas = Color(0xFF1C1E22),
    surface = Color(0xFF25282D),
    subtle = Color(0xFF303339),
    ink = Color(0xFFE4E6EA),
    muted = Color(0xFFABB0B7),
    border = Color(0xFF464B53),
    controlBorder = Color(0xFF868D97),
    focusRing = Color(0xFFAAB8D0),
    primary = Color(0xFF9EACC1),
    onPrimary = Color(0xFF1B222E),
    accent = Color(0xFF3E4859),
    onAccent = Color(0xFFDDE2E9),
    hero = Color(0xFF333B48),
    heroText = Color(0xFFE4E6EA),
    heroMuted = Color(0xFFABB0B7),
    success = Color(0xFFB5BBC5),
    successBg = Color(0xFF303339),
    warning = Color(0xFFD6B88A),
    warningBg = Color(0xFF3A3126),
    danger = Color(0xFFE0AAA4),
    dangerBg = Color(0xFF3C2C2D),
    info = Color(0xFFAAB8D0),
    infoBg = Color(0xFF333B48),
    disabled = Color(0xFF393D44),
    onDisabled = Color(0xFFA5A9AF),
    onDanger = Color(0xFF302322),
    sectionBase = Color(0xFF25282D),
    sectionSoft = Color(0xFF303339),
    sectionRaised = Color(0xFF393D44),
    sectionDanger = Color(0xFF303339),
    sectionBorder = Color(0xFF464B53),
    navSelected = Color(0xFF3E4859),
    onNavSelected = Color(0xFFDDE2E9)
)

internal fun slateHarmonyTokens(darkTheme: Boolean): SlateHarmonyTokens =
    if (darkTheme) DarkHarmonyTokens else LightHarmonyTokens

internal fun slateHarmonyColorScheme(darkTheme: Boolean): ColorScheme {
    val p = slateHarmonyTokens(darkTheme)
    val opposite = slateHarmonyTokens(!darkTheme)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.accent,
        onPrimaryContainer = p.onAccent,
        inversePrimary = opposite.primary,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.sectionSoft,
        onSecondaryContainer = p.ink,
        tertiary = p.primary,
        onTertiary = p.onPrimary,
        tertiaryContainer = p.sectionRaised,
        onTertiaryContainer = p.ink,
        background = p.canvas,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.sectionSoft,
        onSurfaceVariant = p.muted,
        surfaceTint = p.primary,
        inverseSurface = opposite.surface,
        inverseOnSurface = opposite.ink,
        error = p.danger,
        onError = p.onDanger,
        errorContainer = p.dangerBg,
        onErrorContainer = p.danger,
        outline = p.controlBorder,
        outlineVariant = p.border,
        scrim = Color(0xFF101419),
        surfaceBright = if (darkTheme) p.sectionRaised else p.surface,
        surfaceDim = if (darkTheme) p.canvas else p.sectionRaised,
        surfaceContainerLowest = if (darkTheme) p.canvas else p.surface,
        surfaceContainerLow = if (darkTheme) p.surface else p.canvas,
        surfaceContainer = p.sectionSoft,
        surfaceContainerHigh = p.sectionRaised,
        surfaceContainerHighest = p.sectionRaised
    )
}
