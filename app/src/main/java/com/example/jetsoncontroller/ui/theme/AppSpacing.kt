package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** R7 spacing ownership. Named aliases avoid tying callers to scale indices. */
object AppSpacing {
    val screenGutter = 24.dp
    val cardInset = 16.dp
    val workCardInset = 20.dp
    val sectionGap = 24.dp
    val titleGap = 12.dp
    val relatedCardGap = 12.dp
    val actionHorizontal = 24.dp
    val actionVertical = 12.dp
}

object GeoSpace {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space7 = 28.dp
    val space8 = 32.dp
    val space9 = 36.dp
    val space10 = 40.dp
    val space11 = 44.dp
    val space12 = 48.dp
    val space13 = 52.dp
    val space14 = 56.dp

    val focusOffset = 2.dp
    val rowMetaGap = 4.dp
    val captionIconGap = 8.dp
    val sectionTitleGap = AppSpacing.titleGap
    val fieldLabelGap = 8.dp
    val buttonGap = 8.dp
    val buttonIconGap = 8.dp
    val rowPadding = 16.dp
    val actionPadding = AppSpacing.actionVertical
    val overlayInset = 12.dp
    val helperGap = 12.dp
    val figureLabelGap = 0.dp
    val singleRowPadding = 16.dp
    val leadingGap = 8.dp
    val gridGap = 8.dp
    val containedPadding = AppSpacing.cardInset
    val section = AppSpacing.sectionGap
    val dialogPadding = 24.dp
    val sheetPadding = 24.dp
    val dangerSection = 24.dp
    val detailSection = 24.dp
    val sceneGap = 32.dp
    val scrollEnd = 40.dp
    val sceneTop = 56.dp

    @Suppress("UNUSED_PARAMETER")
    fun gutterForPane(widthDp: Float): Dp = AppSpacing.screenGutter

}

object GeoSize {
    val minTouchTarget = 48.dp
    val primaryAction = 56.dp
    val secondaryAction = 48.dp
    val statusDot = 8.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val iconTile = 40.dp
    val badge = 24.dp
    val hairline = GeoShape.borderWidth
}

object GeoBreakpoint {
    val medium = 600.dp
    val expanded = 840.dp
    val compactHeight = 480.dp
}
