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
    val dangerSection = space10
    val detailSection = space10
    val sceneGap = space12
    val scrollEnd = space13
    val sceneTop = space14

    fun gutterForPane(widthDp: Float): Dp = when {
        widthDp >= 390f -> space10
        widthDp >= 340f -> space9
        else -> space8
    }

}

object GeoSize {
    val minTouchTarget = 48.dp
    val primaryAction = 52.dp
    val secondaryAction = 48.dp
    val statusDot = 8.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val hairline = GeoShape.borderWidth
}

object GeoBreakpoint {
    val medium = 600.dp
    val expanded = 840.dp
    val compactHeight = 480.dp
}
