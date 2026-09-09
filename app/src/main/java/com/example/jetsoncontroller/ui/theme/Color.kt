package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

// Semantic colors from jetson_cobalt_v5/design/tokens.json.
data class CobaltColors(
    val canvas: Color,
    val surface: Color,
    val subtle: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Color,
    val heroText: Color,
    val heroMuted: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val info: Color,
    val infoBg: Color,
    val disabled: Color,
    val onDisabled: Color
)

val CobaltLight = CobaltColors(
    canvas = Color(0xFFF7F5F2),
    surface = Color(0xFFFFFFFF),
    subtle = Color(0xFFEDF1F7),
    ink = Color(0xFF1D293B),
    muted = Color(0xFF5D687A),
    border = Color(0xFFD9DFE8),
    primary = Color(0xFF2456D8),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFDCE7FF),
    onAccent = Color(0xFF173B82),
    hero = Color(0xFF172B4D),
    heroText = Color(0xFFF6F8FF),
    heroMuted = Color(0xFFC3CEE2),
    success = Color(0xFF384B92),
    successBg = Color(0xFFEEF0FF),
    warning = Color(0xFF8A4E19),
    warningBg = Color(0xFFFFF0DD),
    danger = Color(0xFFAE382C),
    dangerBg = Color(0xFFFCECE8),
    info = Color(0xFF315E94),
    infoBg = Color(0xFFEAF2FC),
    disabled = Color(0xFFE5E9EF),
    onDisabled = Color(0xFF657083)
)

val CobaltDark = CobaltColors(
    canvas = Color(0xFF101827),
    surface = Color(0xFF182338),
    subtle = Color(0xFF25324B),
    ink = Color(0xFFEDF2FC),
    muted = Color(0xFFB8C4D9),
    border = Color(0xFF3D4E69),
    primary = Color(0xFF9CBBFF),
    onPrimary = Color(0xFF10265A),
    accent = Color(0xFFDCE7FF),
    onAccent = Color(0xFF173B82),
    hero = Color(0xFF203553),
    heroText = Color(0xFFF6F8FF),
    heroMuted = Color(0xFFC3CEE2),
    success = Color(0xFFC2CBFF),
    successBg = Color(0xFF303954),
    warning = Color(0xFFFFD39A),
    warningBg = Color(0xFF473620),
    danger = Color(0xFFFFB8AC),
    dangerBg = Color(0xFF4A2B2A),
    info = Color(0xFFBBD3FF),
    infoBg = Color(0xFF243958),
    disabled = Color(0xFF2E3A51),
    onDisabled = Color(0xFFB0BCD0)
)

val LocalCobaltColors = staticCompositionLocalOf { CobaltLight }
