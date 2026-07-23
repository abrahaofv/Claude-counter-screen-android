package com.example.claudecounter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.claudecounter.ui.theme.StickColors
import kotlin.math.roundToInt

/**
 * Shared color/formatting helpers ported from the stick firmware
 * (claude_stick.ino: grad_color/set_meter/pct_color, tools/gen_mockups.py: grad/chip/meter).
 */

/**
 * Continuous usage gradient: green -> amber (0-50%), amber -> red (50-100%).
 * This is the color used for the big % readouts and the lit segments of the meter —
 * NOT a spatial gradient, a single color computed from the percentage.
 */
fun usageColor(pct: Float): Color {
    val p = pct.coerceIn(0f, 100f)
    return if (p <= 50f) {
        lerp(StickColors.Ok, StickColors.Warn, p / 50f)
    } else {
        lerp(StickColors.Warn, StickColors.Bad, (p - 50f) / 50f)
    }
}

/** Discrete 3-band status color (used for the overall status chip thresholds: <70 / <90 / >=90). */
fun pctColorDiscrete(pct: Float): Color = when {
    pct < 70f -> StickColors.Ok
    pct < 90f -> StickColors.Warn
    else -> StickColors.Bad
}

/**
 * Tinted chip background: mostly [StickColors.Bg] with a hint of [color] mixed in
 * (mirrors gen_mockups.py's `chip()`: `mix(color, BG, 0.76)`).
 */
fun chipBackground(color: Color): Color = lerp(color, StickColors.Bg, 0.76f)

/**
 * Heatmap bar color: brighter (closer to accent) the more quota was burned in that hour.
 * Mirrors gen_mockups.py's `mix(ACCENT, BG, (1 - ratio) * 0.55)`.
 */
fun heatBarColor(ratio: Float, accent: Color = StickColors.Accent): Color =
    lerp(accent, StickColors.Bg, (1f - ratio.coerceIn(0f, 1f)) * 0.55f)

/** How many of [count] segments should be lit for [pct] (0-100). */
fun litSegments(pct: Float, count: Int): Int =
    ((pct.coerceIn(0f, 100f) / 100f) * count).roundToInt()
