package com.example.jetsoncontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.jetsoncontroller.R

/**
 * GEO& Field System — type scale.
 *
 * Font: Pretendard variable (`res/font/pretendard_variable.ttf`, SIL OFL — see
 * docs/licenses/Pretendard-OFL.txt). One variable file serves every weight: each
 * [FontWeight] below is bound to the same resource with an explicit `wght` setting, so
 * the rendered weight does not depend on which static face the platform would pick.
 * Pretendard covers Hangul and Latin in one face, which keeps the Korean and the
 * numeric values in a row visually matched.
 *
 * Sizes and line heights are the scale the screens were laid out against and are kept
 * unchanged. Korean needs more leading than Latin at the same size, so every line height
 * is roughly 1.45× the font size.
 *
 * Numbers (용량 · 시간 · 파일 수) use tabular figures via `fontFeatureSettings = "tnum"`,
 * so a value ticking from 19% to 20% does not shift its row.
 */
@OptIn(ExperimentalTextApi::class)
private fun pretendard(weight: FontWeight): Font = Font(
    resId = R.font.pretendard_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Pretendard at the four weights the product uses (400, 500, 600, 700). */
val PretendardFamily: FontFamily = FontFamily(
    pretendard(FontWeight.Normal),
    pretendard(FontWeight.Medium),
    pretendard(FontWeight.SemiBold),
    pretendard(FontWeight.Bold)
)

private val Base = TextStyle(
    fontFamily = PretendardFamily,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displaySmall = Base.copy(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = Base.copy(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.copy(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.copy(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = Base.copy(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.copy(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.copy(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = Base.copy(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = Base.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = Base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

/** Styles outside the Material scale that this product needs. */
object GeoType {

    /**
     * Tabular figures for any value that refreshes in place: percentages, byte counts,
     * file counts, elapsed time, coordinates. Prevents the row from jittering on update.
     */
    val numeric: TextStyle = Base.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    )

    /** Large in-context reading: the single number a field worker glances at. */
    val numericLarge: TextStyle = Base.copy(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    /** Small tabular values inside dense rows. */
    val numericSmall: TextStyle = Base.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    )

    /**
     * Identifiers the user may need to read aloud or compare character by character:
     * run IDs, preflight IDs, hashes, paths. Monospace is a legibility decision here,
     * not decoration.
     */
    val identifier: TextStyle = Base.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )

    /**
     * Former section eyebrow. Kept for source compatibility only: the redesign removed
     * eyebrow labels above section titles, so no screen should render this style.
     */
    val eyebrow: TextStyle = Base.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp
    )
}
