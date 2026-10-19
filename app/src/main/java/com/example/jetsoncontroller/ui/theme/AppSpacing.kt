package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** R3 §3.3 scale and ownership names. Gutters use the current content pane width. */
object GeoSpace {
    val space1 = 2.dp
    val space2 = 4.dp
    val space3 = 6.dp
    val space4 = 8.dp
    val space5 = 10.dp
    val space6 = 12.dp
    val space7 = 14.dp
    val space8 = 16.dp
    val space9 = 20.dp
    val space10 = 24.dp
    val space11 = 28.dp
    val space12 = 32.dp
    val space13 = 40.dp
    val space14 = 56.dp

    val focusOffset = space1
    val rowMetaGap = space2
    val captionIconGap = space3
    val sectionTitleGap = space4
    val fieldLabelGap = space4
    val buttonGap = space4
    val buttonIconGap = space5
    val rowPadding = space6
    val actionPadding = space6
    val overlayInset = space6
    val helperGap = space6
    val figureLabelGap = space7
    val singleRowPadding = space8
    val leadingGap = space8
    val gridGap = space8
    val containedPadding = space8
    val section = space10
    val dialogPadding = space10
    val sheetPadding = space10
    val dangerSection = space11
    val detailSection = space12
    val sceneGap = space12
    val scrollEnd = space13
    val sceneTop = space14

    fun gutterForPane(widthDp: Float): Dp = when {
        widthDp >= 390f -> space10
        widthDp >= 340f -> space9
        else -> space8
    }

    @Deprecated("Use space2", ReplaceWith("space2")) val xs = space2
    @Deprecated("Use space4", ReplaceWith("space4")) val sm = space4
    @Deprecated("Use space6", ReplaceWith("space6")) val md = space6
    @Deprecated("Use space8", ReplaceWith("space8")) val lg = space8
    @Deprecated("Use space9", ReplaceWith("space9")) val xl = space9
    @Deprecated("Use space10", ReplaceWith("space10")) val xxl = space10
    @Deprecated("Use space12", ReplaceWith("space12")) val xxxl = space12
    @Deprecated("Use space13", ReplaceWith("space13")) val huge = space13
    @Deprecated("Use gutterForPane with measured pane width") val gutter = space9
    @Deprecated("Use rowPadding", ReplaceWith("rowPadding")) val row = rowPadding
}

/** Compatibility names retain their roles until the remaining screens migrate. */
object GeoRadius {
    @Deprecated("Use GeoShape.sm", ReplaceWith("GeoShape.sm")) val xs = GeoShape.sm
    @Deprecated("Use GeoShape.md", ReplaceWith("GeoShape.md")) val sm = GeoShape.md
    @Deprecated("Use GeoShape.lg", ReplaceWith("GeoShape.lg")) val md = GeoShape.lg
    @Deprecated("Use GeoShape.xl", ReplaceWith("GeoShape.xl")) val lg = GeoShape.xl
    @Deprecated("Use GeoShape.xxl", ReplaceWith("GeoShape.xxl")) val xl = GeoShape.xxl
    @Deprecated("Use GeoShape.pillRadius", ReplaceWith("GeoShape.pillRadius")) val pill = GeoShape.pillRadius
}

object GeoSize {
    val minTouchTarget = 48.dp
    val primaryAction = 52.dp
    val secondaryAction = 48.dp
    val statusDot = 8.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    @Deprecated("Use iconLg; R3 icons use 16, 20 or 24 dp") val iconXl = 32.dp
    val hairline = GeoShape.borderWidth
    @Deprecated("Status left rules are retired") val statusRule = 3.dp
}

object GeoBreakpoint {
    val medium = 600.dp
    val expanded = 840.dp
    val compactHeight = 480.dp
}

@Deprecated("Use GeoSpace named uses")
object AppSpacing {
    val xSmall = GeoSpace.space2
    val small = GeoSpace.space4
    val medium = GeoSpace.space6
    val large = GeoSpace.space8
    val xLarge = GeoSpace.space9
    val xxLarge = GeoSpace.space10
    val section = GeoSpace.section
    val screen = GeoSpace.space9
}
