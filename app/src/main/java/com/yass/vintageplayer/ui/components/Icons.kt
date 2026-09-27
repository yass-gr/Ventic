package com.yass.vintageplayer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun strokeIcon(name: String, data: String, width: Float = 2.2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

private fun fillIcon(name: String, data: String, strokeData: String? = null, strokeWidth: Float = 2f): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = SolidColor(Color.Black),
        )
    if (strokeData != null) {
        b.addPath(
            pathData = PathParser().parsePathString(strokeData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** All SVG icons from `design/Main.component.html`, drawn in black for tinting. */
object VintageIcons {
    val ChevronDown: ImageVector = strokeIcon("chevron_down", "M6 9L12 15L18 9")
    val Prev: ImageVector = fillIcon("prev", "M19 20L9 12L19 4V20Z", "M5 19V5")
    val Next: ImageVector = fillIcon("next", "M5 4L15 12L5 20V4Z", "M19 5V19")
    val Play: ImageVector = fillIcon("play", "M8 5V19L19 12L8 5Z")
    val Pause: ImageVector = fillIcon(
        "pause",
        "M7 5H9A1 1 0 0 1 10 6V18A1 1 0 0 1 9 19H7A1 1 0 0 1 6 18V6A1 1 0 0 1 7 5Z" +
            "M15 5H17A1 1 0 0 1 18 6V18A1 1 0 0 1 17 19H15A1 1 0 0 1 14 18V6A1 1 0 0 1 15 5Z",
    )
    val Shuffle: ImageVector = strokeIcon(
        "shuffle",
        "M16 3H21V8M21 16V21H16M4 20L21 3M15 15L21 21M4 4L9 9",
    )
    val Repeat: ImageVector = strokeIcon(
        "repeat",
        "M17 1L21 5L17 9M3 11V9A4 4 0 0 1 7 5H21M7 23L3 19L7 15M21 13V15A4 4 0 0 1 17 19H3",
    )
    val Heart: ImageVector = strokeIcon(
        "heart",
        "M20.8 4.6A5.5 5.5 0 0 0 13 4.6L12 5.7L11 4.6A5.5 5.5 0 0 0 3.2 12.4L4.2 13.5L12 21L19.8 13.5L20.8 12.4A5.5 5.5 0 0 0 20.8 4.6Z",
    )
    val HeartFilled: ImageVector = fillIcon(
        "heart_filled",
        "M20.8 4.6A5.5 5.5 0 0 0 13 4.6L12 5.7L11 4.6A5.5 5.5 0 0 0 3.2 12.4L4.2 13.5L12 21L19.8 13.5L20.8 12.4A5.5 5.5 0 0 0 20.8 4.6Z",
    )
    val Search: ImageVector = strokeIcon(
        "search",
        "M11 18A7 7 0 1 0 11 4A7 7 0 0 0 11 18ZM21 21L16.6 16.6",
    )
    val Folder: ImageVector = strokeIcon(
        "folder",
        "M3 7A2 2 0 0 1 5 5H9L11 7H19A2 2 0 0 1 21 9V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17V7Z",
    )
    val MusicNote: ImageVector = strokeIcon(
        "music_note",
        "M9 18V5L21 3V16M9 18A3 3 0 1 0 3 18A3 3 0 1 0 9 18ZM21 16A3 3 0 1 0 15 16A3 3 0 1 0 21 16Z",
        2f,
    )
    val Disc: ImageVector = strokeIcon(
        "disc",
        "M12 21A9 9 0 1 0 12 3 9 9 0 0 0 12 21ZM12 14.5A2.5 2.5 0 1 0 12 9.5 2.5 2.5 0 0 0 12 14.5Z",
        2f,
    )
    val Sliders: ImageVector = strokeIcon(
        "sliders",
        "M4 21V14M4 10V3M12 21V12M12 8V3M20 21V16M20 12V3M1 14H7M9 8H15M17 16H23",
        2f,
    )
    val Moon: ImageVector = strokeIcon(
        "moon",
        "M21 12.8A9 9 0 1 1 11.2 3A7 7 0 0 0 21 12.8Z",
    )
    val Sun: ImageVector = strokeIcon(
        "sun",
        "M12 16A4 4 0 1 0 12 8 4 4 0 0 0 12 16ZM12 2V4M12 20V22M4.9 4.9L6.3 6.3M17.7 17.7L19.1 19.1M2 12H4M20 12H22M4.9 19.1L6.3 17.7M17.7 6.3L19.1 4.9",
    )
}
