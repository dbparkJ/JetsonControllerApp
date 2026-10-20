package com.example.jetsoncontroller.ui.theme

import com.example.jetsoncontroller.ui.theme.LocalGeoType
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

/** R3 reading roles with the R4 document hierarchy. */
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
    val sectionTitle: TextStyle get() = type(18, 26, 700, -0.015f)
    val deviceName: TextStyle get() = type(18, 26, 700, -0.015f)
    val focusTitle: TextStyle get() = titleSm.copy(fontWeight = FontWeight.SemiBold)
    val stateTitle: TextStyle get() = titleSm.copy(fontWeight = FontWeight.SemiBold)
    val emptyTitle: TextStyle get() = body
    val propertyLabel: TextStyle get() = type(15, 22, if (body.fontWeight == FontWeight.Medium) 600 else 500)
    val propertyValue: TextStyle get() = input.copy(fontWeight = body.fontWeight)
    val inlineStatus: TextStyle get() = type(14, 20, 600)
    val tag: TextStyle get() = label
    val tab: TextStyle get() = caption.copy(fontWeight = FontWeight.SemiBold)
    val tabSelected: TextStyle get() = caption.copy(fontWeight = FontWeight.Bold)
    val figure: TextStyle get() = heading.copy(fontFeatureSettings = "tnum")
}

object GeoType {
    val standard = GeoTypeRoles(
        count = type(12, 16, 600),
        caption = type(13, 20, 400, -0.003f),
        label = type(14, 20, 500),
        body = type(15, 24, 400, -0.003f),
        titleSm = type(16, 24, 500, -0.003f),
        title = type(18, 26, 600, -0.015f),
        boardTitle = type(24, 32, 700, -0.015f),
        heading = type(24, 32, 700, -0.025f),
        button = type(16, 20, 600),
        input = type(16, 24, 400),
        mono = type(13, 20, 400).copy(fontFamily = FontFamily.Monospace)
    )
    val highContrast = standard.copy(
        caption = standard.caption.copy(fontWeight = FontWeight.Medium),
        label = standard.label.copy(fontWeight = FontWeight.SemiBold),
        body = standard.body.copy(fontWeight = FontWeight.Medium),
        titleSm = standard.titleSm.copy(fontWeight = FontWeight.SemiBold)
    )

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
    displayLarge = t.heading, displayMedium = t.heading, displaySmall = t.heading,
    headlineLarge = t.heading, headlineMedium = t.heading, headlineSmall = t.boardTitle,
    titleLarge = t.title, titleMedium = t.titleSm, titleSmall = t.stateTitle,
    bodyLarge = t.body, bodyMedium = t.body, bodySmall = t.caption,
    labelLarge = t.button, labelMedium = t.label, labelSmall = t.count
)
