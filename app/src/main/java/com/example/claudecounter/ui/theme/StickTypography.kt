package com.example.claudecounter.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.claudecounter.R

/**
 * Montserrat — the font used throughout the stick firmware's LVGL UI
 * (lv_conf.h enables Montserrat 12/14/16/18/20/22/24/28/40/48).
 *
 * We ship the single Google Fonts variable-weight file and select weights via
 * [FontVariation.Settings], which needs API 26+ (matches this app's minSdk).
 */
@OptIn(ExperimentalTextApi::class)
private fun montserrat(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.montserrat_variable,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val MontserratFamily = FontFamily(
    montserrat(400, FontWeight.Normal),
    montserrat(500, FontWeight.Medium),
    montserrat(600, FontWeight.SemiBold),
    montserrat(700, FontWeight.Bold),
)

/**
 * Named text styles matching the exact px sizes used by the firmware's tiles
 * (see tools/gen_mockups.py — each `F(n)` call maps to one of these).
 */
object StickType {
    /** Big % readouts on Agora cards and the momento overlay. Mont 48. */
    val displayPercent = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp)

    /** Countdown readout on Agora cards ("1h 40m"). Mont 40. */
    val displayCountdown = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp)

    /** PIN entry digits. Mont 28. */
    val pinDigits = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)

    /** Settings gear / refresh glyph size reference. Mont 22 / 20. */
    val iconGlyph = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    val overlayLabel = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp)

    /** Tile titles, model names, trend verdict, overlay message. Mont 16. */
    val heading = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

    /** Card section titles ("5 HORAS"), period pills, PIN screen title. Mont 14. */
    val label = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)

    /** Captions, chips, status text, axis labels, legends. Mont 12. */
    val caption = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
}
