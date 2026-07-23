package com.example.claudecounter.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette ported 1:1 from the "Claude Usage Stick" firmware
 * (c:\git\claude-usage-stick-SVGL\firmware\claude_stick\claude_stick.ino, lines 37-49)
 * and its Python mockup generator (tools/gen_mockups.py).
 *
 * Do not rename these — they're referenced by hex value throughout the design
 * spec (mockups, .ino source) so keeping the same names makes cross-checking easy.
 */
object StickColors {
    val Bg = Color(0xFF0F0F12)
    val Surface = Color(0xFF1A1A20)
    val Surface2 = Color(0xFF24242C)
    val Track = Color(0xFF26262E)
    val Grid = Color(0xFF232329)
    val Border = Color(0xFF30303A)
    val Text = Color(0xFFF2F0EC)
    val Muted = Color(0xFF8C8C98)
    val Faint = Color(0xFF5C5C68)

    /** Coral — the Claude Code brand accent. */
    val Accent = Color(0xFFD97757)

    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)

    /** Sonnet accessory / sweat-drop blue. */
    val Blue = Color(0xFF7DD3FC)

    /** Fable accessory lilac. */
    val Lilac = Color(0xFFC4B5FD)

    /** Momento overlay background — darker than [Bg]. */
    val OverlayBg = Color(0xFF0D0D10)

    /** Mascot recolor when a model is erroring / KO'd. */
    val MascotKo = Color(0xFF6A6A74)
}
