package com.example.claudecounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Fixed-dark theme matching the stick firmware's UI — there is no light variant
 * (the monitor-mode use case implies a dark room / always dark panel).
 *
 * System bar coloring is handled by [androidx.activity.enableEdgeToEdge] in
 * MainActivity, which draws content edge-to-edge under transparent system bars —
 * StickColors.Bg shows through naturally, no manual statusBarColor needed.
 */
@Composable
fun StickTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = StickColors.Bg,
        surface = StickColors.Surface,
        surfaceVariant = StickColors.Surface2,
        primary = StickColors.Accent,
        onBackground = StickColors.Text,
        onSurface = StickColors.Text,
        error = StickColors.Bad,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
