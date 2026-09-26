package com.yass.vintageplayer.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow

/**
 * Pixel-faithful port of the design's `t` token object
 * (`design/Main.component.html`, `renderVals()`).
 *
 * Raw colours are stored as [Color]; gradient / shadow helpers build the
 * corresponding Compose [Brush]/[Shadow]. Use [vintageTokens] to construct.
 */
@Immutable
data class VintageTokens(
    val dark: Boolean,
    val accent: Color,
    val accentText: Color,
    val accentGlow: Color,
    val lcdText: Color,
    val lcdDim: Color,
    val lcdGlow: Shadow,
    val chassisTop: Color,
    val chassisMid: Color,
    val chassisBottom: Color,
    val chassisStripeLight: Color,
    val chassisStripeDark: Color,
    val chassisGrain: Brush,
    val text: Color,
    val sub: Color,
    val label: Color,
    val labelShadow: Shadow,
    val inset: Color,
    val groove: Color,
    val btnTop: Color,
    val btnBottom: Color,
    val btnBorder: Color,
    val btnText: Color,
    val btnOnTop: Color,
    val btnOnBottom: Color,
    val ledOff: Color,
    val bezelTop: Color,
    val bezelBottom: Color,
    val moduleTop: Color,
    val moduleBottom: Color,
    val moduleBorder: Color,
    val moduleSolid: Color,
    val knobColors: List<Color>,
    val ringTrack: Color,
    val playIcon: Color,
    val navTop: Color,
    val navBottom: Color,
    val navWood: Boolean,
    val navText: Color,
    val navOn: Color,
    val navLabelShadow: Shadow,
    val divider: Color,
    val dividerHi: Color,
    val lcdTop: Color,
    val lcdBottom: Color,
    val vuFaceInner: Color,
    val vuFaceMid: Color,
    val vuFaceOuter: Color,
    val vuInk: Color,
    val vuNeedle: Color,
    val vuCap: Color,
    val vuRed: Color,
) {
    val chassisBrush: Brush
        get() = Brush.verticalGradient(listOf(chassisTop, chassisMid, chassisBottom))

    fun btnBrush(on: Boolean): Brush =
        if (on) Brush.verticalGradient(listOf(btnOnTop, btnOnBottom))
        else Brush.verticalGradient(listOf(btnTop, btnBottom))

    val bezelBrush: Brush
        get() = Brush.verticalGradient(listOf(bezelTop, bezelBottom))

    val moduleBrush: Brush
        get() = Brush.verticalGradient(listOf(moduleTop, moduleBottom))

    val knobBrush: Brush
        get() = Brush.sweepGradient(knobColors)

    val lcdBrush: Brush
        get() = Brush.verticalGradient(listOf(lcdTop, lcdBottom))

    fun vuFaceBrush(center: Offset, radius: Float): Brush =
        Brush.radialGradient(
            listOf(vuFaceInner, vuFaceMid, vuFaceOuter),
            center = center,
            radius = radius,
        )

    fun volRingBrush(volume: Float): Brush {
        val deg = (volume.coerceIn(0f, 1f) * 270f).coerceIn(0f, 270f)
        return Brush.sweepGradient(
            0f to accent,
            (deg / 360f).coerceIn(0.001f, 0.999f) to accent,
            ((deg / 360f) + 0.001f).coerceIn(0.001f, 1f) to ringTrack,
            0.75f to ringTrack,
            0.751f to Color.Transparent,
            1f to Color.Transparent,
        )
    }
}

fun Color.hex(): String =
    "#%02x%02x%02x".format(
        (red * 255f).toInt(),
        (green * 255f).toInt(),
        (blue * 255f).toInt(),
    )

private fun parse(hex: String): Color {
    val h = hex.removePrefix("#")
    val v = h.toLong(16)
    return if (h.length == 8) {
        Color(
            ((v shr 16) and 0xFF).toInt(),
            ((v shr 8) and 0xFF).toInt(),
            (v and 0xFF).toInt(),
            ((v shr 24) and 0xFF).toInt(),
        )
    } else {
        Color(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    }
}

/** Port of the design's `mix(a, b, k)`. */
fun mix(a: Color, b: Color, k: Float): Color {
    val t = k.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

private fun mixHex(a: String, b: String, k: Float): Color = mix(parse(a), parse(b), k)

/** Port of the design's `rgba(hex, alpha)`. */
fun Color.withAlphaRatio(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

private fun rgbaHex(hex: String, alpha: Float): Color = parse(hex).copy(alpha = alpha)

/** Brushed-metal 3 px repeating tile, port of `repeating-linear-gradient(90deg, …)`. */
private fun grainBrush(light: Color, darkStripe: Color): Brush {
    val bmp = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
    bmp.setPixel(0, 0, ((light.alpha * 255).toInt() shl 24) or ((light.red * 255).toInt() shl 16) or ((light.green * 255).toInt() shl 8) or (light.blue * 255).toInt())
    val mid = ((darkStripe.alpha * 255).toInt() shl 24) or ((darkStripe.red * 255).toInt() shl 16) or ((darkStripe.green * 255).toInt() shl 8) or (darkStripe.blue * 255).toInt()
    bmp.setPixel(1, 0, mid)
    bmp.setPixel(2, 0, mid)
    val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    return ShaderBrush(shader)
}

/** Generated-cover palettes, port of `coverFor()`. Indexed by `abs(id) % 12`. */
val CoverPalettes: List<Triple<Color, Color, Color>> = listOf(
    Triple(parse("#f2c14e"), parse("#f78154"), parse("#4d9078")),
    Triple(parse("#ff6ec7"), parse("#3a0ca3"), parse("#4cc9f0")),
    Triple(parse("#e07a5f"), parse("#3d405b"), parse("#f2cc8f")),
    Triple(parse("#d8e2dc"), parse("#9d8189"), parse("#ffcad4")),
    Triple(parse("#264653"), parse("#2a9d8f"), parse("#e9c46a")),
    Triple(parse("#6d597a"), parse("#b56576"), parse("#eaac8b")),
    Triple(parse("#ff9f1c"), parse("#2ec4b6"), parse("#011627")),
    Triple(parse("#577590"), parse("#43aa8b"), parse("#f9c74f")),
    Triple(parse("#f4a261"), parse("#e76f51"), parse("#fefae0")),
    Triple(parse("#8ecae6"), parse("#219ebc"), parse("#ffb703")),
    Triple(parse("#ffbe0b"), parse("#fb5607"), parse("#3a0ca3")),
    Triple(parse("#adb5bd"), parse("#495057"), parse("#90e0ef")),
)

fun coverPalette(id: Long): Triple<Color, Color, Color> {
    val idx = (kotlin.math.abs(id) % 12).toInt()
    return CoverPalettes[idx]
}

/**
 * Port of `renderVals()`: every key of the dark and light `t` objects plus the
 * derived `accent`, `accentText`, `accentGlow`, `lcdText`, `lcdDim`, `lcdGlow`
 * formulas.
 */
fun vintageTokens(dark: Boolean, accent: Color): VintageTokens {
    val black = Color.Black
    val white = Color.White
    val accentText = if (dark) accent else mix(accent, black, 0.45f)
    val accentGlow = accent.copy(alpha = 0.7f)
    val lcdText = if (dark) accent else mix(accent, white, 0.82f)
    val lcdDim = if (dark) accent.copy(alpha = 0.16f) else white.copy(alpha = 0.14f)
    val lcdGlow = if (dark) Shadow(accent.copy(alpha = 0.65f), Offset.Zero, 10f)
    else Shadow(lcdText.copy(alpha = 0.5f), Offset.Zero, 8f)

    return if (dark) {
        VintageTokens(
            dark = true,
            accent = accent,
            accentText = accentText,
            accentGlow = accentGlow,
            lcdText = lcdText,
            lcdDim = lcdDim,
            lcdGlow = lcdGlow,
            chassisTop = parse("#3b3e42"),
            chassisMid = parse("#2c2e31"),
            chassisBottom = parse("#25272a"),
            chassisStripeLight = white.copy(alpha = 0.02f),
            chassisStripeDark = black.copy(alpha = 0.025f),
            chassisGrain = grainBrush(white.copy(alpha = 0.02f), black.copy(alpha = 0.025f)),
            text = parse("#dfe2e5"),
            sub = parse("#a3a9af"),
            label = parse("#a3a9af"),
            labelShadow = Shadow(black.copy(alpha = 0.75f), Offset(0f, -1f), 0f),
            inset = parse("#1d1f22"),
            groove = parse("#141517"),
            btnTop = parse("#4c5055"),
            btnBottom = parse("#33363a"),
            btnBorder = parse("#141517"),
            btnText = parse("#d4d8dc"),
            btnOnTop = parse("#232528"),
            btnOnBottom = parse("#2e3134"),
            ledOff = parse("#141618"),
            bezelTop = parse("#17181a"),
            bezelBottom = parse("#222427"),
            moduleTop = parse("#36393d"),
            moduleBottom = parse("#2c2e32"),
            moduleBorder = parse("#18191b"),
            moduleSolid = parse("#303337"),
            knobColors = listOf(
                parse("#62666c"), parse("#2f3236"), parse("#585c61"),
                parse("#2c2f33"), parse("#63676d"), parse("#303337"),
                parse("#595d62"), parse("#2b2e32"), parse("#62666c"),
            ),
            ringTrack = parse("#15171a"),
            playIcon = accent,
            navTop = parse("#25272a"),
            navBottom = parse("#1b1c1f"),
            navWood = false,
            navText = parse("#a3a9af"),
            navOn = accent,
            navLabelShadow = Shadow(black.copy(alpha = 0.8f), Offset(0f, -1f), 0f),
            divider = black.copy(alpha = 0.55f),
            dividerHi = white.copy(alpha = 0.05f),
            lcdTop = parse("#0d181b"),
            lcdBottom = parse("#081113"),
            vuFaceInner = mix(accent, white, 0.6f),
            vuFaceMid = accent,
            vuFaceOuter = mix(accent, black, 0.45f),
            vuInk = mix(accent, black, 0.82f),
            vuNeedle = parse("#101214"),
            vuCap = parse("#1b1c1f"),
            vuRed = parse("#c8372d"),
        )
    } else {
        VintageTokens(
            dark = false,
            accent = accent,
            accentText = accentText,
            accentGlow = accentGlow,
            lcdText = lcdText,
            lcdDim = lcdDim,
            lcdGlow = lcdGlow,
            chassisTop = parse("#efece6"),
            chassisMid = parse("#dedad1"),
            chassisBottom = parse("#d3cec4"),
            chassisStripeLight = white.copy(alpha = 0.22f),
            chassisStripeDark = black.copy(alpha = 0.02f),
            chassisGrain = grainBrush(white.copy(alpha = 0.22f), black.copy(alpha = 0.02f)),
            text = parse("#2a2824"),
            sub = parse("#5e5a52"),
            label = parse("#56524a"),
            labelShadow = Shadow(white.copy(alpha = 0.9f), Offset(0f, 1f), 0f),
            inset = parse("#e2ded5"),
            groove = parse("#bdb8ad"),
            btnTop = parse("#fdfcf9"),
            btnBottom = parse("#dcd8ce"),
            btnBorder = parse("#a39e93"),
            btnText = parse("#3a3833"),
            btnOnTop = parse("#cdc9bf"),
            btnOnBottom = parse("#dfdbd2"),
            ledOff = parse("#b3aea3"),
            bezelTop = parse("#b6b1a6"),
            bezelBottom = parse("#ccc7bc"),
            moduleTop = parse("#f4f2ed"),
            moduleBottom = parse("#e1ddd4"),
            moduleBorder = parse("#b3aea3"),
            moduleSolid = parse("#e9e6df"),
            knobColors = listOf(
                parse("#f6f6f4"), parse("#9d9d9a"), parse("#ececea"),
                parse("#8f8f8c"), parse("#f7f7f5"), parse("#9a9a97"),
                parse("#eeeeec"), parse("#8d8d8a"), parse("#f6f6f4"),
            ),
            ringTrack = parse("#bdb8ad"),
            playIcon = parse("#34322d"),
            navTop = parse("#8a5a30"),
            navBottom = parse("#6b401e"),
            navWood = true,
            navText = parse("#f6e7d0"),
            navOn = white,
            navLabelShadow = Shadow(black.copy(alpha = 0.45f), Offset(0f, -1f), 0f),
            divider = black.copy(alpha = 0.12f),
            dividerHi = white.copy(alpha = 0.8f),
            lcdTop = mixHex(accent.hex(), "#062630", 0.5f),
            lcdBottom = mixHex(accent.hex(), "#041a21", 0.7f),
            vuFaceInner = parse("#fff4d2"),
            vuFaceMid = parse("#f3c96f"),
            vuFaceOuter = parse("#cf8c2f"),
            vuInk = parse("#3b2710"),
            vuNeedle = parse("#2a1c0b"),
            vuCap = parse("#3a3833"),
            vuRed = parse("#c8372d"),
        )
    }
}

/** Needle angle port: `-50°` idle, else `-40 + 88*vol*level` clamped to 48. */
fun needleAngle(playing: Boolean, volume: Float, level: Float): Float =
    if (!playing) -50f
    else (-40f + 88f * volume.coerceIn(0f, 1f) * level.coerceIn(0f, 1f)).coerceAtMost(48f)

/** Knob indicator rotation port: `-135 + vol*270`. */
fun knobDegrees(volume: Float): Float = -135f + volume.coerceIn(0f, 1f) * 270f

/** Silence floor for the spectrum when paused, port of the `0.06` fallback. */
const val SPECTRUM_FLOOR = 0.06f
