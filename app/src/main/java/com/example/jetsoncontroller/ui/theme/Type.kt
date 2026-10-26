package com.example.jetsoncontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.jetsoncontroller.R

@OptIn(ExperimentalTextApi::class)
private fun pretendard(weight: FontWeight): Font = Font(
    resId = R.font.pretendard_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val PretendardFamily: FontFamily = FontFamily(
    pretendard(FontWeight.Normal),
    pretendard(FontWeight.Medium),
    pretendard(FontWeight.SemiBold),
    pretendard(FontWeight.Bold)
)

private fun type(size: Int, line: Int, weight: Int, trackingEm: Float = 0f) = TextStyle(
    fontFamily = PretendardFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = (size * trackingEm).sp
)

/** R7 reading roles; legacy names remain source compatible. */
@Immutable
data class GeoTypeRoles(
    val count: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val titleSm: TextStyle,
    val title: TextStyle,
    val boardTitle: TextStyle,
    val heading: TextStyle,
    val button: TextStyle,
    val input: TextStyle,
    val mono: TextStyle
) {
    val badge: TextStyle get() = caption.copy(fontWeight = FontWeight.SemiBold)
    val brandTitle: TextStyle get() = type(32, 40, 700)
    val workTitle: TextStyle get() = type(22, 32, 700)
    val sectionTitle: TextStyle get() = type(18, 28, 700)
    val deviceName: TextStyle get() = sectionTitle
    val focusTitle: TextStyle get() = workTitle
    val stateTitle: TextStyle get() = titleSm.copy(fontWeight = FontWeight.SemiBold)
    val emptyStateTitle: TextStyle get() = type(18, 28, 700)
    val emptyTitle: TextStyle get() = body
    val supporting: TextStyle get() = type(14, 20, 400)
    val propertyLabel: TextStyle get() = label
    val propertyValue: TextStyle get() = input.copy(fontWeight = body.fontWeight)
    val inlineStatus: TextStyle get() = type(14, 20, 500)
    val tag: TextStyle get() = badge
    val tab: TextStyle get() = caption.copy(fontWeight = FontWeight.SemiBold)
    val tabSelected: TextStyle get() = caption.copy(fontWeight = FontWeight.Bold)
    val figure: TextStyle get() = type(28, 36, 700).copy(fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
    val clock: TextStyle get() = type(40, 48, 700).copy(fontFeatureSettings = "tnum")
    val filter: TextStyle get() = type(13, 20, 600)
    val unit: TextStyle get() = type(13, 20, 500)
}

object GeoType {
    val standard = GeoTypeRoles(
        count = type(13, 20, 600),
        caption = type(13, 20, 500),
        label = type(14, 20, 500),
        body = type(16, 24, 400),
        titleSm = type(16, 24, 600),
        title = type(18, 28, 700),
        boardTitle = type(24, 32, 700),
        heading = type(24, 32, 700),
        button = type(16, 24, 600),
        input = type(16, 24, 400),
        mono = type(13, 20, 400).copy(fontFamily = FontFamily.Monospace)
    )
    val highContrast = standard

    fun forPalette(palette: GeoPalette): GeoTypeRoles = if (palette.highContrast) highContrast else standard

    val count get() = standard.count
    val caption get() = standard.caption
    val label get() = standard.label
    val body get() = standard.body
    val titleSm get() = standard.titleSm
    val title get() = standard.title
    val boardTitle get() = standard.boardTitle
    val heading get() = standard.heading
    val button get() = standard.button
    val input get() = standard.input
    val mono get() = standard.mono


}

val LocalGeoType = staticCompositionLocalOf { GeoType.standard }

internal fun geoMaterialTypography(t: GeoTypeRoles): Typography = Typography(
    displayLarge = t.clock, displayMedium = t.clock, displaySmall = t.brandTitle,
    headlineLarge = t.heading, headlineMedium = t.heading, headlineSmall = t.boardTitle,
    titleLarge = t.title, titleMedium = t.titleSm, titleSmall = t.stateTitle,
    bodyLarge = t.body, bodyMedium = t.body, bodySmall = t.caption,
    labelLarge = t.button, labelMedium = t.label, labelSmall = t.count
)
