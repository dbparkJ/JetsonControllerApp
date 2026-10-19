package com.example.jetsoncontroller.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Palette identity only; persistence and the picker belong to Phase 4-B. */
enum class GeoPalette(val label: String, val isDark: Boolean) {
    STANDARD("표준", false),
    NAVY_LIGHT("네이비 (밝게)", false),
    HIGH_CONTRAST("고대비", false),
    GRAPHITE("그래파이트", true),
    NAVY_DARK("네이비 (어둡게)", true),
    MIST("흐림", true);

    val colors: GeoColors
        get() = when (this) {
            STANDARD -> GeoStandard
            NAVY_LIGHT -> GeoNavyLight
            HIGH_CONTRAST -> GeoHighContrast
            GRAPHITE -> GeoGraphite
            NAVY_DARK -> GeoNavyDark
            MIST -> GeoMist
        }

    val highContrast: Boolean get() = this == HIGH_CONTRAST
    val lightStatusBarIcons: Boolean get() = isDark || this == NAVY_LIGHT
    val lightNavigationBarIcons: Boolean get() = lightStatusBarIcons
    val lightSurfaceBarIcons: Boolean get() = isDark

    companion object {
        fun defaultFor(darkTheme: Boolean): GeoPalette = if (darkTheme) GRAPHITE else STANDARD

        @Deprecated("Use GeoStandard.canvas", ReplaceWith("GeoStandard.canvas"))
        val WorkBg get() = GeoStandard.canvas
        @Deprecated("Use GeoStandard.surface", ReplaceWith("GeoStandard.surface"))
        val WorkSurface get() = GeoStandard.surface
        @Deprecated("Use GeoStandard.sunken", ReplaceWith("GeoStandard.sunken"))
        val WorkSurfaceSubtle get() = GeoStandard.sunken
        @Deprecated("Use GeoStandard.sunken", ReplaceWith("GeoStandard.sunken"))
        val WorkSurfaceSunken get() = GeoStandard.sunken
        @Deprecated("Use GeoStandard.fieldPressed", ReplaceWith("GeoStandard.fieldPressed"))
        val WorkFieldHover get() = GeoStandard.fieldPressed
        @Deprecated("Use GeoStandard.text", ReplaceWith("GeoStandard.text"))
        val WorkText get() = GeoStandard.text
        @Deprecated("Use GeoStandard.textSecondary", ReplaceWith("GeoStandard.textSecondary"))
        val WorkTextSecondary get() = GeoStandard.textSecondary
        @Deprecated("Use GeoStandard.textSubtle", ReplaceWith("GeoStandard.textSubtle"))
        val WorkTextSubtle get() = GeoStandard.textSubtle
        @Deprecated("Use GeoStandard.border", ReplaceWith("GeoStandard.border"))
        val WorkBorder get() = GeoStandard.border
        @Deprecated("Use GeoStandard.border", ReplaceWith("GeoStandard.border"))
        val WorkDivider get() = GeoStandard.border
        @Deprecated("Use GeoStandard.borderStrong", ReplaceWith("GeoStandard.borderStrong"))
        val WorkControlBorder get() = GeoStandard.borderStrong
        @Deprecated("Use GeoStandard.primary", ReplaceWith("GeoStandard.primary"))
        val WorkPrimary get() = GeoStandard.primary
        @Deprecated("Use GeoStandard.primaryPressed", ReplaceWith("GeoStandard.primaryPressed"))
        val WorkPrimaryHover get() = GeoStandard.primaryPressed
        @Deprecated("Use GeoStandard.primaryTint", ReplaceWith("GeoStandard.primaryTint"))
        val WorkPrimaryTint get() = GeoStandard.primaryTint
        @Deprecated("Use GeoStandard.infoBorder", ReplaceWith("GeoStandard.infoBorder"))
        val WorkPrimarySoftBorder get() = GeoStandard.infoBorder
        @Deprecated("Use GeoNavyLight.topBar", ReplaceWith("GeoNavyLight.topBar"))
        val WorkRail get() = GeoNavyLight.topBar
        @Deprecated("Use GeoNavyLight.onTopBarIcon", ReplaceWith("GeoNavyLight.onTopBarIcon"))
        val WorkRailText get() = GeoNavyLight.onTopBarIcon
        @Deprecated("Use GeoStandard.success", ReplaceWith("GeoStandard.success"))
        val WorkSuccess get() = GeoStandard.success
        @Deprecated("Use GeoStandard.successSoft", ReplaceWith("GeoStandard.successSoft"))
        val WorkSuccessSoft get() = GeoStandard.successSoft
        @Deprecated("Use GeoStandard.successBorder", ReplaceWith("GeoStandard.successBorder"))
        val WorkSuccessBorder get() = GeoStandard.successBorder
        @Deprecated("Use GeoStandard.warning", ReplaceWith("GeoStandard.warning"))
        val WorkWarning get() = GeoStandard.warning
        @Deprecated("Use GeoStandard.warningSoft", ReplaceWith("GeoStandard.warningSoft"))
        val WorkWarningSoft get() = GeoStandard.warningSoft
        @Deprecated("Use GeoStandard.warningBorder", ReplaceWith("GeoStandard.warningBorder"))
        val WorkWarningBorder get() = GeoStandard.warningBorder
        @Deprecated("Use GeoStandard.danger", ReplaceWith("GeoStandard.danger"))
        val WorkDanger get() = GeoStandard.danger
        @Deprecated("Use GeoStandard.dangerSoft", ReplaceWith("GeoStandard.dangerSoft"))
        val WorkDangerSoft get() = GeoStandard.dangerSoft
        @Deprecated("Use GeoStandard.dangerBorder", ReplaceWith("GeoStandard.dangerBorder"))
        val WorkDangerBorder get() = GeoStandard.dangerBorder
        @Deprecated("Use GeoStandard.neutral", ReplaceWith("GeoStandard.neutral"))
        val WorkTodo get() = GeoStandard.neutral
        @Deprecated("Use GeoStandard.neutralSoft", ReplaceWith("GeoStandard.neutralSoft"))
        val WorkTodoSoft get() = GeoStandard.neutralSoft
        @Deprecated("Use GeoStandard.neutralBorder", ReplaceWith("GeoStandard.neutralBorder"))
        val WorkTodoBorder get() = GeoStandard.neutralBorder
        @Deprecated("Use GeoStandard.pending", ReplaceWith("GeoStandard.pending"))
        val WorkViolet get() = GeoStandard.pending
        @Deprecated("Use GeoStandard.pendingSoft", ReplaceWith("GeoStandard.pendingSoft"))
        val WorkVioletSoft get() = GeoStandard.pendingSoft
        @Deprecated("Use GeoStandard.pendingBorder", ReplaceWith("GeoStandard.pendingBorder"))
        val WorkVioletBorder get() = GeoStandard.pendingBorder
        @Deprecated("Use GeoGraphite.canvas", ReplaceWith("GeoGraphite.canvas"))
        val WorkDarkBg get() = GeoGraphite.canvas
        @Deprecated("Use GeoGraphite.surface", ReplaceWith("GeoGraphite.surface"))
        val WorkDarkSurface get() = GeoGraphite.surface
        @Deprecated("Use GeoGraphite.field", ReplaceWith("GeoGraphite.field"))
        val WorkDarkSurfaceSubtle get() = GeoGraphite.field
        @Deprecated("Use GeoGraphite.sunken", ReplaceWith("GeoGraphite.sunken"))
        val WorkDarkSurfaceSunken get() = GeoGraphite.sunken
        @Deprecated("Use GeoGraphite.field", ReplaceWith("GeoGraphite.field"))
        val WorkDarkField get() = GeoGraphite.field
        @Deprecated("Use GeoGraphite.text", ReplaceWith("GeoGraphite.text"))
        val WorkDarkText get() = GeoGraphite.text
        @Deprecated("Use GeoGraphite.text", ReplaceWith("GeoGraphite.text"))
        val WorkDarkTextStrong get() = GeoGraphite.text
        @Deprecated("Use GeoGraphite.textSecondary", ReplaceWith("GeoGraphite.textSecondary"))
        val WorkDarkTextSecondary get() = GeoGraphite.textSecondary
        @Deprecated("Use GeoGraphite.textSubtle", ReplaceWith("GeoGraphite.textSubtle"))
        val WorkDarkTextSubtle get() = GeoGraphite.textSubtle
        @Deprecated("Use GeoGraphite.border", ReplaceWith("GeoGraphite.border"))
        val WorkDarkBorder get() = GeoGraphite.border
        @Deprecated("Use GeoGraphite.neutralBorder", ReplaceWith("GeoGraphite.neutralBorder"))
        val WorkDarkDivider get() = GeoGraphite.neutralBorder
        @Deprecated("Use GeoGraphite.borderStrong", ReplaceWith("GeoGraphite.borderStrong"))
        val WorkDarkControlBorder get() = GeoGraphite.borderStrong
        @Deprecated("Use GeoGraphite.primary", ReplaceWith("GeoGraphite.primary"))
        val WorkDarkPrimary get() = GeoGraphite.primary
        @Deprecated("Use GeoGraphite.primaryPressed", ReplaceWith("GeoGraphite.primaryPressed"))
        val WorkDarkPrimaryHover get() = GeoGraphite.primaryPressed
        @Deprecated("Use GeoGraphite.primaryTint", ReplaceWith("GeoGraphite.primaryTint"))
        val WorkDarkPrimaryTint get() = GeoGraphite.primaryTint
        @Deprecated("Use GeoGraphite.primaryTintText", ReplaceWith("GeoGraphite.primaryTintText"))
        val WorkDarkPrimarySoftText get() = GeoGraphite.primaryTintText
        @Deprecated("Use GeoGraphite.infoBorder", ReplaceWith("GeoGraphite.infoBorder"))
        val WorkDarkPrimarySoftBorder get() = GeoGraphite.infoBorder
        @Deprecated("Use GeoGraphite.nav", ReplaceWith("GeoGraphite.nav"))
        val WorkDarkRail get() = GeoGraphite.nav
        @Deprecated("Use GeoGraphite.navUnselected", ReplaceWith("GeoGraphite.navUnselected"))
        val WorkDarkRailText get() = GeoGraphite.navUnselected
        @Deprecated("Use GeoGraphite.success", ReplaceWith("GeoGraphite.success"))
        val WorkDarkSuccess get() = GeoGraphite.success
        @Deprecated("Use GeoGraphite.successSoft", ReplaceWith("GeoGraphite.successSoft"))
        val WorkDarkSuccessSoft get() = GeoGraphite.successSoft
        @Deprecated("Use GeoGraphite.successBorder", ReplaceWith("GeoGraphite.successBorder"))
        val WorkDarkSuccessBorder get() = GeoGraphite.successBorder
        @Deprecated("Use GeoGraphite.warning", ReplaceWith("GeoGraphite.warning"))
        val WorkDarkWarning get() = GeoGraphite.warning
        @Deprecated("Use GeoGraphite.warningSoft", ReplaceWith("GeoGraphite.warningSoft"))
        val WorkDarkWarningSoft get() = GeoGraphite.warningSoft
        @Deprecated("Use GeoGraphite.warningBorder", ReplaceWith("GeoGraphite.warningBorder"))
        val WorkDarkWarningBorder get() = GeoGraphite.warningBorder
        @Deprecated("Use GeoGraphite.danger", ReplaceWith("GeoGraphite.danger"))
        val WorkDarkDanger get() = GeoGraphite.danger
        @Deprecated("Use GeoGraphite.dangerSoft", ReplaceWith("GeoGraphite.dangerSoft"))
        val WorkDarkDangerSoft get() = GeoGraphite.dangerSoft
        @Deprecated("Use GeoGraphite.dangerBorder", ReplaceWith("GeoGraphite.dangerBorder"))
        val WorkDarkDangerBorder get() = GeoGraphite.dangerBorder
        @Deprecated("Use GeoGraphite.neutral", ReplaceWith("GeoGraphite.neutral"))
        val WorkDarkTodo get() = GeoGraphite.neutral
        @Deprecated("Use GeoGraphite.neutralSoft", ReplaceWith("GeoGraphite.neutralSoft"))
        val WorkDarkTodoSoft get() = GeoGraphite.neutralSoft
        @Deprecated("Use GeoGraphite.pending", ReplaceWith("GeoGraphite.pending"))
        val WorkDarkViolet get() = GeoGraphite.pending
        @Deprecated("Use GeoGraphite.pendingSoft", ReplaceWith("GeoGraphite.pendingSoft"))
        val WorkDarkVioletSoft get() = GeoGraphite.pendingSoft
        @Deprecated("Use GeoGraphite.pendingBorder", ReplaceWith("GeoGraphite.pendingBorder"))
        val WorkDarkVioletBorder get() = GeoGraphite.pendingBorder
        @Deprecated("Use GeoFixedColors.brandGreen", ReplaceWith("GeoFixedColors.brandGreen"))
        val BrandGreen get() = GeoFixedColors.brandGreen
        @Deprecated("Use GeoFixedColors.brandGreenLight", ReplaceWith("GeoFixedColors.brandGreenLight"))
        val BrandGreenLight get() = GeoFixedColors.brandGreenLight
    }
}

val LocalGeoPalette = staticCompositionLocalOf { GeoPalette.STANDARD }
