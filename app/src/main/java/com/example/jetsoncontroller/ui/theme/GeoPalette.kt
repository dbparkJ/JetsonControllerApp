package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GEO& Field System — raw palette.
 *
 * Layer 1 of the token system. These are *values*, never used directly by a screen.
 * Screens read semantic roles from [GeoColors] (`LocalGeoColors.current`) so that a
 * value change here can never silently change what a colour *means*.
 *
 * The light theme follows the GEON Work palette used by the company's other app
 * (plan §시각 규칙): a cool off-white canvas, white surfaces, one blue for the primary
 * action and a navy that appears only in the device card's next-action band. The dark
 * theme keeps the charcoal values from the previous design.
 */
internal object GeoPalette {

    // ---- Brand blue -----------------------------------------------------------------
    val Brand50 = Color(0xFFEEF3FC)
    val Brand100 = Color(0xFFDCE7F8)
    val Brand200 = Color(0xFFB9CEF0)
    val Brand300 = Color(0xFF8FAEE4)
    val Brand400 = Color(0xFF5C86D1)
    /** Primary action and focus in the light theme (GEON Work #2550A3). */
    val Brand500 = Color(0xFF2550A3)
    val Brand600 = Color(0xFF1F4489)
    val Brand700 = Color(0xFF1A3A74)
    val Brand800 = Color(0xFF17305E)
    val Brand900 = Color(0xFF101F3C)

    /** Brand navy. Used only as the next-action band on the device card (light). */
    val HeroNavy = Color(0xFF1A2147)

    // ---- Sky (globe highlight; dark-theme focus ring) -------------------------------
    val Sky200 = Color(0xFFC7E4F6)
    val Sky300 = Color(0xFF9CD1EF)
    val Sky400 = Color(0xFF69BDEA)
    val Sky500 = Color(0xFF2A86C7)
    val Sky600 = Color(0xFF1F6DA6)

    // ---- Deep navy — dark-theme grounding -------------------------------------------
    val Navy700 = Color(0xFF32366A)
    val Navy800 = Color(0xFF262A52)
    val Navy900 = Color(0xFF1B1E3B)

    // ---- Ampersand green — brand accent, used sparingly -----------------------------
    val BrandGreen = Color(0xFF29674E)
    val BrandGreenLight = Color(0xFF64B993)

    // ---- Neutrals -------------------------------------------------------------------
    // N0–N600 are the light-theme scale (GEON Work). N800–N950 are the dark-theme scale.
    val N0 = Color(0xFFFFFFFF)
    /** 낮은 표면 — a quieter surface than the card, for sunken areas. */
    val N25 = Color(0xFFF8F9FB)
    /** 바탕 — the light canvas behind every card. */
    val N50 = Color(0xFFF3F5F8)
    /** 낮은 톤 — a subtle fill for chips, disabled and "raised" light surfaces. */
    val N100 = Color(0xFFEBEEF2)
    /** 장식 선 — dividers and card edges, where a line is still wanted. */
    val N200 = Color(0xFFE2E6EC)
    val N300 = Color(0xFFB9C0CB)
    val N400 = Color(0xFF8E97A5)
    /** 약한 글자 — the lightest text that still meets 4.5:1 on a white surface. */
    val N500 = Color(0xFF5E6676)
    /** 보조 글자 — secondary text in the light theme. */
    val N600 = Color(0xFF3B4252)
    val N700 = Color(0xFF3C434F)
    val N800 = Color(0xFF2A303A)
    val N850 = Color(0xFF21262E)
    val N900 = Color(0xFF181C23)
    val N950 = Color(0xFF11141A)

    /** 글자 (light primary ink). */
    val InkLight = Color(0xFF171B26)
    /** 입력·조작 경계 — 3:1 against white for control boundaries. */
    val ControlBorderLight = Color(0xFF838A99)

    // ---- Positive: "확인됨" — a verified fact, never merely "connected" ---------------
    val PositiveLightFg = Color(0xFF0F6B4F)
    val PositiveLightBg = Color(0xFFE3F5ED)
    val PositiveLightBorder = Color(0xFFA2D8C1)
    val PositiveDarkFg = Color(0xFF6FD3A7)
    val PositiveDarkBg = Color(0xFF13342A)
    val PositiveDarkBorder = Color(0xFF2E6B54)

    // ---- Caution: "주의" — allowed to proceed, with a stated limitation ---------------
    val CautionLightFg = Color(0xFF8A5A00)
    val CautionLightBg = Color(0xFFFCF0DB)
    val CautionLightBorder = Color(0xFFEACB92)
    val CautionDarkFg = Color(0xFFF2BE68)
    val CautionDarkBg = Color(0xFF382B12)
    val CautionDarkBorder = Color(0xFF7A5A20)

    // ---- Critical: "실패" — the action cannot or did not happen ----------------------
    val CriticalLightFg = Color(0xFFB42318)
    val CriticalLightBg = Color(0xFFFCEBE9)
    val CriticalLightBorder = Color(0xFFEFB2AC)
    val CriticalDarkFg = Color(0xFFFF9E95)
    val CriticalDarkBg = Color(0xFF3B1D1B)
    val CriticalDarkBorder = Color(0xFF80322C)

    // ---- Informational: "안내" -------------------------------------------------------
    val InfoLightFg = Color(0xFF1F6FA8)
    val InfoLightBg = Color(0xFFEAF2FA)
    val InfoLightBorder = Color(0xFFB7D3EC)

    // ---- Pending: "처리 중" — a request was accepted, the result is not in yet --------
    val PendingLightFg = Color(0xFF574AA6)
    val PendingLightBg = Color(0xFFEEEBFA)
    val PendingLightBorder = Color(0xFFC3B9EA)
    val PendingDarkFg = Color(0xFFB7ACEF)
    val PendingDarkBg = Color(0xFF272240)
    val PendingDarkBorder = Color(0xFF524878)

    // ---- Unknown: "미확인·오래됨" — we genuinely do not know. Not success, not failure --
    val UnknownLightFg = Color(0xFF5E6676)
    val UnknownLightBg = Color(0xFFEDF0F4)
    val UnknownLightBorder = Color(0xFFC4CCD7)
    val UnknownDarkFg = Color(0xFFAAB4C1)
    val UnknownDarkBg = Color(0xFF262C35)
    val UnknownDarkBorder = Color(0xFF4A5462)
}
