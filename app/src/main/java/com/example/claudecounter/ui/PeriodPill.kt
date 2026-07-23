package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType

/**
 * Small pill-shaped period/toggle selector — "Hoje / 7d / 30d / Tudo" on
 * [com.example.claudecounter.ui.tiles.TileHeat], "5h / 7d" on
 * [com.example.claudecounter.ui.tiles.TileTrend]. Extracted so both tiles share
 * one look for the same interaction instead of two near-identical composables.
 */
@Composable
fun PeriodPill(text: String, active: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(StickDimens.PeriodPillRadius))
            .background(if (active) StickColors.Accent else StickColors.Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = StickType.label,
            color = if (active) StickColors.Bg else StickColors.Muted
        )
    }
}
