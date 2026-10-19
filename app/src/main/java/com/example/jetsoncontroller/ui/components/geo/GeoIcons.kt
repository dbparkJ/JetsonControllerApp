package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** KIT section 4 on a 24 dp grid. Render the filled tabs with tint = Color.Unspecified. */
object GeoIcons {
    val tabWork: ImageVector by lazy {
        icon("tabWork", paths = arrayOf(
            IconPath("M6.5 5h11a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1 -2.5 2.5h-11a2.5 2.5 0 0 1 -2.5 -2.5v-11a2.5 2.5 0 0 1 2.5 -2.5z"),
            IconPath("M9 3.5h6v3H9z"),
            IconPath("M8 12h8M8 16h5")
        ))
    }

    fun tabWorkFilled(foreground: Color, nav: Color): ImageVector = icon(
        "tabWork-filled", foreground, nav,
        IconPath("M6.5 5h11a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1 -2.5 2.5h-11a2.5 2.5 0 0 1 -2.5 -2.5v-11a2.5 2.5 0 0 1 2.5 -2.5z", filled = true),
        IconPath("M9 3.5h6v3H9z", filled = true),
        IconPath("M8 12h8M8 16h5", knockout = true)
    )

    val tabDevice: ImageVector by lazy {
        icon("tabDevice", paths = arrayOf(
            IconPath("M5.5 6h13a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5h-13a2.5 2.5 0 0 1 -2.5 -2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z"),
            IconPath("M13 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
            IconPath("M7 10h3M7 14h3")
        ))
    }

    fun tabDeviceFilled(foreground: Color, nav: Color): ImageVector = icon(
        "tabDevice-filled", foreground, nav,
        IconPath("M5.5 6h13a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5h-13a2.5 2.5 0 0 1 -2.5 -2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z", filled = true),
        IconPath("M13 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", knockout = true),
        IconPath("M7 10h3M7 14h3", knockout = true)
    )

    val tabTransfer: ImageVector by lazy {
        icon("tabTransfer", paths = arrayOf(
            IconPath("M4 14v3.5A2.5 2.5 0 0 0 6.5 20h11a2.5 2.5 0 0 0 2.5-2.5V14"),
            IconPath("M12 15V4"),
            IconPath("M7.5 8.5L12 4l4.5 4.5")
        ))
    }

    fun tabTransferFilled(foreground: Color, nav: Color): ImageVector = icon(
        "tabTransfer-filled", foreground, nav,
        IconPath("M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1 -4 4h-10a4 4 0 0 1 -4 -4v-10a4 4 0 0 1 4 -4z", filled = true),
        IconPath("M12 16V8M8.5 11.5L12 8l3.5 3.5", knockout = true)
    )

    val tabSettings: ImageVector by lazy {
        icon("tabSettings", paths = arrayOf(
            IconPath("M9.92 5.21L10.61 2.70L13.39 2.70L14.08 5.21L15.33 5.73L17.59 4.44L19.56 6.41L18.27 8.67L18.79 9.92L21.30 10.61L21.30 13.39L18.79 14.08L18.27 15.33L19.56 17.59L17.59 19.56L15.33 18.27L14.08 18.79L13.39 21.30L10.61 21.30L9.92 18.79L8.67 18.27L6.41 19.56L4.44 17.59L5.73 15.33L5.21 14.08L2.70 13.39L2.70 10.61L5.21 9.92L5.73 8.67L4.44 6.41L6.41 4.44L8.67 5.73Z"),
            IconPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
        ))
    }

    fun tabSettingsFilled(foreground: Color, nav: Color): ImageVector = icon(
        "tabSettings-filled", foreground, nav,
        IconPath("M9.92 5.21L10.61 2.70L13.39 2.70L14.08 5.21L15.33 5.73L17.59 4.44L19.56 6.41L18.27 8.67L18.79 9.92L21.30 10.61L21.30 13.39L18.79 14.08L18.27 15.33L19.56 17.59L17.59 19.56L15.33 18.27L14.08 18.79L13.39 21.30L10.61 21.30L9.92 18.79L8.67 18.27L6.41 19.56L4.44 17.59L5.73 15.33L5.21 14.08L2.70 13.39L2.70 10.61L5.21 9.92L5.73 8.67L4.44 6.41L6.41 4.44L8.67 5.73Z", filled = true),
        IconPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = true, knockout = true)
    )

    val back: ImageVector by lazy {
        icon("back", paths = arrayOf(
            IconPath("M19 12H5"),
            IconPath("M11 6l-6 6 6 6")
        ))
    }

    val close: ImageVector by lazy {
        icon("close", paths = arrayOf(
            IconPath("M6 6l12 12M18 6L6 18")
        ))
    }

    val more: ImageVector by lazy {
        icon("more", paths = arrayOf(
            IconPath("M10.5 5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0", filled = true, stroked = false),
            IconPath("M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0", filled = true, stroked = false),
            IconPath("M10.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0", filled = true, stroked = false)
        ))
    }

    val bell: ImageVector by lazy {
        icon("bell", paths = arrayOf(
            IconPath("M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z"),
            IconPath("M10 20.5a2 2 0 0 0 4 0")
        ))
    }

    val add: ImageVector by lazy {
        icon("add", paths = arrayOf(
            IconPath("M12 5v14M5 12h14")
        ))
    }

    val zoomOut: ImageVector by lazy {
        icon("zoomOut", paths = arrayOf(
            IconPath("M5 12h14")
        ))
    }

    val search: ImageVector by lazy {
        icon("search", paths = arrayOf(
            IconPath("M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0"),
            IconPath("M20 20l-4.5-4.5")
        ))
    }

    val chevronRight: ImageVector by lazy {
        icon("chevronRight", paths = arrayOf(
            IconPath("M9 6l6 6-6 6")
        ))
    }

    val chevronDown: ImageVector by lazy {
        icon("chevronDown", paths = arrayOf(
            IconPath("M6 9l6 6 6-6")
        ))
    }

    val chevronUp: ImageVector by lazy {
        icon("chevronUp", paths = arrayOf(
            IconPath("M6 15l6-6 6 6")
        ))
    }

    val copy: ImageVector by lazy {
        icon("copy", paths = arrayOf(
            IconPath("M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-7a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z"),
            IconPath("M5 15H4.5A1.5 1.5 0 0 1 3 13.5v-9A1.5 1.5 0 0 1 4.5 3h9A1.5 1.5 0 0 1 15 4.5V5")
        ))
    }

    val refresh: ImageVector by lazy {
        icon("refresh", paths = arrayOf(
            IconPath("M20 11a8 8 0 1 0-2.34 5.66"),
            IconPath("M20 5v6h-6")
        ))
    }

    val send: ImageVector by lazy {
        icon("send", paths = arrayOf(
            IconPath("M12 20V8"),
            IconPath("M7 13l5-5 5 5"),
            IconPath("M5 4h14")
        ))
    }

    val trash: ImageVector by lazy {
        icon("trash", paths = arrayOf(
            IconPath("M4 7h16"),
            IconPath("M10 11v6M14 11v6"),
            IconPath("M6 7l1 13h10l1-13"),
            IconPath("M9 7V4h6v3")
        ))
    }

    val restore: ImageVector by lazy {
        icon("restore", paths = arrayOf(
            IconPath("M9 14L4 9l5-5"),
            IconPath("M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
        ))
    }

    val play: ImageVector by lazy {
        icon("play", paths = arrayOf(
            IconPath("M8 5v14l11-7z")
        ))
    }

    val pause: ImageVector by lazy {
        icon("pause", paths = arrayOf(
            IconPath("M9 5v14M15 5v14")
        ))
    }

    val stop: ImageVector by lazy {
        icon("stop", paths = arrayOf(
            IconPath("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z")
        ))
    }

    val torch: ImageVector by lazy {
        icon("torch", paths = arrayOf(
            IconPath("M8 3h8v4l-2 3v11h-4V10L8 7z"),
            IconPath("M12 13v3")
        ))
    }

    val torchOff: ImageVector by lazy {
        icon("torchOff", paths = arrayOf(
            IconPath("M8 3h8v4l-2 3v11h-4V10L8 7z"),
            IconPath("M4 4l16 16")
        ))
    }

    val camera: ImageVector by lazy {
        icon("camera", paths = arrayOf(
            IconPath("M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"),
            IconPath("M8.5 13.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0")
        ))
    }

    val qr: ImageVector by lazy {
        icon("qr", paths = arrayOf(
            IconPath("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z"),
            IconPath("M14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2")
        ))
    }

    val myLocation: ImageVector by lazy {
        icon("myLocation", paths = arrayOf(
            IconPath("M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0"),
            IconPath("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", filled = true, stroked = false),
            IconPath("M12 2v3M12 19v3M2 12h3M19 12h3")
        ))
    }

    val fitZone: ImageVector by lazy {
        icon("fitZone", paths = arrayOf(
            IconPath("M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5")
        ))
    }

    val layers: ImageVector by lazy {
        icon("layers", paths = arrayOf(
            IconPath("M12 4L3 9l9 5 9-5z"),
            IconPath("M3 14l9 5 9-5")
        ))
    }

    val eye: ImageVector by lazy {
        icon("eye", paths = arrayOf(
            IconPath("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"),
            IconPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
        ))
    }

    val eyeOff: ImageVector by lazy {
        icon("eyeOff", paths = arrayOf(
            IconPath("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"),
            IconPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"),
            IconPath("M4 4l16 16")
        ))
    }

    val wifi: ImageVector by lazy {
        icon("wifi", paths = arrayOf(
            IconPath("M2.5 9.5a14 14 0 0 1 19 0"),
            IconPath("M5.5 12.8a9.5 9.5 0 0 1 13 0"),
            IconPath("M8.6 16a5 5 0 0 1 6.8 0"),
            IconPath("M10.9 19.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", filled = true, stroked = false)
        ))
    }

    val bluetooth: ImageVector by lazy {
        icon("bluetooth", paths = arrayOf(
            IconPath("M7 7l10 10-5 4V3l5 4L7 17")
        ))
    }

    val link: ImageVector by lazy {
        icon("link", paths = arrayOf(
            IconPath("M4 9h15l-3.5-3.5"),
            IconPath("M20 15H5l3.5 3.5")
        ))
    }

    val power: ImageVector by lazy {
        icon("power", paths = arrayOf(
            IconPath("M12 3v8"),
            IconPath("M7.4 6.4a7.5 7.5 0 1 0 9.2 0")
        ))
    }

    val reboot: ImageVector by lazy {
        icon("reboot", paths = arrayOf(
            IconPath("M4 12a8 8 0 1 0 2.34-5.66"),
            IconPath("M4 4v5h5")
        ))
    }

    val fan: ImageVector by lazy {
        icon("fan", paths = arrayOf(
            IconPath("M12 12C12 7.2 14.6 4.4 17.1 5.6C18.6 6.4 18.1 9.6 12 12Z M12 12C16.16 14.4 17.28 18.05 14.99 19.62C13.55 20.52 11.03 18.48 12 12Z M12 12C7.84 14.4 4.12 13.55 3.91 10.78C3.85 9.08 6.87 7.92 12 12Z"),
            IconPath("M10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0")
        ))
    }

    val terminal: ImageVector by lazy {
        icon("terminal", paths = arrayOf(
            IconPath("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z"),
            IconPath("M7 10l3 2-3 2"),
            IconPath("M12.5 15h4.5")
        ))
    }

    val folder: ImageVector by lazy {
        icon("folder", paths = arrayOf(
            IconPath("M3 7.5A2.5 2.5 0 0 1 5.5 5H9l2 2h7.5A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z")
        ))
    }

    val file: ImageVector by lazy {
        icon("file", paths = arrayOf(
            IconPath("M6 3h8l4 4v14H6z"),
            IconPath("M14 3v4h4")
        ))
    }

    val image: ImageVector by lazy {
        icon("image", paths = arrayOf(
            IconPath("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z"),
            IconPath("M7.5 10a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0"),
            IconPath("M21 16l-5-5-8 8")
        ))
    }

    val video: ImageVector by lazy {
        icon("video", paths = arrayOf(
            IconPath("M5 6h9a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-9a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z"),
            IconPath("M16 10.5l5-3v9l-5-3")
        ))
    }

    val log: ImageVector by lazy {
        icon("log", paths = arrayOf(
            IconPath("M6 3h12v18H6z"),
            IconPath("M9 8h6M9 12h6M9 16h4")
        ))
    }

    val success: ImageVector by lazy {
        icon("success", paths = arrayOf(
            IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            IconPath("M8 12.5l2.5 2.5L16 9.5")
        ))
    }

    val warning: ImageVector by lazy {
        icon("warning", paths = arrayOf(
            IconPath("M12 4L2.8 19.5h18.4z"),
            IconPath("M12 10v4.5"),
            IconPath("M11 17a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true, stroked = false)
        ))
    }

    val danger: ImageVector by lazy {
        icon("danger", paths = arrayOf(
            IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            IconPath("M12 7.5v5.5"),
            IconPath("M11 16.3a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true, stroked = false)
        ))
    }

    val info: ImageVector by lazy {
        icon("info", paths = arrayOf(
            IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            IconPath("M12 11v5.5"),
            IconPath("M11 7.8a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true, stroked = false)
        ))
    }

    val pending: ImageVector by lazy {
        icon("pending", paths = arrayOf(
            IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            IconPath("M12 7v5l3 2")
        ))
    }

    val unknown: ImageVector by lazy {
        icon("unknown", paths = arrayOf(
            IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            IconPath("M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.6"),
            IconPath("M11 17a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true, stroked = false)
        ))
    }

    val spinner: ImageVector by lazy {
        icon("spinner", paths = arrayOf(
            IconPath("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", strokeAlpha = 0.25f),
            IconPath("M20 12a8 8 0 0 0-8-8")
        ))
    }

    val zoomIn: ImageVector get() = add
    val expandPanel: ImageVector get() = chevronUp
    val collapsePanel: ImageVector get() = chevronDown

    /** Includes the KIT aliases and spinner for gallery and completeness checks. */
    fun all(foreground: Color, nav: Color): Map<String, ImageVector> = linkedMapOf(
        "tabWork" to tabWork,
        "tabWork-filled" to tabWorkFilled(foreground, nav),
        "tabDevice" to tabDevice,
        "tabDevice-filled" to tabDeviceFilled(foreground, nav),
        "tabTransfer" to tabTransfer,
        "tabTransfer-filled" to tabTransferFilled(foreground, nav),
        "tabSettings" to tabSettings,
        "tabSettings-filled" to tabSettingsFilled(foreground, nav),
        "back" to back,
        "close" to close,
        "more" to more,
        "bell" to bell,
        "add" to add,
        "zoomOut" to zoomOut,
        "search" to search,
        "chevronRight" to chevronRight,
        "chevronDown" to chevronDown,
        "chevronUp" to chevronUp,
        "copy" to copy,
        "refresh" to refresh,
        "send" to send,
        "trash" to trash,
        "restore" to restore,
        "play" to play,
        "pause" to pause,
        "stop" to stop,
        "torch" to torch,
        "torchOff" to torchOff,
        "camera" to camera,
        "qr" to qr,
        "myLocation" to myLocation,
        "fitZone" to fitZone,
        "layers" to layers,
        "eye" to eye,
        "eyeOff" to eyeOff,
        "wifi" to wifi,
        "bluetooth" to bluetooth,
        "link" to link,
        "power" to power,
        "reboot" to reboot,
        "fan" to fan,
        "terminal" to terminal,
        "folder" to folder,
        "file" to file,
        "image" to image,
        "video" to video,
        "log" to log,
        "success" to success,
        "warning" to warning,
        "danger" to danger,
        "info" to info,
        "pending" to pending,
        "unknown" to unknown,
        "spinner" to spinner,
        "zoomIn" to zoomIn,
        "expandPanel" to expandPanel,
        "collapsePanel" to collapsePanel
    )
}

private data class IconPath(
    val data: String,
    val filled: Boolean = false,
    val stroked: Boolean = true,
    val knockout: Boolean = false,
    val strokeAlpha: Float = 1f
)

private fun icon(
    name: String,
    foreground: Color = Color.Black,
    nav: Color = Color.Transparent,
    vararg paths: IconPath
): ImageVector = ImageVector.Builder(
    name = "GeoIcons.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    paths.forEach { path ->
        val brush = SolidColor(if (path.knockout) nav else foreground)
        addPath(
            pathData = PathParser().parsePathString(path.data).toNodes(),
            fill = if (path.filled) brush else null,
            stroke = if (path.stroked) brush else null,
            strokeAlpha = path.strokeAlpha,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}.build()
