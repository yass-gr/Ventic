package com.yass.vintageplayer.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.yass.vintageplayer.R

/** Barlow Semi Condensed stand-in: 500 / 600 / 700. */
val Barlow = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** Orbitron variable font, used at 600 and 800. */
@OptIn(ExperimentalTextApi::class)
val Orbitron = FontFamily(
    Font(
        R.font.orbitron,
        FontWeight(600),
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.orbitron,
        FontWeight(800),
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

/** Share Tech Mono, single regular cut. */
val ShareTechMono = FontFamily(
    Font(R.font.share_tech_mono, FontWeight.Normal),
)
