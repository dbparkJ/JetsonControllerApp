package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.unit.dp

/**
 * GEO& Field System — spacing, radius and size tokens.
 *
 * The previous scale mixed 4/8/12/16/20/24/28 with two overlapping "large" values
 * (`large` = 16, `screen` = 20), so two screens could look different while claiming
 * the same token. This is a strict 4dp scale with one name per step.
 */
object GeoSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    /** Horizontal gutter for a screen's content. One value, everywhere. */
    val gutter = 20.dp

    /** Vertical gap between two sibling sections on a screen. */
    val section = 24.dp

    /** Gap between rows inside one section. */
    val row = 12.dp
}

object GeoRadius {
    /** Badges, chips, small inline surfaces. */
    val xs = 6.dp
    /** Buttons, inputs, list rows. */
    val sm = 10.dp
    /** Section surfaces and cards. */
    val md = 14.dp
    /** Full-bleed panels, bottom sheets. */
    val lg = 18.dp
    val xl = 24.dp
}

object GeoSize {
    /** Android's documented minimum touch target. Never go below this. */
    val minTouchTarget = 48.dp

    /**
     * Primary field actions (시작 · 중지 · 전송). Taller than the platform minimum so a
     * standing operator can hit them without looking. Project standard, not a platform
     * requirement — see the design system doc.
     */
    val primaryAction = 56.dp

    /** Secondary actions on the same screen as a primary action. */
    val secondaryAction = 48.dp

    /** Status dot next to a badge label, for colour-blind-safe redundancy. */
    val statusDot = 8.dp

    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val iconXl = 32.dp

    /** Hairline used to separate surfaces instead of a shadow. */
    val hairline = 1.dp

    /** Left rule that carries a section's status colour. */
    val statusRule = 3.dp
}

/**
 * Width breakpoints, measured against the *app window* rather than the physical
 * screen, per Android's adaptive-layout guidance. A phone in a resized window must
 * behave like a narrow window, not like a tablet.
 */
object GeoBreakpoint {
    val medium = 600.dp
    val expanded = 840.dp
}

/**
 * Legacy spacing names, kept so unmigrated screens keep compiling and inherit the
 * corrected scale. New code should use [GeoSpace].
 */
object AppSpacing {
    val xSmall = GeoSpace.xs
    val small = GeoSpace.sm
    val medium = GeoSpace.md
    val large = GeoSpace.lg
    val xLarge = GeoSpace.xl
    val xxLarge = GeoSpace.xxl
    val section = GeoSpace.section
    val screen = GeoSpace.gutter
}
