package com.example.jetsoncontroller.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** R3 §4.2 semantic roles. Transparent borders mean that chrome has no hairline. */
@Immutable
data class GeoColors(
    val canvas: Color,
    val surface: Color,
    val sunken: Color,
    val field: Color,
    val fieldPressed: Color,
    val text: Color,
    val textSecondary: Color,
    val textSubtle: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,
    val primaryTint: Color,
    val primaryTintText: Color,
    val focusHalo: Color,
    val success: Color,
    val successSoft: Color,
    val successBorder: Color,
    val warning: Color,
    val warningSoft: Color,
    val warningBorder: Color,
    val danger: Color,
    val dangerPressed: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val infoBorder: Color,
    val pending: Color,
    val pendingSoft: Color,
    val pendingBorder: Color,
    val neutral: Color,
    val neutralSoft: Color,
    val neutralBorder: Color,
    val disabledFill: Color,
    val disabledText: Color,
    val switchTrackOff: Color,
    val switchThumbOff: Color,
    val switchThumbOn: Color,
    val topBar: Color,
    val onTopBar: Color,
    val onTopBarIcon: Color,
    val topBarBorder: Color,
    val nav: Color,
    val navSelected: Color,
    val navUnselected: Color,
    val navBorder: Color,
    val navFocus: Color,
    val attention: Color,
    val onAttention: Color,
    val chromeSuccess: Color,
    val chromeWarning: Color,
    val chromeDanger: Color,
    val chromeInfo: Color,
    val chromePending: Color,
    val chromeNeutral: Color,
    val inverse: Color,
    val onInverse: Color,
    val inverseAction: Color,
    val scrim: Color,
    val mapPlate: Color
) {
    val info: Color get() = primary
    val infoSoft: Color get() = primaryTint
    val focus: Color get() = primary
    val switchTrackOn: Color get() = primary

    @Deprecated("Use surface", ReplaceWith("surface"))
    val surfaceRaised: Color get() = surface
    @Deprecated("Use sunken", ReplaceWith("sunken"))
    val surfaceSunken: Color get() = sunken
    @Deprecated("Use fieldPressed", ReplaceWith("fieldPressed"))
    val subtle: Color get() = fieldPressed
    @Deprecated("Use scrim", ReplaceWith("scrim"))
    val overlay: Color get() = scrim
    @Deprecated("Use text", ReplaceWith("text"))
    val ink: Color get() = text
    @Deprecated("Use text", ReplaceWith("text"))
    val inkStrong: Color get() = text
    @Deprecated("Use textSecondary", ReplaceWith("textSecondary"))
    val muted: Color get() = textSecondary
    @Deprecated("Use textSubtle", ReplaceWith("textSubtle"))
    val inkSubtle: Color get() = textSubtle
    @Deprecated("Use borderStrong", ReplaceWith("borderStrong"))
    val controlBorder: Color get() = borderStrong
    @Deprecated("Use focus", ReplaceWith("focus"))
    val focusRing: Color get() = focus
    @Deprecated("Use primaryTint", ReplaceWith("primaryTint"))
    val brandSoft: Color get() = primaryTint
    @Deprecated("Use primaryTintText", ReplaceWith("primaryTintText"))
    val onBrandSoft: Color get() = primaryTintText
    @Deprecated("Use primaryTint", ReplaceWith("primaryTint"))
    val accent: Color get() = primaryTint
    @Deprecated("Use primaryTintText", ReplaceWith("primaryTintText"))
    val onAccent: Color get() = primaryTintText
    @Deprecated("Use topBar", ReplaceWith("topBar"))
    val hero: Color get() = topBar
    @Deprecated("Use onTopBar", ReplaceWith("onTopBar"))
    val heroText: Color get() = onTopBar
    @Deprecated("Use onTopBarIcon", ReplaceWith("onTopBarIcon"))
    val heroMuted: Color get() = onTopBarIcon
    @Deprecated("Use successSoft", ReplaceWith("successSoft"))
    val successBg: Color get() = successSoft
    @Deprecated("Use onPrimary", ReplaceWith("onPrimary"))
    val onSuccess: Color get() = onPrimary
    @Deprecated("Use warningSoft", ReplaceWith("warningSoft"))
    val warningBg: Color get() = warningSoft
    @Deprecated("Use onPrimary", ReplaceWith("onPrimary"))
    val onWarning: Color get() = onPrimary
    @Deprecated("Use dangerSoft", ReplaceWith("dangerSoft"))
    val dangerBg: Color get() = dangerSoft
    @Deprecated("Use infoSoft", ReplaceWith("infoSoft"))
    val infoBg: Color get() = infoSoft
    @Deprecated("Use pendingSoft", ReplaceWith("pendingSoft"))
    val pendingBg: Color get() = pendingSoft
    @Deprecated("Use neutral", ReplaceWith("neutral"))
    val unknown: Color get() = neutral
    @Deprecated("Use neutralSoft", ReplaceWith("neutralSoft"))
    val unknownBg: Color get() = neutralSoft
    @Deprecated("Use neutralBorder", ReplaceWith("neutralBorder"))
    val unknownBorder: Color get() = neutralBorder
    @Deprecated("Use disabledFill", ReplaceWith("disabledFill"))
    val disabled: Color get() = disabledFill
    @Deprecated("Use disabledText", ReplaceWith("disabledText"))
    val onDisabled: Color get() = disabledText
    @Deprecated("Use surface", ReplaceWith("surface"))
    val sectionBase: Color get() = surface
    @Deprecated("Use sunken", ReplaceWith("sunken"))
    val sectionSoft: Color get() = sunken
    @Deprecated("Use field", ReplaceWith("field"))
    val sectionRaised: Color get() = this.field
    @Deprecated("Use dangerSoft", ReplaceWith("dangerSoft"))
    val sectionDanger: Color get() = dangerSoft
    @Deprecated("Use border", ReplaceWith("border"))
    val sectionBorder: Color get() = border
    @Deprecated("Use primaryTintText", ReplaceWith("primaryTintText"))
    val onNavSelected: Color get() = primaryTintText
    @Deprecated("Brand artwork only; use success for status")
    val accentGreen: Color get() = if (surface == Color.White) GeoFixedColors.brandGreen else GeoFixedColors.brandGreenLight

    /** Canonical roles, including the four explicit aliases in the specification. */
    fun asRoleMap(): Map<String, Color> = mapOf(
        "canvas" to canvas,
        "surface" to surface,
        "sunken" to sunken,
        "field" to field,
        "fieldPressed" to fieldPressed,
        "text" to text,
        "textSecondary" to textSecondary,
        "textSubtle" to textSubtle,
        "border" to border,
        "borderStrong" to borderStrong,
        "primary" to primary,
        "primaryPressed" to primaryPressed,
        "onPrimary" to onPrimary,
        "primaryTint" to primaryTint,
        "primaryTintText" to primaryTintText,
        "focusHalo" to focusHalo,
        "success" to success,
        "successSoft" to successSoft,
        "successBorder" to successBorder,
        "warning" to warning,
        "warningSoft" to warningSoft,
        "warningBorder" to warningBorder,
        "danger" to danger,
        "dangerPressed" to dangerPressed,
        "onDanger" to onDanger,
        "dangerSoft" to dangerSoft,
        "dangerBorder" to dangerBorder,
        "infoBorder" to infoBorder,
        "pending" to pending,
        "pendingSoft" to pendingSoft,
        "pendingBorder" to pendingBorder,
        "neutral" to neutral,
        "neutralSoft" to neutralSoft,
        "neutralBorder" to neutralBorder,
        "disabledFill" to disabledFill,
        "disabledText" to disabledText,
        "switchTrackOff" to switchTrackOff,
        "switchThumbOff" to switchThumbOff,
        "switchThumbOn" to switchThumbOn,
        "topBar" to topBar,
        "onTopBar" to onTopBar,
        "onTopBarIcon" to onTopBarIcon,
        "topBarBorder" to topBarBorder,
        "nav" to nav,
        "navSelected" to navSelected,
        "navUnselected" to navUnselected,
        "navBorder" to navBorder,
        "navFocus" to navFocus,
        "attention" to attention,
        "onAttention" to onAttention,
        "chromeSuccess" to chromeSuccess,
        "chromeWarning" to chromeWarning,
        "chromeDanger" to chromeDanger,
        "chromeInfo" to chromeInfo,
        "chromePending" to chromePending,
        "chromeNeutral" to chromeNeutral,
        "inverse" to inverse,
        "onInverse" to onInverse,
        "inverseAction" to inverseAction,
        "scrim" to scrim,
        "mapPlate" to mapPlate,
        "info" to info,
        "infoSoft" to infoSoft,
        "focus" to focus,
        "switchTrackOn" to switchTrackOn
    )
}

val GeoStandard = GeoColors(
    canvas = Color(0xFFEDF0F5),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFF3F5F8),
    field = Color(0xFFF1F3F7),
    fieldPressed = Color(0xFFE9ECF2),
    text = Color(0xFF151B31),
    textSecondary = Color(0xFF4B5570),
    textSubtle = Color(0xFF5F6880),
    border = Color(0xFFE7EAF0),
    borderStrong = Color(0xFF838891),
    primary = Color(0xFF1F52B8),
    primaryPressed = Color(0xFF1A47A0),
    onPrimary = Color(0xFFFFFFFF),
    primaryTint = Color(0xFFE7EEFB),
    primaryTintText = Color(0xFF1A47A0),
    focusHalo = Color(0x3D1F52B8),
    success = Color(0xFF22704B),
    successSoft = Color(0xFFE2F1E9),
    successBorder = Color(0xFFA9D3BD),
    warning = Color(0xFF8A5A06),
    warningSoft = Color(0xFFFBF0DA),
    warningBorder = Color(0xFFECCA8C),
    danger = Color(0xFFB3372C),
    dangerPressed = Color(0xFF9A2E25),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFFBE8E5),
    dangerBorder = Color(0xFFF0B8B1),
    infoBorder = Color(0xFFB9CBEF),
    pending = Color(0xFF3D3A86),
    pendingSoft = Color(0xFFE4E3F4),
    pendingBorder = Color(0xFFC4C1E8),
    neutral = Color(0xFF5D667B),
    neutralSoft = Color(0xFFEEF0F4),
    neutralBorder = Color(0xFFD9DEE7),
    disabledFill = Color(0xFFE9ECF2),
    disabledText = Color(0xFF5F6880),
    switchTrackOff = Color(0xFF7D8598),
    switchThumbOff = Color(0xFFFFFFFF),
    switchThumbOn = Color(0xFFFFFFFF),
    topBar = Color(0xFFFFFFFF),
    onTopBar = Color(0xFF151B31),
    onTopBarIcon = Color(0xFF4B5570),
    topBarBorder = Color(0xFFE7EAF0),
    nav = Color(0xFFFFFFFF),
    navSelected = Color(0xFF1F52B8),
    navUnselected = Color(0xFF5F6880),
    navBorder = Color(0xFFE7EAF0),
    navFocus = Color(0xFF1F52B8),
    attention = Color(0xFFD0342C),
    onAttention = Color(0xFFFFFFFF),
    chromeSuccess = Color(0xFF22704B),
    chromeWarning = Color(0xFF8A5A06),
    chromeDanger = Color(0xFFB3372C),
    chromeInfo = Color(0xFF1F52B8),
    chromePending = Color(0xFF3D3A86),
    chromeNeutral = Color(0xFF5D667B),
    inverse = Color(0xFF151B31),
    onInverse = Color(0xFFFFFFFF),
    inverseAction = Color(0xFF8FB0F0),
    scrim = Color(0x52121A3D),
    mapPlate = Color(0xFFFFFFFF)
)

val GeoNavyLight = GeoColors(
    canvas = Color(0xFFEDF0F5),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFF3F5F8),
    field = Color(0xFFF1F3F7),
    fieldPressed = Color(0xFFE9ECF2),
    text = Color(0xFF151B31),
    textSecondary = Color(0xFF4B5570),
    textSubtle = Color(0xFF5F6880),
    border = Color(0xFFE7EAF0),
    borderStrong = Color(0xFF838891),
    primary = Color(0xFF1F52B8),
    primaryPressed = Color(0xFF1A47A0),
    onPrimary = Color(0xFFFFFFFF),
    primaryTint = Color(0xFFE7EEFB),
    primaryTintText = Color(0xFF1A47A0),
    focusHalo = Color(0x3D1F52B8),
    success = Color(0xFF22704B),
    successSoft = Color(0xFFE2F1E9),
    successBorder = Color(0xFFA9D3BD),
    warning = Color(0xFF8A5A06),
    warningSoft = Color(0xFFFBF0DA),
    warningBorder = Color(0xFFECCA8C),
    danger = Color(0xFFB3372C),
    dangerPressed = Color(0xFF9A2E25),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFFBE8E5),
    dangerBorder = Color(0xFFF0B8B1),
    infoBorder = Color(0xFFB9CBEF),
    pending = Color(0xFF3D3A86),
    pendingSoft = Color(0xFFE4E3F4),
    pendingBorder = Color(0xFFC4C1E8),
    neutral = Color(0xFF5D667B),
    neutralSoft = Color(0xFFEEF0F4),
    neutralBorder = Color(0xFFD9DEE7),
    disabledFill = Color(0xFFE9ECF2),
    disabledText = Color(0xFF5F6880),
    switchTrackOff = Color(0xFF7D8598),
    switchThumbOff = Color(0xFFFFFFFF),
    switchThumbOn = Color(0xFFFFFFFF),
    topBar = Color(0xFF121A3D),
    onTopBar = Color(0xFFFFFFFF),
    onTopBarIcon = Color(0xFFAEB9D9),
    topBarBorder = Color(0x00000000),
    nav = Color(0xFF121A3D),
    navSelected = Color(0xFFFFFFFF),
    navUnselected = Color(0xFFAEB9D9),
    navBorder = Color(0x00000000),
    navFocus = Color(0xFF8FB0F0),
    attention = Color(0xFFF08F86),
    onAttention = Color(0xFF121A3D),
    chromeSuccess = Color(0xFF88CCA5),
    chromeWarning = Color(0xFFE3BC7A),
    chromeDanger = Color(0xFFF08F86),
    chromeInfo = Color(0xFF8FB0F0),
    chromePending = Color(0xFFCCC8F3),
    chromeNeutral = Color(0xFFAEB9D9),
    inverse = Color(0xFF151B31),
    onInverse = Color(0xFFFFFFFF),
    inverseAction = Color(0xFF8FB0F0),
    scrim = Color(0x52121A3D),
    mapPlate = Color(0xFFFFFFFF)
)

val GeoHighContrast = GeoColors(
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFF2F4F7),
    field = Color(0xFFF2F4F7),
    fieldPressed = Color(0xFFE6E9EF),
    text = Color(0xFF0B1022),
    textSecondary = Color(0xFF283045),
    textSubtle = Color(0xFF3B4256),
    border = Color(0xFFC3C9D4),
    borderStrong = Color(0xFF4D5466),
    primary = Color(0xFF163E8F),
    primaryPressed = Color(0xFF103170),
    onPrimary = Color(0xFFFFFFFF),
    primaryTint = Color(0xFFE3EBF9),
    primaryTintText = Color(0xFF103170),
    focusHalo = Color(0x52163E8F),
    success = Color(0xFF0E5634),
    successSoft = Color(0xFFDFF0E6),
    successBorder = Color(0xFF0E5634),
    warning = Color(0xFF5F3D00),
    warningSoft = Color(0xFFFAEDD3),
    warningBorder = Color(0xFF5F3D00),
    danger = Color(0xFF8B1E15),
    dangerPressed = Color(0xFF6F150E),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFFBE4E1),
    dangerBorder = Color(0xFF8B1E15),
    infoBorder = Color(0xFF163E8F),
    pending = Color(0xFF2C2970),
    pendingSoft = Color(0xFFE5E4F5),
    pendingBorder = Color(0xFF2C2970),
    neutral = Color(0xFF3D4456),
    neutralSoft = Color(0xFFECEEF2),
    neutralBorder = Color(0xFF3D4456),
    disabledFill = Color(0xFFE6E9EF),
    disabledText = Color(0xFF555D70),
    switchTrackOff = Color(0xFF5E6577),
    switchThumbOff = Color(0xFFFFFFFF),
    switchThumbOn = Color(0xFFFFFFFF),
    topBar = Color(0xFFFFFFFF),
    onTopBar = Color(0xFF0B1022),
    onTopBarIcon = Color(0xFF283045),
    topBarBorder = Color(0xFF4D5466),
    nav = Color(0xFFFFFFFF),
    navSelected = Color(0xFF163E8F),
    navUnselected = Color(0xFF5A6276),
    navBorder = Color(0xFF4D5466),
    navFocus = Color(0xFF163E8F),
    attention = Color(0xFFB3261E),
    onAttention = Color(0xFFFFFFFF),
    chromeSuccess = Color(0xFF0E5634),
    chromeWarning = Color(0xFF5F3D00),
    chromeDanger = Color(0xFF8B1E15),
    chromeInfo = Color(0xFF163E8F),
    chromePending = Color(0xFF2C2970),
    chromeNeutral = Color(0xFF3D4456),
    inverse = Color(0xFF0B1022),
    onInverse = Color(0xFFFFFFFF),
    inverseAction = Color(0xFFA9C3F7),
    scrim = Color(0x7A0B1022),
    mapPlate = Color(0xFFFFFFFF)
)

val GeoGraphite = GeoColors(
    canvas = Color(0xFF161719),
    surface = Color(0xFF1E1F21),
    sunken = Color(0xFF191B1D),
    field = Color(0xFF292A2C),
    fieldPressed = Color(0xFF303234),
    text = Color(0xFFE8E9EC),
    textSecondary = Color(0xFFBDBFC4),
    textSubtle = Color(0xFF9EA2A7),
    border = Color(0xFF323336),
    borderStrong = Color(0xFF7E8289),
    primary = Color(0xFF8AADEC),
    primaryPressed = Color(0xFFA8C3F4),
    onPrimary = Color(0xFF1E1F21),
    primaryTint = Color(0xFF242E40),
    primaryTintText = Color(0xFFB9CFF5),
    focusHalo = Color(0x528AADEC),
    success = Color(0xFF88CCA5),
    successSoft = Color(0xFF203026),
    successBorder = Color(0xFF376048),
    warning = Color(0xFFE3BC7A),
    warningSoft = Color(0xFF332A1C),
    warningBorder = Color(0xFF67522D),
    danger = Color(0xFFE9938C),
    dangerPressed = Color(0xFFF0A9A3),
    onDanger = Color(0xFF1E1F21),
    dangerSoft = Color(0xFF392626),
    dangerBorder = Color(0xFF734844),
    infoBorder = Color(0xFF435B85),
    pending = Color(0xFFCCC8F3),
    pendingSoft = Color(0xFF2D2B3F),
    pendingBorder = Color(0xFF565176),
    neutral = Color(0xFFADB1B9),
    neutralSoft = Color(0xFF2C2E31),
    neutralBorder = Color(0xFF46494E),
    disabledFill = Color(0xFF292A2C),
    disabledText = Color(0xFF9EA2A7),
    switchTrackOff = Color(0xFF7E8289),
    switchThumbOff = Color(0xFF1E1F21),
    switchThumbOn = Color(0xFF1E1F21),
    topBar = Color(0xFF1E1F21),
    onTopBar = Color(0xFFE8E9EC),
    onTopBarIcon = Color(0xFFBDBFC4),
    topBarBorder = Color(0xFF323336),
    nav = Color(0xFF0E0F11),
    navSelected = Color(0xFFA8C3F4),
    navUnselected = Color(0xFF9EA2A7),
    navBorder = Color(0x00000000),
    navFocus = Color(0xFF9BBAF0),
    attention = Color(0xFFF08F86),
    onAttention = Color(0xFF161719),
    chromeSuccess = Color(0xFF88CCA5),
    chromeWarning = Color(0xFFE3BC7A),
    chromeDanger = Color(0xFFE9938C),
    chromeInfo = Color(0xFF8AADEC),
    chromePending = Color(0xFFCCC8F3),
    chromeNeutral = Color(0xFFADB1B9),
    inverse = Color(0xFF3D3E41),
    onInverse = Color(0xFFE8E9EC),
    inverseAction = Color(0xFFA8C3F4),
    scrim = Color(0x99050608),
    mapPlate = Color(0xFF1E1F21)
)

val GeoNavyDark = GeoColors(
    canvas = Color(0xFF13171F),
    surface = Color(0xFF1B1F28),
    sunken = Color(0xFF171B23),
    field = Color(0xFF262A33),
    fieldPressed = Color(0xFF2D323A),
    text = Color(0xFFE6E9F0),
    textSecondary = Color(0xFFBBBFC8),
    textSubtle = Color(0xFF9DA1AB),
    border = Color(0xFF2F333C),
    borderStrong = Color(0xFF7D828D),
    primary = Color(0xFF8BACEF),
    primaryPressed = Color(0xFFA9C3F7),
    onPrimary = Color(0xFF1B1F28),
    primaryTint = Color(0xFF242F46),
    primaryTintText = Color(0xFFBBCEF5),
    focusHalo = Color(0x528BACEF),
    success = Color(0xFF88CCA5),
    successSoft = Color(0xFF203026),
    successBorder = Color(0xFF376048),
    warning = Color(0xFFE3BC7A),
    warningSoft = Color(0xFF332A1C),
    warningBorder = Color(0xFF67522D),
    danger = Color(0xFFE9938C),
    dangerPressed = Color(0xFFF0A9A3),
    onDanger = Color(0xFF1B1F28),
    dangerSoft = Color(0xFF392626),
    dangerBorder = Color(0xFF734844),
    infoBorder = Color(0xFF455A85),
    pending = Color(0xFFCCC8F3),
    pendingSoft = Color(0xFF2D2B3F),
    pendingBorder = Color(0xFF565176),
    neutral = Color(0xFFACB1BD),
    neutralSoft = Color(0xFF292E38),
    neutralBorder = Color(0xFF444955),
    disabledFill = Color(0xFF262A33),
    disabledText = Color(0xFF9DA1AB),
    switchTrackOff = Color(0xFF7D828D),
    switchThumbOff = Color(0xFF1B1F28),
    switchThumbOn = Color(0xFF1B1F28),
    topBar = Color(0xFF1B1F28),
    onTopBar = Color(0xFFE6E9F0),
    onTopBarIcon = Color(0xFFBBBFC8),
    topBarBorder = Color(0xFF2F333C),
    nav = Color(0xFF0C0F17),
    navSelected = Color(0xFFA9C3F7),
    navUnselected = Color(0xFF9DA1AB),
    navBorder = Color(0x00000000),
    navFocus = Color(0xFF9DB9F0),
    attention = Color(0xFFF08F86),
    onAttention = Color(0xFF13171F),
    chromeSuccess = Color(0xFF88CCA5),
    chromeWarning = Color(0xFFE3BC7A),
    chromeDanger = Color(0xFFE9938C),
    chromeInfo = Color(0xFF8BACEF),
    chromePending = Color(0xFFCCC8F3),
    chromeNeutral = Color(0xFFACB1BD),
    inverse = Color(0xFF3A3E48),
    onInverse = Color(0xFFE6E9F0),
    inverseAction = Color(0xFFA9C3F7),
    scrim = Color(0x9904060E),
    mapPlate = Color(0xFF1B1F28)
)

val GeoMist = GeoColors(
    canvas = Color(0xFF26282B),
    surface = Color(0xFF2F3033),
    sunken = Color(0xFF2A2B2E),
    field = Color(0xFF3A3C3F),
    fieldPressed = Color(0xFF424447),
    text = Color(0xFFEBEDEF),
    textSecondary = Color(0xFFCACCD0),
    textSubtle = Color(0xFFB3B6BB),
    border = Color(0xFF444649),
    borderStrong = Color(0xFF90949A),
    primary = Color(0xFF9ABBF6),
    primaryPressed = Color(0xFFB9D2FE),
    onPrimary = Color(0xFF2F3033),
    primaryTint = Color(0xFF374154),
    primaryTintText = Color(0xFFC1D7FD),
    focusHalo = Color(0x529ABBF6),
    success = Color(0xFF91D6AE),
    successSoft = Color(0xFF314238),
    successBorder = Color(0xFF477159),
    warning = Color(0xFFEDC684),
    warningSoft = Color(0xFF453C2D),
    warningBorder = Color(0xFF78623D),
    danger = Color(0xFFF39D95),
    dangerPressed = Color(0xFFF7B2AB),
    onDanger = Color(0xFF2F3033),
    dangerSoft = Color(0xFF4B3837),
    dangerBorder = Color(0xFF855854),
    infoBorder = Color(0xFF546C97),
    pending = Color(0xFFD5D0FC),
    pendingSoft = Color(0xFF3F3D52),
    pendingBorder = Color(0xFF676288),
    neutral = Color(0xFFBBBFC7),
    neutralSoft = Color(0xFF3D4044),
    neutralBorder = Color(0xFF56595F),
    disabledFill = Color(0xFF3A3C3F),
    disabledText = Color(0xFFB3B6BB),
    switchTrackOff = Color(0xFF90949A),
    switchThumbOff = Color(0xFF2F3033),
    switchThumbOn = Color(0xFF2F3033),
    topBar = Color(0xFF2F3033),
    onTopBar = Color(0xFFEBEDEF),
    onTopBarIcon = Color(0xFFCACCD0),
    topBarBorder = Color(0xFF444649),
    nav = Color(0xFF1E1F22),
    navSelected = Color(0xFFB9D2FE),
    navUnselected = Color(0xFFB3B6BB),
    navBorder = Color(0x00000000),
    navFocus = Color(0xFFA5C3FA),
    attention = Color(0xFFFB988F),
    onAttention = Color(0xFF26282B),
    chromeSuccess = Color(0xFF91D6AE),
    chromeWarning = Color(0xFFEDC684),
    chromeDanger = Color(0xFFF39D95),
    chromeInfo = Color(0xFF9ABBF6),
    chromePending = Color(0xFFD5D0FC),
    chromeNeutral = Color(0xFFBBBFC7),
    inverse = Color(0xFF4F5155),
    onInverse = Color(0xFFEBEDEF),
    inverseAction = Color(0xFFB9D2FE),
    scrim = Color(0x99060709),
    mapPlate = Color(0xFF2F3033)
)

/** R3 §4.3: overlays and on-dark badges are independent of the palette. */
object GeoFixedColors {
    val mapZone = Color(0xFF1F52B8)
    val mapTrailPrecise = Color(0xFF22704B)
    val mapTrailApproximate = Color(0xFF8A5A06)
    val mapTrailUnknown = Color(0xFF5D667B)
    val mapGap = Color(0xFFB3372C)
    val mapDevice = Color(0xFF121A3D)
    val mapPhone = Color(0xFF1F52B8)
    val mapCasing = Color(0xFFFFFFFF)
    val mapFocus = Color(0xFF1F52B8)
    val mediaStage = Color(0xFF000000)
    val mediaPlate = Color(0xB8000000)
    val mediaText = Color(0xFFFFFFFF)
    val mediaBadge = Color(0xFF1E1F21)
    val mediaLive = Color(0xFF88CCA5)
    val mediaDelayed = Color(0xFFE3BC7A)
    val mediaPending = Color(0xFFBDBFC4)
    val mediaFailed = Color(0xFFE9938C)
    val brandGreen = Color(0xFF29674E)
    val brandGreenLight = Color(0xFF64B993)
}

@Deprecated("Use GeoStandard", ReplaceWith("GeoStandard"))
val GeoLight: GeoColors = GeoStandard
@Deprecated("Use GeoGraphite", ReplaceWith("GeoGraphite"))
val GeoDark: GeoColors = GeoGraphite

val LocalGeoColors = staticCompositionLocalOf { GeoStandard }

@Deprecated("Use GeoColors", ReplaceWith("GeoColors"))
typealias CobaltColors = GeoColors
@Deprecated("Use GeoStandard", ReplaceWith("GeoStandard"))
val CobaltLight: GeoColors = GeoStandard
@Deprecated("Use GeoGraphite", ReplaceWith("GeoGraphite"))
val CobaltDark: GeoColors = GeoGraphite
@Deprecated("Use LocalGeoColors", ReplaceWith("LocalGeoColors"))
val LocalCobaltColors = LocalGeoColors
