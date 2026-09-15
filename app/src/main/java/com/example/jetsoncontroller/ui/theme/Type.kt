package com.example.jetsoncontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * GEO& Field System — type scale.
 *
 * Font choice: the platform sans-serif is kept on purpose. On the target Android
 * devices it resolves to Roboto with a Noto Sans KR fallback, which is the most
 * reliable Hangul rendering available without shipping a licensed face. Bundling a
 * webfont would add weight for no legibility gain and would need a licence review.
 *
 * Three problems in the previous scale are fixed here:
 *
 *  - `bodyLarge` and `bodyMedium` were both 16/24, so "body" and "emphasised body"
 *    were the same thing and screens had no mid-level step to reach for.
 *  - Korean needs more leading than Latin at the same size, so every line height is
 *    raised to roughly 1.45× the font size instead of the previous 1.35×.
 *  - Numbers (용량 · 시간 · 파일 수) were rendered with proportional figures, so a value
 *    ticking from 19% to 20% shifted the row. [GeoType.numeric] uses tabular figures.
 */
private val Base = TextStyle(
    fontFamily = FontFamily.SansSerif,
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

    /** Section eyebrow above a title. Short, quiet, never carries a unique meaning. */
    val eyebrow: TextStyle = Base.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp
    )
}
